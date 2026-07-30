#!/usr/bin/env python3
# Copyright 2026 W4ME Station contributors
# SPDX-License-Identifier: MIT
#
"""Independent W4Bench V1 result oracle.

It intentionally reimplements the seven workload kernels instead of importing
the generator's calculator.  The host benchmark uses this only outside its
timed interval to check the cartridge's fixed little-endian result block.
"""

import argparse
import json
import struct
import sys
import zlib
from pathlib import Path
from typing import TypeAlias

Profile: TypeAlias = dict[str, object]
TestCase: TypeAlias = dict[str, object]

ROOT = Path(__file__).resolve().parent
PROFILE_PATH = ROOT / "profile_v1.json"
MASK32 = 0xFFFFFFFF
MASK64 = 0xFFFFFFFFFFFFFFFF
MAGIC = 0x57423431
CONTRACT_VERSION = 1
VALIDATION_TEST_ID = 0x8000
VALIDATION_F32_UNSIGNED_I64 = 0x8000008000000001
VALIDATION_F64_UNSIGNED_I64 = 0xC000000000000401
VALIDATION_COVER_VALUES = (
    0x0123456789ABCDEF,
    0x1020304050607080,
    0x1122334455667788,
    0x2233445566778899,
    0x33445566778899AA,
    0x445566778899AABB,
    0x66778899AABBCCDD,
)


def canonical_json(value: object) -> str:
    """Serialize a value using the contract's canonical JSON form."""
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def parse_seed(value: int | str) -> int:
    """Parse a decimal or hexadecimal profile seed."""
    return int(value, 16) if isinstance(value, str) else int(value)


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


def as_f32(value: float) -> float:
    """Round one Python float to IEEE-754 binary32."""
    return struct.unpack("<f", struct.pack("<f", value))[0]


def f32_bits(value: float) -> int:
    """Return the IEEE-754 binary32 bit pattern."""
    return struct.unpack("<I", struct.pack("<f", value))[0]


def f64_bits(value: float) -> int:
    """Return the IEEE-754 binary64 bit pattern."""
    return struct.unpack("<Q", struct.pack("<d", value))[0]


def unsigned_integer_to_ieee_bits(
    value: int, fraction_bits: int, exponent_bias: int
) -> int:
    """Convert an unsigned integer to a positive IEEE-754 bit pattern."""
    if value == 0:
        return 0
    exponent = value.bit_length() - 1
    if exponent <= fraction_bits:
        significand = value << (fraction_bits - exponent)
    else:
        shift = exponent - fraction_bits
        significand = value >> shift
        remainder = value & ((1 << shift) - 1)
        half = 1 << (shift - 1)
        if remainder > half or (remainder == half and (significand & 1) != 0):
            significand += 1
            if significand == 1 << (fraction_bits + 1):
                significand >>= 1
                exponent += 1
    fraction = significand & ((1 << fraction_bits) - 1)
    return ((exponent + exponent_bias) << fraction_bits) | fraction


def kernel_i32_control(test: TestCase) -> int:
    """Execute the i32 control-flow oracle kernel."""
    x = parse_seed(test["seed"])
    for index in range(test["work_units"]):
        x = rotl32(u32(x + index), 13) ^ 0x9E3779B9
        x = u32(x + 7) if x & 1 else u32(x - 3)
    return x


def kernel_direct_calls_locals(test: TestCase) -> int:
    """Execute the direct-call and local-variable oracle kernel."""
    x = parse_seed(test["seed"])
    for index in range(test["work_units"]):
        x = rotr32(u32((x ^ index) * 0x7FEB352D), 16)
    return x


def kernel_call_indirect_table(test: TestCase) -> int:
    """Execute the indirect-call table oracle kernel."""
    x = parse_seed(test["seed"])
    for index in range(test["work_units"]):
        if (index & 1) == 0:
            x = rotl32(u32(x + index * 3), 5) ^ 0x85EBCA6B
        else:
            x = u32(rotr32(u32(x ^ (index * 0x27D4EB2D)), 7) + 0x165667B1)
    return x


def kernel_memory_widths(test: TestCase) -> int:
    """Execute the mixed-width memory oracle kernel."""
    x = parse_seed(test["seed"])
    words = [0] * 64
    for index in range(test["work_units"]):
        slot = index & 63
        words[slot] = x
        x = u32(words[slot] ^ (x & 0xFF) ^ (x & 0xFFFF) ^ index)
        x = rotl32(x, 3)
    return x


def kernel_i64(test: TestCase) -> int:
    """Execute the i64 arithmetic oracle kernel."""
    seed = parse_seed(test["seed"])
    x = u64((seed << 32) | (seed ^ 0xA5A5A5A5))
    for index in range(test["work_units"]):
        x = rotl64(u64(x + index), 17) ^ 0x9E3779B97F4A7C15
    return x


def kernel_f32(test: TestCase) -> int:
    """Execute the f32 arithmetic oracle kernel."""
    seed = parse_seed(test["seed"])
    x = struct.unpack("<f", struct.pack("<I", seed))[0]
    mul = as_f32(1.0001220703125)
    scale = as_f32(0.000001)
    for index in range(test["work_units"]):
        x = as_f32(as_f32(x * mul) + as_f32(as_f32(float(index)) * scale))
    return f32_bits(x)


def kernel_f64(test: TestCase) -> int:
    """Execute the f64 arithmetic oracle kernel."""
    seed = parse_seed(test["seed"])
    x = struct.unpack("<d", struct.pack("<Q", 0x3FF0000000000000 | seed))[0]
    for index in range(test["work_units"]):
        x = x * 1.0000001 + float(index) * 0.000000001
    return f64_bits(x)


KERNELS = (
    kernel_i32_control,
    kernel_direct_calls_locals,
    kernel_call_indirect_table,
    kernel_memory_widths,
    kernel_i64,
    kernel_f32,
    kernel_f64,
)


def contract_crc(profile: Profile) -> int:
    """Compute the profile contract identity."""
    return zlib.crc32(canonical_json(profile).encode("utf-8")) & MASK32


def payload(test: TestCase) -> int:
    """Compute one workload's expected payload."""
    return KERNELS[test["id"] - 1](test)


def result_block(profile: Profile, test: TestCase) -> bytes:
    """Build one canonical little-endian result block."""
    value = payload(test)
    return struct.pack(
        "<IIIIIIII",
        MAGIC,
        CONTRACT_VERSION,
        contract_crc(profile),
        test["id"],
        test["work_units"],
        0,
        value & MASK32,
        (value >> 32) & MASK32,
    )


def result_crc32(profile: Profile, test: TestCase) -> int:
    """Compute the CRC-32 of one expected result block."""
    return zlib.crc32(result_block(profile, test)) & MASK32


def validation_payload() -> int:
    """Compute the fixed validation-only payload."""
    f32_result = unsigned_integer_to_ieee_bits(VALIDATION_F32_UNSIGNED_I64, 23, 127)
    f64_result = unsigned_integer_to_ieee_bits(VALIDATION_F64_UNSIGNED_I64, 52, 1023)
    value = (f32_result << 32) ^ f64_result
    for cover_value in VALIDATION_COVER_VALUES:
        value ^= cover_value
    return value


def validation_result_block(profile: Profile) -> bytes:
    """Build the fixed validation result block."""
    value = validation_payload()
    return struct.pack(
        "<IIIIIIII",
        MAGIC,
        CONTRACT_VERSION,
        contract_crc(profile),
        VALIDATION_TEST_ID,
        0,
        0,
        value & MASK32,
        (value >> 32) & MASK32,
    )


def validation_result_crc32(profile: Profile) -> int:
    """Compute the validation result block's CRC-32."""
    return zlib.crc32(validation_result_block(profile)) & MASK32


def load_profile(path: str | Path) -> Profile:
    """Load and normalize one W4Bench profile."""
    with Path(path).open(encoding="utf-8") as source:
        profile = json.load(source)
    # The generator canonicalizes hexadecimal seeds to u32 numbers before
    # deriving CONTRACT_CRC32; do the same without importing generator code.
    for test in profile.get("tests", []):
        test["seed"] = parse_seed(test["seed"])
    return profile


def main(argv: list[str]) -> int:
    """Print the independent result oracle for selected tests."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", default=PROFILE_PATH)
    parser.add_argument("--test", type=int)
    parser.add_argument("--all", action="store_true")
    options = parser.parse_args(argv)
    if options.test is None and not options.all:
        parser.error("select --test ID or --all")
    profile = load_profile(options.profile)
    tests = profile["tests"] if options.all else [profile["tests"][options.test - 1]]
    for test in tests:
        value = payload(test)
        sys.stdout.write(
            f"id={test['id']} name={test['name']} payload=0x{value:016x} "
            f"crc32={result_crc32(profile, test):08x}\n"
        )
    if options.all:
        sys.stdout.write(
            f"validation-id={VALIDATION_TEST_ID} "
            f"payload=0x{validation_payload():016x} "
            f"crc32={validation_result_crc32(profile):08x}\n"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
