#!/usr/bin/env python3
# Copyright 2026 W4ME Station contributors
# SPDX-License-Identifier: MIT
#
"""Host-side contract tests for W4Bench V1."""

import copy
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any

CRC32_CHECK_VECTOR = 3421780262
SOURCE_OPCODE_COUNT = 190
VALIDATION_F32_EXPECTED_BITS = 1593835521
VALIDATION_F64_EXPECTED_BITS = 4893160995138043905

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import generate_profile  # noqa: E402  # Local contract module after path bootstrap.
import reference_oracle  # noqa: E402  # Independent oracle after path bootstrap.


class W4BenchContractTest(unittest.TestCase):
    """Enforce the frozen W4Bench profile and generated output contract."""

    profile: dict[str, Any]
    contract_crc: int
    profile_crc: int

    def setUp(self) -> None:
        """Load the canonical profile and its stable identities."""
        self.profile = generate_profile.load_profile(ROOT / "profile_v1.json")
        self.contract_crc = generate_profile.contract_crc(self.profile)
        self.profile_crc = generate_profile.frozen_profile_crc(
            self.profile, self.contract_crc
        )

    def test_ieee_crc32_known_vector(self) -> None:
        """Match the standard CRC-32 check vector."""
        assert generate_profile.crc32(b"123456789") == CRC32_CHECK_VECTOR

    def test_catalog_is_exact_complete_source_set(self) -> None:
        """Require a complete and exact source-opcode catalog."""
        catalog = generate_profile.validate_catalog_data(
            json.loads(generate_profile.render_catalog())
        )
        assert len(catalog["entries"]) == SOURCE_OPCODE_COUNT
        assert sum(1 for entry in catalog["entries"] if entry["mode"] == "trap") == 1
        assert not any(entry["mode"] == "pending" for entry in catalog["entries"])
        timed = {
            int(entry["opcode"], 16)
            for entry in catalog["entries"]
            if entry["mode"] == "timed"
        }
        assert timed == generate_profile.TIMED_SOURCE_OPCODES
        for entry in catalog["entries"]:
            assert entry["name"] == generate_profile.opcode_token(
                int(entry["opcode"], 16)
            )

    def test_build_outputs_are_deterministic(self) -> None:
        """Generate every tracked artifact twice and compare exact bytes."""
        with (
            tempfile.TemporaryDirectory() as first,
            tempfile.TemporaryDirectory() as second,
        ):
            first_root = Path(first)
            second_root = Path(second)
            generate_profile.generate(ROOT / "profile_v1.json", first_root)
            generate_profile.generate(ROOT / "profile_v1.json", second_root)
            for relative in (
                Path("opcode_catalog_v1.json"),
                Path("w4bench_v1.wat"),
                Path("java") / "w4me" / "W4BenchProfile.java",
            ):
                first_bytes = (first_root / relative).read_bytes()
                second_bytes = (second_root / relative).read_bytes()
                assert first_bytes == second_bytes, relative

    def test_authoritative_gate_rejects_precalibration(self) -> None:
        """Reject a non-frozen profile at the authoritative generation gate."""
        profile = copy.deepcopy(self.profile)
        profile["state"] = "PRECALIBRATION"
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".json", delete=False
        ) as output:
            json.dump(profile, output)
            path = output.name
        try:
            with tempfile.TemporaryDirectory() as output_dir:
                completed = subprocess.run(  # noqa: S603  # Fixed repository script.
                    [
                        sys.executable,
                        str(ROOT / "generate_profile.py"),
                        "--profile",
                        path,
                        "--output-dir",
                        output_dir,
                        "--require-frozen",
                    ],
                    capture_output=True,
                    check=False,
                    text=True,
                )
            assert completed.returncode == 1
            assert "requires a FROZEN profile" in completed.stderr
        finally:
            Path(path).unlink()

    def test_frozen_calibration_matches_tracked_contract(self) -> None:
        """Match the tracked calibration to the frozen profile and generator."""
        with (ROOT / "calibration_v1.json").open(encoding="utf-8") as source:
            calibration = json.load(source)
        assert calibration["profile_state"] == "FROZEN"
        assert calibration["profile_crc32"] == f"{self.profile_crc:08x}"
        assert calibration["contract_crc32"] == f"{self.contract_crc:08x}"
        profile_sha256 = hashlib.sha256(
            (ROOT / "profile_v1.json").read_bytes()
        ).hexdigest()
        assert calibration["profile_sha256"] == profile_sha256
        generated_java = generate_profile.render_java(
            self.profile, self.contract_crc, self.profile_crc
        ).encode("utf-8")
        assert (
            calibration["generated_profile_sha256"]
            == hashlib.sha256(generated_java).hexdigest()
        )
        assert set(calibration["median_wall_ms"]) == {
            test["name"] for test in self.profile["tests"]
        }
        assert all(
            value >= self.profile["min_timed_ms"]
            for value in calibration["median_wall_ms"].values()
        )

    def test_oracle_matches_generated_payload_and_crc(self) -> None:
        """Match the independent oracle to every generated result."""
        assert reference_oracle.contract_crc(self.profile) == self.contract_crc
        raw_profile = reference_oracle.load_profile(ROOT / "profile_v1.json")
        assert reference_oracle.contract_crc(raw_profile) == self.contract_crc
        for test in self.profile["tests"]:
            expected_payload = generate_profile.payload_for_test(test)
            expected_crc = generate_profile.crc32(
                generate_profile.result_block(self.contract_crc, test)
            )
            assert reference_oracle.payload(test) == expected_payload
            assert reference_oracle.result_crc32(self.profile, test) == expected_crc
        assert (
            reference_oracle.validation_payload()
            == generate_profile.validation_payload()
        )
        assert reference_oracle.validation_result_crc32(
            self.profile
        ) == generate_profile.validation_result_crc(self.contract_crc)
        assert (
            reference_oracle.unsigned_integer_to_ieee_bits(
                reference_oracle.VALIDATION_F32_UNSIGNED_I64,
                23,
                127,
            )
            == VALIDATION_F32_EXPECTED_BITS
        )
        assert (
            reference_oracle.unsigned_integer_to_ieee_bits(
                reference_oracle.VALIDATION_F64_UNSIGNED_I64,
                52,
                1023,
            )
            == VALIDATION_F64_EXPECTED_BITS
        )

    def test_tampered_profile_is_rejected(self) -> None:
        """Reject a profile whose frozen calibration values were altered."""
        broken = copy.deepcopy(self.profile)
        broken["tests"][0]["work_units"] = 7
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".json", delete=False
        ) as output:
            json.dump(broken, output)
            path = output.name
        try:
            try:
                generate_profile.load_profile(path)
            except ValueError:
                pass
            else:
                self.fail("tampered profile was accepted")
        finally:
            Path(path).unlink()

    def test_required_runtime_exports_are_generated(self) -> None:
        """Generate every runtime export required by the contract."""
        wat = generate_profile.render_wat(self.profile, self.contract_crc)
        for name in (
            "cover_control",
            "cover_memory",
            "cover_i32",
            "cover_i64",
            "cover_f32",
            "cover_f64",
            "cover_convert",
            "cover_bulk",
            "validate_all",
            "trap_unreachable",
            "report",
        ):
            assert f'(export "{name}")' in wat
        for test in self.profile["tests"]:
            assert f'(export "{test["prepare_export"]}")' in wat
            assert f'(export "{test["run_export"]}")' in wat

    def test_every_catalogued_opcode_has_a_wat_probe_token(self) -> None:
        """Require an executable WAT probe token for every catalog entry."""
        catalog = generate_profile.validate_catalog_data(
            json.loads(generate_profile.render_catalog())
        )
        wat = generate_profile.render_wat(self.profile, self.contract_crc)
        for entry in catalog["entries"]:
            assert entry["wat_token"] in wat, entry["opcode"]


if __name__ == "__main__":
    unittest.main()
