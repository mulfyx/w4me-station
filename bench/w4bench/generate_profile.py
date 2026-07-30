#!/usr/bin/env python3
# Copyright 2026 W4ME Station contributors
# SPDX-License-Identifier: MIT
#
"""Generate the deterministic W4Bench V1 contract into a build directory.

This is deliberately dependency-free. The frozen profile is the only editable
benchmark input; Java metadata, the opcode catalog, and WAT are build outputs.
"""

import argparse
import json
import struct
import sys
import zlib
from pathlib import Path
from typing import Any, TypeAlias

JsonObject: TypeAlias = dict[str, Any]
Profile: TypeAlias = JsonObject
TestCase: TypeAlias = JsonObject

ROOT = Path(__file__).resolve().parent
DEFAULT_PROFILE = ROOT / "profile_v1.json"
DEFAULT_OUTPUT = (
    ROOT.parent.parent / "build" / "reports" / "bench" / "w4bench" / "generated"
)

MAGIC = 0x57423431
CONTRACT_VERSION = 1
RESULT_OFFSET = 8192
RESULT_LENGTH = 32
STATUS_PASS = 0
STATUS_PREPARED = 1
STATUS_ERROR = 2
MASK32 = 0xFFFFFFFF
MASK64 = 0xFFFFFFFFFFFFFFFF
MAX_SIGNED_I64 = 0x7FFFFFFFFFFFFFFF
EXPECTED_MEASURED_RUNS = 9
EXPECTED_TEST_COUNT = 7
MIN_WORK_UNITS = 1024
MAX_WORK_UNITS = 1000000
SOURCE_OPCODE_COUNT = 190
FC_PREFIX = 0xFC00
MEMORY_TEST_ID = 4
VALIDATION_TEST_ID = 0x8000
VALIDATION_F32_UNSIGNED_I64 = 0x8000008000000001
VALIDATION_F32_EXPECTED_BITS = 0x5F000001
VALIDATION_F64_UNSIGNED_I64 = 0xC000000000000401
VALIDATION_F64_EXPECTED_BITS = 0x43E8000000000001
VALIDATION_COVER_VALUES = (
    0x0123456789ABCDEF,
    0x1020304050607080,
    0x1122334455667788,
    0x2233445566778899,
    0x33445566778899AA,
    0x445566778899AABB,
    0x66778899AABBCCDD,
)

# This is intentionally an explicit source-WASM space, rather than W4IR
# internals.  178 core/sign-extension opcodes plus 12 FC-prefixed extensions
# are accepted by the current interpreter.  `unreachable` is the one expected
# trap; every other entry is exercised by validate_all's eight sub-exports.
SUPPORTED_SOURCE_OPCODES = [
    0,
    1,
    2,
    3,
    4,
    5,
    11,
    *list(range(12, 18)),
    *list(range(26, 29)),
    *list(range(32, 37)),
    *list(range(40, 197)),
    *list(range(64512, 64524)),
]

# Source opcodes reached between entering a run_* export and returning from it,
# including its transitive kernel/helper calls.  This is metadata about the
# timed workload shape, not the all-opcode validation sweep below.
TIMED_SOURCE_OPCODES = frozenset(
    (
        0x02,
        0x03,
        0x04,
        0x05,
        0x0B,
        0x0C,
        0x0D,
        0x10,
        0x11,
        0x20,
        0x21,
        0x24,
        0x28,
        0x2D,
        0x2F,
        0x36,
        0x3A,
        0x3B,
        0x41,
        0x42,
        0x43,
        0x44,
        0x4F,
        0x6A,
        0x6B,
        0x6C,
        0x71,
        0x73,
        0x77,
        0x78,
        0x7C,
        0x85,
        0x89,
        0x92,
        0x94,
        0xA0,
        0xA2,
        0xAD,
        0xB3,
        0xB8,
        0xBC,
        0xBD,
        0xBE,
        0xBF,
    )
)


def canonical_json(value: object) -> str:
    """Serialize a value using the contract's canonical JSON form."""
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def parse_u32(value: int | str, field: str) -> int:
    """Parse and range-check one profile u32 field."""
    if isinstance(value, int):
        number = value
    elif isinstance(value, str) and value.startswith("0x"):
        number = int(value, 16)
    else:
        msg = f"{field} must be an integer or 0x-prefixed integer"
        raise ValueError(msg)
    if number < 0 or number > MASK32:
        msg = f"{field} outside u32 range"
        raise ValueError(msg)
    return number


def load_profile(  # noqa: C901, PLR0912, PLR0915  # Central contract audit.
    path: str | Path,
) -> Profile:
    """Load and exhaustively validate one frozen-profile candidate."""
    with Path(path).open(encoding="utf-8") as source:
        profile = json.load(source)
    required = {
        "format",
        "state",
        "contract_version",
        "warmup_runs",
        "measured_runs",
        "min_timed_ms",
        "instruction_limit",
        "result_offset",
        "result_length",
        "tests",
        "opcode_validation",
    }
    missing = required.difference(profile)
    if missing:
        msg = "profile missing fields: {}".format(", ".join(sorted(missing)))
        raise ValueError(msg)
    if profile["format"] != "w4bench-profile-v1":
        msg = "unsupported profile format"
        raise ValueError(msg)
    if profile["state"] not in ("PRECALIBRATION", "FROZEN"):
        msg = "state must be PRECALIBRATION or FROZEN"
        raise ValueError(msg)
    if profile["contract_version"] != CONTRACT_VERSION:
        msg = "unexpected contract version"
        raise ValueError(msg)
    if (
        profile["warmup_runs"] != 1
        or profile["measured_runs"] != EXPECTED_MEASURED_RUNS
    ):
        msg = "W4Bench V1 requires exactly one warmup and nine measured runs"
        raise ValueError(msg)
    if not isinstance(profile["min_timed_ms"], int) or profile["min_timed_ms"] < 1:
        msg = "min_timed_ms must be a positive integer"
        raise ValueError(msg)
    if (
        not isinstance(profile["instruction_limit"], int)
        or profile["instruction_limit"] < 1
        or profile["instruction_limit"] > MAX_SIGNED_I64
    ):
        msg = "instruction_limit must fit a positive signed i64"
        raise ValueError(msg)
    if (
        profile["result_offset"] != RESULT_OFFSET
        or profile["result_length"] != RESULT_LENGTH
    ):
        msg = "W4Bench V1 result block layout is fixed"
        raise ValueError(msg)
    tests = profile["tests"]
    if not isinstance(tests, list) or len(tests) != EXPECTED_TEST_COUNT:
        msg = "profile must contain exactly seven ordered timed tests"
        raise ValueError(msg)
    expected_names = (
        "i32-control",
        "direct-calls-locals",
        "call-indirect-table",
        "memory-widths",
        "i64",
        "f32",
        "f64",
    )
    for index, test in enumerate(tests):
        if set(test) != {
            "id",
            "name",
            "seed",
            "work_units",
            "prepare_export",
            "run_export",
        }:
            msg = f"test {index + 1} has an invalid field set"
            raise ValueError(msg)
        if test["id"] != index + 1 or test["name"] != expected_names[index]:
            msg = "tests must retain the V1 fixed ordered identifiers"
            raise ValueError(msg)
        test["seed"] = parse_u32(test["seed"], f"tests[{index}].seed")
        if test["prepare_export"] != "prepare_" + test["name"].replace(
            "-", "_"
        ) or test["run_export"] != "run_" + test["name"].replace("-", "_"):
            msg = f"tests[{index}] must use canonical no-argument exports"
            raise ValueError(msg)
        if (
            not isinstance(test["work_units"], int)
            or not MIN_WORK_UNITS <= test["work_units"] <= MAX_WORK_UNITS
        ):
            msg = f"tests[{index}].work_units outside safe V1 range"
            raise ValueError(msg)
    validation = profile["opcode_validation"]
    if validation.get("mode") != "CATALOG_SWEEP":
        msg = "opcode validation must use CATALOG_SWEEP"
        raise ValueError(msg)
    if validation.get("required_coverage") != "ALL_SUPPORTED_SOURCE_OPCODES":
        msg = "opcode validation must require all supported source opcodes"
        raise ValueError(msg)
    if validation.get("catalog") != "opcode_catalog_v1.json":
        msg = "unexpected opcode catalog name"
        raise ValueError(msg)
    if validation.get("expected_trap_exports") != ["trap_unreachable"]:
        msg = "V1 must expose trap_unreachable"
        raise ValueError(msg)
    if len(SUPPORTED_SOURCE_OPCODES) != SOURCE_OPCODE_COUNT:
        msg = "internal source opcode catalog is incomplete"
        raise ValueError(msg)
    return profile


def validate_catalog_data(  # noqa: C901, PLR0912  # Central catalog audit.
    catalog: JsonObject,
) -> JsonObject:
    """Validate the generated opcode catalog against the exact source set."""
    if catalog.get("format") != "w4bench-opcode-catalog-v1":
        msg = "invalid opcode catalog format"
        raise ValueError(msg)
    entries = catalog.get("entries")
    if not isinstance(entries, list) or not entries:
        msg = "opcode catalog has no entries"
        raise ValueError(msg)
    seen = set()
    valid_modes = {"timed", "validate", "trap", "pending"}
    for entry in entries:
        opcode = entry.get("opcode")
        if not isinstance(opcode, str) or not opcode.startswith("0x"):
            msg = "catalog opcode must be an 0x string"
            raise ValueError(msg)
        if opcode in seen:
            msg_0 = f"duplicate catalog opcode {opcode}"
            raise ValueError(msg_0)
        seen.add(opcode)
        if entry.get("mode") not in valid_modes:
            msg_0 = f"invalid catalog mode for {opcode}"
            raise ValueError(msg_0)
        if not entry.get("name"):
            msg_0 = f"catalog name missing for {opcode}"
            raise ValueError(msg_0)
        if not entry.get("wat_token"):
            msg_0 = f"catalog WAT token missing for {opcode}"
            raise ValueError(msg_0)
        value = int(opcode, 16)
        if entry.get("name") != opcode_token(value):
            msg_0 = f"catalog name does not match opcode {opcode}"
            raise ValueError(msg_0)
        expected_mode = catalog_mode(value)
        if entry.get("mode") != expected_mode:
            msg_0 = f"catalog mode for {opcode} must be {expected_mode}"
            raise ValueError(msg_0)
    required = {
        f"0x{value:04x}" if value >= FC_PREFIX else f"0x{value:02x}"
        for value in SUPPORTED_SOURCE_OPCODES
    }
    if seen != required:
        missing = required.difference(seen)
        extra = seen.difference(required)
        msg_0 = (
            "catalog does not exactly match supported source opcodes: "
            f"missing={sorted(missing)} extra={sorted(extra)}"
        )
        raise ValueError(msg_0)
    if catalog.get("coverage_status") != "COMPLETE":
        msg = "catalog must declare COMPLETE source opcode coverage"
        raise ValueError(msg)
    if any(entry.get("mode") == "pending" for entry in entries):
        msg = "COMPLETE catalog cannot contain pending entries"
        raise ValueError(msg)
    return catalog


def validate_catalog(path: str | Path) -> JsonObject:
    """Load and validate one generated opcode catalog."""
    with Path(path).open(encoding="utf-8") as source:
        return validate_catalog_data(json.load(source))


def catalog_opcode(value: int) -> str:
    """Format one source opcode for the JSON catalog."""
    if value >= FC_PREFIX:
        return f"0x{value:04x}"
    return f"0x{value:02x}"


def catalog_mode(value: int) -> str:
    """Return the required coverage mode for one source opcode."""
    if value == 0x00:
        return "trap"
    if value in TIMED_SOURCE_OPCODES:
        return "timed"
    return "validate"


def opcode_token(value: int) -> str:
    """Return the canonical WAT token for one supported source opcode."""
    direct = {
        0x00: "unreachable",
        0x01: "nop",
        0x02: "block",
        0x03: "loop",
        0x04: "if",
        0x05: "else",
        0x0B: "end",
        0x0C: "br",
        0x0D: "br_if",
        0x0E: "br_table",
        0x0F: "return",
        0x10: "call",
        0x11: "call_indirect",
        0x1A: "drop",
        0x1B: "select",
        0x1C: "select (result i32)",
        0x20: "local.get",
        0x21: "local.set",
        0x22: "local.tee",
        0x23: "global.get",
        0x24: "global.set",
        0x3F: "memory.size",
        0x40: "memory.grow",
        0x41: "i32.const",
        0x42: "i64.const",
        0x43: "f32.const",
        0x44: "f64.const",
        0xC0: "i32.extend8_s",
        0xC1: "i32.extend16_s",
        0xC2: "i64.extend8_s",
        0xC3: "i64.extend16_s",
        0xC4: "i64.extend32_s",
        0xFC00: "i32.trunc_sat_f32_s",
        0xFC01: "i32.trunc_sat_f32_u",
        0xFC02: "i32.trunc_sat_f64_s",
        0xFC03: "i32.trunc_sat_f64_u",
        0xFC04: "i64.trunc_sat_f32_s",
        0xFC05: "i64.trunc_sat_f32_u",
        0xFC06: "i64.trunc_sat_f64_s",
        0xFC07: "i64.trunc_sat_f64_u",
        0xFC08: "memory.init",
        0xFC09: "data.drop",
        0xFC0A: "memory.copy",
        0xFC0B: "memory.fill",
    }
    if value in direct:
        return direct[value]
    memory = {
        0x28: "i32.load",
        0x29: "i64.load",
        0x2A: "f32.load",
        0x2B: "f64.load",
        0x2C: "i32.load8_s",
        0x2D: "i32.load8_u",
        0x2E: "i32.load16_s",
        0x2F: "i32.load16_u",
        0x30: "i64.load8_s",
        0x31: "i64.load8_u",
        0x32: "i64.load16_s",
        0x33: "i64.load16_u",
        0x34: "i64.load32_s",
        0x35: "i64.load32_u",
        0x36: "i32.store",
        0x37: "i64.store",
        0x38: "f32.store",
        0x39: "f64.store",
        0x3A: "i32.store8",
        0x3B: "i32.store16",
        0x3C: "i64.store8",
        0x3D: "i64.store16",
        0x3E: "i64.store32",
    }
    if value in memory:
        return memory[value]
    names = {
        0x45: "i32.eqz",
        0x46: "i32.eq",
        0x47: "i32.ne",
        0x48: "i32.lt_s",
        0x49: "i32.lt_u",
        0x4A: "i32.gt_s",
        0x4B: "i32.gt_u",
        0x4C: "i32.le_s",
        0x4D: "i32.le_u",
        0x4E: "i32.ge_s",
        0x4F: "i32.ge_u",
        0x50: "i64.eqz",
        0x51: "i64.eq",
        0x52: "i64.ne",
        0x53: "i64.lt_s",
        0x54: "i64.lt_u",
        0x55: "i64.gt_s",
        0x56: "i64.gt_u",
        0x57: "i64.le_s",
        0x58: "i64.le_u",
        0x59: "i64.ge_s",
        0x5A: "i64.ge_u",
        0x5B: "f32.eq",
        0x5C: "f32.ne",
        0x5D: "f32.lt",
        0x5E: "f32.gt",
        0x5F: "f32.le",
        0x60: "f32.ge",
        0x61: "f64.eq",
        0x62: "f64.ne",
        0x63: "f64.lt",
        0x64: "f64.gt",
        0x65: "f64.le",
        0x66: "f64.ge",
        0x67: "i32.clz",
        0x68: "i32.ctz",
        0x69: "i32.popcnt",
        0x6A: "i32.add",
        0x6B: "i32.sub",
        0x6C: "i32.mul",
        0x6D: "i32.div_s",
        0x6E: "i32.div_u",
        0x6F: "i32.rem_s",
        0x70: "i32.rem_u",
        0x71: "i32.and",
        0x72: "i32.or",
        0x73: "i32.xor",
        0x74: "i32.shl",
        0x75: "i32.shr_s",
        0x76: "i32.shr_u",
        0x77: "i32.rotl",
        0x78: "i32.rotr",
        0x79: "i64.clz",
        0x7A: "i64.ctz",
        0x7B: "i64.popcnt",
        0x7C: "i64.add",
        0x7D: "i64.sub",
        0x7E: "i64.mul",
        0x7F: "i64.div_s",
        0x80: "i64.div_u",
        0x81: "i64.rem_s",
        0x82: "i64.rem_u",
        0x83: "i64.and",
        0x84: "i64.or",
        0x85: "i64.xor",
        0x86: "i64.shl",
        0x87: "i64.shr_s",
        0x88: "i64.shr_u",
        0x89: "i64.rotl",
        0x8A: "i64.rotr",
        0x8B: "f32.abs",
        0x8C: "f32.neg",
        0x8D: "f32.ceil",
        0x8E: "f32.floor",
        0x8F: "f32.trunc",
        0x90: "f32.nearest",
        0x91: "f32.sqrt",
        0x92: "f32.add",
        0x93: "f32.sub",
        0x94: "f32.mul",
        0x95: "f32.div",
        0x96: "f32.min",
        0x97: "f32.max",
        0x98: "f32.copysign",
        0x99: "f64.abs",
        0x9A: "f64.neg",
        0x9B: "f64.ceil",
        0x9C: "f64.floor",
        0x9D: "f64.trunc",
        0x9E: "f64.nearest",
        0x9F: "f64.sqrt",
        0xA0: "f64.add",
        0xA1: "f64.sub",
        0xA2: "f64.mul",
        0xA3: "f64.div",
        0xA4: "f64.min",
        0xA5: "f64.max",
        0xA6: "f64.copysign",
        0xA7: "i32.wrap_i64",
        0xA8: "i32.trunc_f32_s",
        0xA9: "i32.trunc_f32_u",
        0xAA: "i32.trunc_f64_s",
        0xAB: "i32.trunc_f64_u",
        0xAC: "i64.extend_i32_s",
        0xAD: "i64.extend_i32_u",
        0xAE: "i64.trunc_f32_s",
        0xAF: "i64.trunc_f32_u",
        0xB0: "i64.trunc_f64_s",
        0xB1: "i64.trunc_f64_u",
        0xB2: "f32.convert_i32_s",
        0xB3: "f32.convert_i32_u",
        0xB4: "f32.convert_i64_s",
        0xB5: "f32.convert_i64_u",
        0xB6: "f32.demote_f64",
        0xB7: "f64.convert_i32_s",
        0xB8: "f64.convert_i32_u",
        0xB9: "f64.convert_i64_s",
        0xBA: "f64.convert_i64_u",
        0xBB: "f64.promote_f32",
        0xBC: "i32.reinterpret_f32",
        0xBD: "i64.reinterpret_f64",
        0xBE: "f32.reinterpret_i32",
        0xBF: "f64.reinterpret_i64",
    }
    return names[value]


def render_catalog() -> str:
    """Render the complete deterministic opcode catalog."""
    entries = []
    for value in SUPPORTED_SOURCE_OPCODES:
        token = opcode_token(value)
        entry = {
            "opcode": catalog_opcode(value),
            "name": token,
            "mode": catalog_mode(value),
            "wat_token": token,
        }
        if value == 0x00:
            entry["export"] = "trap_unreachable"
        entries.append(entry)
    return (
        json.dumps(
            {
                "format": "w4bench-opcode-catalog-v1",
                "scope": "W4ME source-WASM opcodes",
                "coverage_status": "COMPLETE",
                "contract": (
                    "All 190 supported source-WASM opcodes are individually "
                    "listed. validate_all executes the 189 non-trapping opcodes; "
                    "trap_unreachable is an expected trap."
                ),
                "entries": entries,
            },
            indent=2,
            sort_keys=True,
        )
        + "\n"
    )


def u32(value: int) -> int:
    """Normalize an integer to an unsigned 32-bit value."""
    return value & MASK32


def u64(value: int) -> int:
    """Normalize an integer to an unsigned 64-bit value."""
    return value & MASK64


def rotl32(value: int, count: int) -> int:
    """Rotate one 32-bit integer left."""
    count &= 31
    return u32((value << count) | (value >> ((32 - count) & 31)))


def rotr32(value: int, count: int) -> int:
    """Rotate one 32-bit integer right."""
    count &= 31
    return u32((value >> count) | (value << ((32 - count) & 31)))


def rotl64(value: int, count: int) -> int:
    """Rotate one 64-bit integer left."""
    count &= 63
    return u64((value << count) | (value >> ((64 - count) & 63)))


def f32(value: float) -> float:
    """Round one Python float to IEEE-754 binary32."""
    return struct.unpack("<f", struct.pack("<f", value))[0]


def f32_bits(value: float) -> int:
    """Return the IEEE-754 binary32 bit pattern."""
    return struct.unpack("<I", struct.pack("<f", value))[0]


def f64_bits(value: float) -> int:
    """Return the IEEE-754 binary64 bit pattern."""
    return struct.unpack("<Q", struct.pack("<d", value))[0]


def validation_payload() -> int:
    """Compute the fixed validation-only payload."""
    value = (VALIDATION_F32_EXPECTED_BITS << 32) ^ VALIDATION_F64_EXPECTED_BITS
    for cover_value in VALIDATION_COVER_VALUES:
        value ^= cover_value
    return value


def payload_i32_control(test: TestCase) -> int:
    """Compute the i32 control-flow workload payload."""
    x = test["seed"]
    for index in range(test["work_units"]):
        x = rotl32(u32(x + index), 13) ^ 0x9E3779B9
        x = u32(x + 7) if (x & 1) else u32(x - 3)
    return x


def direct_step(x: int, index: int) -> int:
    """Execute one direct-call workload step."""
    return rotr32(u32((x ^ index) * 0x7FEB352D), 16)


def payload_direct(test: TestCase) -> int:
    """Compute the direct-call and locals workload payload."""
    x = test["seed"]
    for index in range(test["work_units"]):
        x = direct_step(x, index)
    return x


def indirect_a(x: int, index: int) -> int:
    """Execute the first indirect-call table target."""
    return rotl32(u32(x + index * 3), 5) ^ 0x85EBCA6B


def indirect_b(x: int, index: int) -> int:
    """Execute the second indirect-call table target."""
    return rotr32(u32(x ^ (index * 0x27D4EB2D)), 7) + 0x165667B1 & MASK32


def payload_indirect(test: TestCase) -> int:
    """Compute the indirect-call table workload payload."""
    x = test["seed"]
    for index in range(test["work_units"]):
        x = indirect_a(x, index) if (index & 1) == 0 else indirect_b(x, index)
    return x


def payload_memory(test: TestCase) -> int:
    """Compute the mixed-width memory workload payload."""
    x = test["seed"]
    words = [0] * 64
    for index in range(test["work_units"]):
        slot = index & 63
        words[slot] = x
        low8 = x & 0xFF
        low16 = x & 0xFFFF
        x = u32(words[slot] ^ low8 ^ low16 ^ index)
        x = rotl32(x, 3)
    return x


def payload_i64(test: TestCase) -> int:
    """Compute the i64 workload payload."""
    x = u64((test["seed"] << 32) | (test["seed"] ^ 0xA5A5A5A5))
    for index in range(test["work_units"]):
        x = rotl64(u64(x + index), 17) ^ 0x9E3779B97F4A7C15
    return x


def payload_f32(test: TestCase) -> int:
    """Compute the f32 workload payload."""
    x = struct.unpack("<f", struct.pack("<I", test["seed"]))[0]
    for index in range(test["work_units"]):
        x = f32(f32(x * f32(1.0001220703125)) + f32(f32(float(index)) * f32(0.000001)))
    return f32_bits(x)


def payload_f64(test: TestCase) -> int:
    """Compute the f64 workload payload."""
    bits = 0x3FF0000000000000 | test["seed"]
    x = struct.unpack("<d", struct.pack("<Q", bits))[0]
    for index in range(test["work_units"]):
        x = x * 1.0000001 + float(index) * 0.000000001
    return f64_bits(x)


PAYLOADS = (
    payload_i32_control,
    payload_direct,
    payload_indirect,
    payload_memory,
    payload_i64,
    payload_f32,
    payload_f64,
)


def payload_for_test(test: TestCase) -> int:
    """Compute one selected workload payload."""
    return PAYLOADS[test["id"] - 1](test)


def result_block(
    contract_crc: int,
    test: TestCase,
    status: int = STATUS_PASS,
    payload: int | None = None,
) -> bytes:
    """Build one canonical little-endian result block."""
    if payload is None:
        payload = payload_for_test(test)
    return struct.pack(
        "<IIIIIIII",
        MAGIC,
        CONTRACT_VERSION,
        contract_crc,
        test["id"],
        test["work_units"],
        status,
        payload & MASK32,
        (payload >> 32) & MASK32,
    )


def validation_result_crc(contract_identity: int) -> int:
    """Compute the validation result block's CRC-32."""
    validation_test = {"id": VALIDATION_TEST_ID, "work_units": 0}
    return crc32(
        result_block(contract_identity, validation_test, payload=validation_payload())
    )


def crc32(data: bytes) -> int:
    """Compute the unsigned IEEE CRC-32 used by W4Bench."""
    return zlib.crc32(data) & MASK32


def contract_crc(profile: Profile) -> int:
    """Compute the profile contract identity."""
    return crc32(canonical_json(profile).encode("utf-8"))


def frozen_profile_crc(profile: Profile, contract_identity: int) -> int:
    """Compute the frozen profile identity including expected results."""
    expected = [
        crc32(result_block(contract_identity, test)) for test in profile["tests"]
    ]
    return crc32(
        canonical_json({"profile": profile, "expected_crc32": expected}).encode("utf-8")
    )


def java_int_array(values: list[int]) -> str:
    """Render a Java int-array initializer body."""
    return ", ".join("0x%08x" % (value & MASK32) for value in values)


def java_string_array(values: list[str]) -> str:
    """Render a Java String-array initializer body."""
    return ", ".join(f'"{value}"' for value in values)


def render_java(profile: Profile, contract_identity: int, profile_identity: int) -> str:
    """Render the generated Java benchmark contract."""
    tests = profile["tests"]
    expected = [crc32(result_block(contract_identity, test)) for test in tests]
    template = """// Generated by bench/w4bench/generate_profile.py; do not edit.
package w4me;

/** Build-time contract consumed by W4BenchRunner. */
public final class W4BenchProfile {
    public static final String PROFILE_ID = "w4bench-v1";
    public static final String PROFILE_STATE = "%s";
    public static final int PROFILE_CRC32 = 0x%08x;
    public static final int CONTRACT_CRC32 = 0x%08x;
    public static final int WARMUPS = %d;
    public static final int REPETITIONS = %d;
    public static final int MIN_TIMED_MS = %d;
    public static final int RESULT_OFFSET = %d;
    public static final long INSTRUCTION_LIMIT = %dL;
    public static final int VALIDATION_TEST_ID = 0x%08x;
    public static final long VALIDATION_PAYLOAD = 0x%016xL;
    public static final int VALIDATION_EXPECTED_CRC32 = 0x%08x;
    public static final int[] TEST_IDS = { %s };
    public static final String[] TEST_NAMES = { %s };
    public static final String[] PREPARE_EXPORTS = { %s };
    public static final String[] RUN_EXPORTS = { %s };
    public static final int[] WORKLOAD_UNITS = { %s };
    public static final int[] RESULT_LENGTHS = { %s };
    public static final int[] EXPECTED_CRC32 = { %s };

    private W4BenchProfile() {
    }
}
"""
    return template % (
        profile["state"],
        profile_identity,
        contract_identity,
        profile["warmup_runs"],
        profile["measured_runs"],
        profile["min_timed_ms"],
        profile["result_offset"],
        profile["instruction_limit"],
        VALIDATION_TEST_ID,
        validation_payload(),
        validation_result_crc(contract_identity),
        java_int_array([test["id"] for test in tests]),
        java_string_array([test["name"] for test in tests]),
        java_string_array([test["prepare_export"] for test in tests]),
        java_string_array([test["run_export"] for test in tests]),
        java_int_array([test["work_units"] for test in tests]),
        java_int_array([profile["result_length"] for test in tests]),
        java_int_array(expected),
    )


def wat_dispatch(tests: list[TestCase], function_prefix: str, result_type: str) -> str:
    """Render the active-test dispatch ladder."""
    lines = []
    for test in tests:
        lines.extend(
            (
                "    global.get $active",
                f"    i32.const {test['id']}",
                "    i32.eq",
                f"    if (result {result_type})",
                f"      call ${function_prefix}_{test['id']}",
                "    else",
            )
        )
    zero_type = "i64" if result_type == "i64" else "i32"
    lines.append(f"      {zero_type}.const 0")
    lines.extend("    end" for _unused in tests)
    return "\n".join(lines)


def wat_explicit_exports(tests: list[TestCase]) -> str:
    """Render the stable per-test prepare and run exports."""
    lines = []
    for test in tests:
        prepare = test["prepare_export"]
        run = test["run_export"]
        lines.extend(
            (
                f'  (func (export "{prepare}")',
                f"    i32.const {test['id']}",
                "    global.set $active",
                f"    i32.const {test['id']}",
                "    global.set $result_test",
                f"    i32.const {test['work_units']}",
                "    global.set $result_units",
                "    i32.const 1",
                "    global.set $result_status",
            )
        )
        if test["id"] == MEMORY_TEST_ID:
            lines.append("    call $prepare_memory")
        lines.append("  )")
        lines.extend(
            (
                f'  (func (export "{run}")',
                f"    call $kernel_{test['id']}",
                "    global.set $last_payload",
                "    i32.const 0",
                "    global.set $result_status",
                "  )",
            )
        )
    return "\n".join(lines)


def coverage_unary(value_type: str, opcode: str, value: float) -> str:
    """Render one unary-opcode validation probe."""
    return f"    {value_type}.const {value}\n    nop\n    {opcode}\n    drop"


def coverage_binary(
    value_type: str,
    opcode: str,
    left: float,
    right: float,
) -> str:
    """Render one binary-opcode validation probe."""
    return "\n".join(
        (
            f"    {value_type}.const {left}",
            "    nop",
            f"    {value_type}.const {right}",
            "    nop",
            f"    {opcode}",
            "    drop",
        )
    )


def render_coverage_wat() -> str:
    """Return untimed, fusion-separated probes for every successful opcode."""
    i32_compare = [
        "i32.eq",
        "i32.ne",
        "i32.lt_s",
        "i32.lt_u",
        "i32.gt_s",
        "i32.gt_u",
        "i32.le_s",
        "i32.le_u",
        "i32.ge_s",
        "i32.ge_u",
    ]
    i32_unary = ["i32.clz", "i32.ctz", "i32.popcnt"]
    i32_binary = [
        "i32.add",
        "i32.sub",
        "i32.mul",
        "i32.div_s",
        "i32.div_u",
        "i32.rem_s",
        "i32.rem_u",
        "i32.and",
        "i32.or",
        "i32.xor",
        "i32.shl",
        "i32.shr_s",
        "i32.shr_u",
        "i32.rotl",
        "i32.rotr",
    ]
    i64_compare = [
        "i64.eq",
        "i64.ne",
        "i64.lt_s",
        "i64.lt_u",
        "i64.gt_s",
        "i64.gt_u",
        "i64.le_s",
        "i64.le_u",
        "i64.ge_s",
        "i64.ge_u",
    ]
    i64_unary = ["i64.clz", "i64.ctz", "i64.popcnt"]
    i64_binary = [
        "i64.add",
        "i64.sub",
        "i64.mul",
        "i64.div_s",
        "i64.div_u",
        "i64.rem_s",
        "i64.rem_u",
        "i64.and",
        "i64.or",
        "i64.xor",
        "i64.shl",
        "i64.shr_s",
        "i64.shr_u",
        "i64.rotl",
        "i64.rotr",
    ]
    f32_compare = ["f32.eq", "f32.ne", "f32.lt", "f32.gt", "f32.le", "f32.ge"]
    f64_compare = ["f64.eq", "f64.ne", "f64.lt", "f64.gt", "f64.le", "f64.ge"]
    f32_unary = [
        "f32.abs",
        "f32.neg",
        "f32.ceil",
        "f32.floor",
        "f32.trunc",
        "f32.nearest",
        "f32.sqrt",
    ]
    f64_unary = [
        "f64.abs",
        "f64.neg",
        "f64.ceil",
        "f64.floor",
        "f64.trunc",
        "f64.nearest",
        "f64.sqrt",
    ]
    f32_binary = [
        "f32.add",
        "f32.sub",
        "f32.mul",
        "f32.div",
        "f32.min",
        "f32.max",
        "f32.copysign",
    ]
    f64_binary = [
        "f64.add",
        "f64.sub",
        "f64.mul",
        "f64.div",
        "f64.min",
        "f64.max",
        "f64.copysign",
    ]
    i32_lines = [
        "  (func $cover_i32 (result i64)",
        coverage_unary("i32", "i32.eqz", "7"),
    ]
    i32_lines.extend(
        coverage_binary("i32", opcode, "17", "3") for opcode in i32_compare
    )
    i32_lines.extend(coverage_unary("i32", opcode, "17") for opcode in i32_unary)
    i32_lines.extend(coverage_binary("i32", opcode, "17", "3") for opcode in i32_binary)
    i32_lines.extend(("    i64.const 0x1122334455667788", "  )"))
    i64_lines = [
        "  (func $cover_i64 (result i64)",
        coverage_unary("i64", "i64.eqz", "7"),
    ]
    i64_lines.extend(
        coverage_binary("i64", opcode, "17", "3") for opcode in i64_compare
    )
    i64_lines.extend(coverage_unary("i64", opcode, "17") for opcode in i64_unary)
    i64_lines.extend(coverage_binary("i64", opcode, "17", "3") for opcode in i64_binary)
    i64_lines.extend(("    i64.const 0x2233445566778899", "  )"))
    f32_lines = ["  (func $cover_f32 (result i64)"]
    f32_lines.extend(
        coverage_binary("f32", opcode, "1.25", "2.5") for opcode in f32_compare
    )
    f32_lines.extend(coverage_unary("f32", opcode, "1.25") for opcode in f32_unary)
    f32_lines.extend(
        coverage_binary("f32", opcode, "1.25", "2.5") for opcode in f32_binary
    )
    f32_lines.extend(("    i64.const 0x33445566778899aa", "  )"))
    f64_lines = ["  (func $cover_f64 (result i64)"]
    f64_lines.extend(
        coverage_binary("f64", opcode, "1.25", "2.5") for opcode in f64_compare
    )
    f64_lines.extend(coverage_unary("f64", opcode, "1.25") for opcode in f64_unary)
    f64_lines.extend(
        coverage_binary("f64", opcode, "1.25", "2.5") for opcode in f64_binary
    )
    f64_lines.extend(("    i64.const 0x445566778899aabb", "  )"))
    return """
  (func $return_one (result i32)
    i32.const 1
    nop
    return
    i32.const 0)
  (func $cover_control (result i64) (local $x i32)
    nop
    i32.const 7
    nop
    local.set $x
    nop
    local.get $x
    nop
    local.tee $x
    drop
    global.get $sweep_global
    nop
    global.set $sweep_global
    block $done
      loop $again
        local.get $x
        nop
        i32.eqz
        br_if $done
        local.get $x
        nop
        i32.const 1
        nop
        i32.sub
        local.set $x
        br $again
      end
    end
    i32.const 1
    if (result i32)
      i32.const 3
    else
      i32.const 4
    end
    drop
    block $control_zero
      i32.const 0
      br_table $control_zero $control_zero
    end
    call $return_one
    drop
    i32.const 4
    i32.const 9
    i32.const 1
    select
    drop
    i32.const 4
    i32.const 9
    i32.const 0
    select (result i32)
    drop
    i32.const 17
    i32.const 3
    i32.const 2
    call_indirect (type $step)
    drop
    i64.const 0x0123456789abcdef)

  (func $cover_memory (result i64) (local $x i64)
    i32.const 12288
    i32.const 0x11223344
    i32.store
    i32.const 12288
    nop
    i32.load
    drop
    i32.const 12296
    i64.const 0x1122334455667788
    i64.store
    i32.const 12296
    nop
    i64.load
    drop
    i32.const 12304
    f32.const 1.25
    f32.store
    i32.const 12304
    nop
    f32.load
    drop
    i32.const 12312
    f64.const 1.25
    f64.store
    i32.const 12312
    nop
    f64.load
    drop
    i32.const 12320
    i32.const 0xff80
    i32.store8
    i32.const 12320
    nop
    i32.load8_s
    drop
    i32.const 12320
    nop
    i32.load8_u
    drop
    i32.const 12322
    i32.const 0xff80
    i32.store16
    i32.const 12322
    nop
    i32.load16_s
    drop
    i32.const 12322
    nop
    i32.load16_u
    drop
    i32.const 12328
    i64.const 0xffffffffffffff80
    i64.store8
    i32.const 12328
    nop
    i64.load8_s
    drop
    i32.const 12328
    nop
    i64.load8_u
    drop
    i32.const 12330
    i64.const 0xffffffffffff8000
    i64.store16
    i32.const 12330
    nop
    i64.load16_s
    drop
    i32.const 12330
    nop
    i64.load16_u
    drop
    i32.const 12336
    i64.const 0xffffffff80000000
    i64.store32
    i32.const 12336
    nop
    i64.load32_s
    drop
    i32.const 12336
    nop
    i64.load32_u
    drop
    memory.size
    drop
    i32.const 0
    memory.grow
    drop
    i64.const 0x1020304050607080)

{}

{}

{}

{}

  (func $cover_convert (result i64)
    i64.const 7
    nop
    i32.wrap_i64
    drop
    f32.const 7
    nop
    i32.trunc_f32_s
    drop
    f32.const 7
    nop
    i32.trunc_f32_u
    drop
    f64.const 7
    nop
    i32.trunc_f64_s
    drop
    f64.const 7
    nop
    i32.trunc_f64_u
    drop
    i32.const -7
    nop
    i64.extend_i32_s
    drop
    i32.const 7
    nop
    i64.extend_i32_u
    drop
    f32.const 7
    nop
    i64.trunc_f32_s
    drop
    f32.const 7
    nop
    i64.trunc_f32_u
    drop
    f64.const 7
    nop
    i64.trunc_f64_s
    drop
    f64.const 7
    nop
    i64.trunc_f64_u
    drop
    i32.const -7
    nop
    f32.convert_i32_s
    drop
    i32.const 7
    nop
    f32.convert_i32_u
    drop
    i64.const -7
    nop
    f32.convert_i64_s
    drop
    i64.const 7
    nop
    f32.convert_i64_u
    drop
    f64.const 7
    nop
    f32.demote_f64
    drop
    i32.const -7
    nop
    f64.convert_i32_s
    drop
    i32.const 7
    nop
    f64.convert_i32_u
    drop
    i64.const -7
    nop
    f64.convert_i64_s
    drop
    i64.const 7
    nop
    f64.convert_i64_u
    drop
    f32.const 7
    nop
    f64.promote_f32
    drop
    f32.const 1.25
    nop
    i32.reinterpret_f32
    drop
    f64.const 1.25
    nop
    i64.reinterpret_f64
    drop
    i32.const 0x3f800000
    nop
    f32.reinterpret_i32
    drop
    i64.const 0x3ff0000000000000
    nop
    f64.reinterpret_i64
    drop
    i32.const 0x80
    nop
    i32.extend8_s
    drop
    i32.const 0x8000
    nop
    i32.extend16_s
    drop
    i64.const 0x80
    nop
    i64.extend8_s
    drop
    i64.const 0x8000
    nop
    i64.extend16_s
    drop
    i64.const 0x80000000
    nop
    i64.extend32_s
    drop
    f32.const nan
    nop
    i32.trunc_sat_f32_s
    drop
    f32.const nan
    nop
    i32.trunc_sat_f32_u
    drop
    f64.const nan
    nop
    i32.trunc_sat_f64_s
    drop
    f64.const nan
    nop
    i32.trunc_sat_f64_u
    drop
    f32.const nan
    nop
    i64.trunc_sat_f32_s
    drop
    f32.const nan
    nop
    i64.trunc_sat_f32_u
    drop
    f64.const nan
    nop
    i64.trunc_sat_f64_s
    drop
    f64.const nan
    nop
    i64.trunc_sat_f64_u
    drop
    ;; Semantic sentinels for correctly rounded unsigned-i64 conversion.
    i64.const 0x8000008000000001
    nop
    f32.convert_i64_u
    nop
    i32.reinterpret_f32
    nop
    i64.extend_i32_u
    i64.const 32
    i64.shl
    i64.const 0xc000000000000401
    nop
    f64.convert_i64_u
    nop
    i64.reinterpret_f64
    i64.xor)

  (func $cover_bulk (result i64)
    i32.const 12400
    i32.const 0
    i32.const 7
    memory.init $sweep_data
    i32.const 12408
    i32.const 12400
    i32.const 7
    memory.copy
    i32.const 12416
    i32.const 0x5a
    i32.const 7
    memory.fill
    data.drop $sweep_data
    i64.const 0x66778899aabbccdd)

  (func (export "cover_control") call $cover_control drop)
  (func (export "cover_memory") call $cover_memory drop)
  (func (export "cover_i32") call $cover_i32 drop)
  (func (export "cover_i64") call $cover_i64 drop)
  (func (export "cover_f32") call $cover_f32 drop)
  (func (export "cover_f64") call $cover_f64 drop)
  (func (export "cover_convert") call $cover_convert drop)
  (func (export "cover_bulk") call $cover_bulk drop)
""".format(
        "\n".join(i32_lines),
        "\n".join(i64_lines),
        "\n".join(f32_lines),
        "\n".join(f64_lines),
    )


def render_wat(profile: Profile, contract_identity: int) -> str:
    """Render the deterministic WAT benchmark cartridge."""
    tests = profile["tests"]
    seed = {test["id"]: test["seed"] for test in tests}
    units = {test["id"]: test["work_units"] for test in tests}
    prepare_cases = []
    for test in tests:
        prepare_cases.extend(
            (
                "    global.get $active",
                f"    i32.const {test['id']}",
                "    i32.eq",
                "    if",
                f"      i32.const {test['id']}",
                "      global.set $result_test",
                f"      i32.const {units[test['id']]}",
                "      global.set $result_units",
                "      i32.const 1",
                "      global.set $result_status",
                "    end",
            )
        )
    template = """;; Generated by bench/w4bench/generate_profile.py; do not edit.
;; W4Bench V1 is a test-only deterministic cartridge. Timed run() has no clock,
;; logging or CRC: call prepare(), time run(), then call report() and validate
;; the 32-byte little-endian block at offset 8192 in the host harness.
(module
  (memory (export "memory") 1)
  (table 3 funcref)
  (elem (i32.const 0) $indirect_a $indirect_b $coverage_indirect)
  (data $sweep_data "W4Bench")
  (type $step (func (param i32 i32) (result i32)))

  (global $active (mut i32) (i32.const -1))
  (global $next (mut i32) (i32.const 0))
  (global $result_test (mut i32) (i32.const 0))
  (global $result_units (mut i32) (i32.const 0))
  (global $result_status (mut i32) (i32.const 2))
  (global $last_payload (mut i64) (i64.const 0))
  (global $sweep_global (mut i32) (i32.const 0))
  (global $bulk_done (mut i32) (i32.const 0))

  (func $start
    i32.const 0
    global.set $next
    i32.const -1
    global.set $active)
  (start $start)

  (func (export "update"))
  (func (export "reset")
    call $start)

  (func $direct_step (param $x i32) (param $i i32) (result i32)
    local.get $x
    local.get $i
    i32.xor
    i32.const 0x7feb352d
    i32.mul
    i32.const 16
    i32.rotr)
  (func $indirect_a (type $step) (param $x i32) (param $i i32) (result i32)
    local.get $x
    local.get $i
    i32.const 3
    i32.mul
    i32.add
    i32.const 5
    i32.rotl
    i32.const 0x85ebca6b
    i32.xor)
  (func $indirect_b (type $step) (param $x i32) (param $i i32) (result i32)
    local.get $x
    local.get $i
    i32.const 0x27d4eb2d
    i32.mul
    i32.xor
    i32.const 7
    i32.rotr
    i32.const 0x165667b1
    i32.add)
  (func $coverage_indirect (type $step) (param $x i32) (param $i i32) (result i32)
    local.get $x
    nop
    local.get $i
    nop
    i32.add)

  (func $kernel_1 (result i64) (local $i i32) (local $x i32)
    i32.const 0x%08x
    local.set $x
    block $done
      loop $again
        local.get $i
        i32.const %d
        i32.ge_u
        br_if $done
        local.get $x
        local.get $i
        i32.add
        i32.const 13
        i32.rotl
        i32.const 0x9e3779b9
        i32.xor
        local.set $x
        local.get $x
        i32.const 1
        i32.and
        if
          local.get $x
          i32.const 7
          i32.add
          local.set $x
        else
          local.get $x
          i32.const 3
          i32.sub
          local.set $x
        end
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $again
      end
    end
    local.get $x
    i64.extend_i32_u)

  (func $kernel_2 (result i64) (local $i i32) (local $x i32)
    i32.const 0x%08x
    local.set $x
    block $done
      loop $again
        local.get $i
        i32.const %d
        i32.ge_u
        br_if $done
        local.get $x
        local.get $i
        call $direct_step
        local.set $x
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $again
      end
    end
    local.get $x
    i64.extend_i32_u)

  (func $kernel_3 (result i64) (local $i i32) (local $x i32)
    i32.const 0x%08x
    local.set $x
    block $done
      loop $again
        local.get $i
        i32.const %d
        i32.ge_u
        br_if $done
        local.get $x
        local.get $i
        local.get $i
        i32.const 1
        i32.and
        call_indirect (type $step)
        local.set $x
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $again
      end
    end
    local.get $x
    i64.extend_i32_u)

  (func $prepare_memory
    i32.const 12288
    i32.const 0
    i32.store
    i32.const 12292
    i32.const 0
    i32.store)
  (func $kernel_4 (result i64) (local $i i32) (local $x i32) (local $addr i32)
    i32.const 0x%08x
    local.set $x
    block $done
      loop $again
        local.get $i
        i32.const %d
        i32.ge_u
        br_if $done
        i32.const 12288
        local.get $i
        i32.const 63
        i32.and
        i32.const 8
        i32.mul
        i32.add
        local.set $addr
        local.get $addr
        local.get $x
        i32.store
        local.get $addr
        i32.const 4
        i32.add
        local.get $x
        i32.store8
        local.get $addr
        i32.const 5
        i32.add
        local.get $x
        i32.store16
        local.get $addr
        i32.load
        local.get $addr
        i32.const 4
        i32.add
        i32.load8_u
        i32.xor
        local.get $addr
        i32.const 5
        i32.add
        i32.load16_u
        i32.xor
        local.get $i
        i32.xor
        i32.const 3
        i32.rotl
        local.set $x
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $again
      end
    end
    local.get $x
    i64.extend_i32_u)

  (func $kernel_5 (result i64) (local $i i32) (local $x i64)
    i64.const 0x%08x%08x
    local.set $x
    block $done
      loop $again
        local.get $i
        i32.const %d
        i32.ge_u
        br_if $done
        local.get $x
        local.get $i
        i64.extend_i32_u
        i64.add
        i64.const 17
        i64.rotl
        i64.const 0x9e3779b97f4a7c15
        i64.xor
        local.set $x
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $again
      end
    end
    local.get $x)

  (func $kernel_6 (result i64) (local $i i32) (local $x f32)
    i32.const 0x%08x
    f32.reinterpret_i32
    local.set $x
    block $done
      loop $again
        local.get $i
        i32.const %d
        i32.ge_u
        br_if $done
        local.get $x
        f32.const 1.0001220703125
        f32.mul
        local.get $i
        f32.convert_i32_u
        f32.const 0.000001
        f32.mul
        f32.add
        local.set $x
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $again
      end
    end
    local.get $x
    i32.reinterpret_f32
    i64.extend_i32_u)

  (func $kernel_7 (result i64) (local $i i32) (local $x f64)
    i64.const 0x3ff00000%08x
    f64.reinterpret_i64
    local.set $x
    block $done
      loop $again
        local.get $i
        i32.const %d
        i32.ge_u
        br_if $done
        local.get $x
        f64.const 1.0000001
        f64.mul
        local.get $i
        f64.convert_i32_u
        f64.const 0.000000001
        f64.mul
        f64.add
        local.set $x
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $again
      end
    end
    local.get $x
    i64.reinterpret_f64)

%s

%s

  ;; setup is explicitly outside timed run().
  (func (export "prepare")
    global.get $next
    global.set $active
    global.get $next
    i32.const 1
    i32.add
    i32.const 7
    i32.rem_u
    global.set $next
%s
    global.get $active
    i32.const 4
    i32.eq
    if
      call $prepare_memory
    end)

  ;; This is the only interval the host times. It has no clock, output write,
  ;; CRC, logging, allocation, or preparation work.
  (func (export "run")
    global.get $active
    i32.const 0
    i32.lt_s
    if
      i32.const 2
      global.set $result_status
    else
%s
      global.set $last_payload
      i32.const 0
      global.set $result_status
    end)

  (func $write_u32 (param $offset i32) (param $value i32)
    local.get $offset
    local.get $value
    i32.store)
  (func $report (export "report")
    i32.const 8192
    i32.const 0x57423431
    call $write_u32
    i32.const 8196
    i32.const 1
    call $write_u32
    i32.const 8200
    i32.const 0x%08x
    call $write_u32
    i32.const 8204
    global.get $result_test
    call $write_u32
    i32.const 8208
    global.get $result_units
    call $write_u32
    i32.const 8212
    global.get $result_status
    call $write_u32
    i32.const 8216
    global.get $last_payload
    i32.wrap_i64
    call $write_u32
    i32.const 8220
    global.get $last_payload
    i64.const 32
    i64.shr_u
    i32.wrap_i64
    call $write_u32)

  ;; Untimed diagnostic sweep. The catalog records opcode ownership separately
  ;; from the timed score so rare instructions never skew performance numbers.
  (func $opcode_sweep (result i64) (local $x i64)
    call $cover_control
    call $cover_memory
    i64.xor
    call $cover_i32
    i64.xor
    call $cover_i64
    i64.xor
    call $cover_f32
    i64.xor
    call $cover_f64
    i64.xor
    call $cover_convert
    i64.xor
    global.get $bulk_done
    if (result i64)
      i64.const 0
    else
      call $cover_bulk
      i32.const 1
      global.set $bulk_done
    end
    i64.xor)
  (func (export "validate_all")
    i32.const 32768
    global.set $result_test
    i32.const 0
    global.set $result_units
    call $opcode_sweep
    global.set $last_payload
    i32.const 0
    global.set $result_status
    call $report)
  (func (export "trap_unreachable")
    unreachable)
)
"""
    return template % (
        seed[1],
        units[1],
        seed[2],
        units[2],
        seed[3],
        units[3],
        seed[4],
        units[4],
        seed[5],
        seed[5] ^ 0xA5A5A5A5,
        units[5],
        seed[6],
        units[6],
        seed[7],
        units[7],
        wat_explicit_exports(tests),
        render_coverage_wat(),
        "\n".join(prepare_cases),
        wat_dispatch(tests, "kernel", "i64"),
        contract_identity,
    )


def write_output(path: str | Path, content: str) -> None:
    """Write one generated UTF-8 artifact, creating its parent directory."""
    output_path = Path(path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8", newline="\n") as output:
        output.write(content)


def generate(
    profile_path: str | Path = DEFAULT_PROFILE,
    output_dir: str | Path = DEFAULT_OUTPUT,
) -> tuple[Profile, int, int]:
    """Generate and validate every W4Bench artifact."""
    profile = load_profile(profile_path)
    contract_identity = contract_crc(profile)
    profile_identity = frozen_profile_crc(profile, contract_identity)
    output_root = Path(output_dir)
    catalog_path = output_root / "opcode_catalog_v1.json"
    write_output(catalog_path, render_catalog())
    validate_catalog(catalog_path)
    write_output(
        output_root / "java" / "w4me" / "W4BenchProfile.java",
        render_java(profile, contract_identity, profile_identity),
    )
    write_output(
        output_root / "w4bench_v1.wat",
        render_wat(profile, contract_identity),
    )
    return profile, contract_identity, profile_identity


def require_frozen_profile(path: str | Path) -> None:
    """Reject a profile that is not ready for authoritative measurement."""
    candidate_profile = load_profile(path)
    if candidate_profile["state"] != "FROZEN":
        msg = "authoritative benchmark requires a FROZEN profile"
        raise ValueError(msg)


def main(argv: list[str]) -> int:
    """Generate W4Bench artifacts and report their stable identities."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", default=DEFAULT_PROFILE)
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT)
    parser.add_argument("--require-frozen", action="store_true")
    options = parser.parse_args(argv)
    try:
        if options.require_frozen:
            require_frozen_profile(options.profile)
        profile, contract_identity, profile_identity = generate(
            options.profile, options.output_dir
        )
    except (OSError, ValueError, TypeError) as error:
        sys.stderr.write(f"error: {error}\n")
        return 1
    sys.stdout.write(
        f"W4Bench V1 {profile['state']} contract_crc32={contract_identity:08x} "
        f"profile_crc32={profile_identity:08x} tests={len(profile['tests'])}\n"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
