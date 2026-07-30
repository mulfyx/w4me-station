#!/usr/bin/env python3
# Copyright 2026 W4ME Station contributors
# SPDX-License-Identifier: MIT
#
"""Validate repository-owned JSON and CSV files without rewriting them."""

from __future__ import annotations

import csv
import json
import sys
from pathlib import Path
from typing import TYPE_CHECKING, NoReturn

if TYPE_CHECKING:
    from collections.abc import Callable, Iterable

EXCLUDED_DIRECTORIES = {
    ".git",
    ".local",
    "__pycache__",
    "build",
    "dist",
    "generated",
    "node_modules",
    "target",
}


def fail(message: str) -> NoReturn:
    """Report one strict validation failure."""
    raise ValueError(message)


def owned_files(root: Path, suffix: str) -> Iterable[Path]:
    """Yield repository-owned files with the requested suffix."""
    for path in root.rglob(f"*{suffix}"):
        if any(part in EXCLUDED_DIRECTORIES for part in path.parts):
            continue
        yield path


def reject_duplicate_keys(pairs: list[tuple[str, object]]) -> dict[str, object]:
    """Reject JSON objects whose spelling hides a duplicate key."""
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            fail(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def reject_non_finite(value: str) -> NoReturn:
    """Reject NaN and infinities, which are not valid JSON numbers."""
    fail(f"non-finite JSON number: {value}")


def validate_json(path: Path) -> None:
    """Parse one JSON file using strict object and number handling."""
    with path.open(encoding="utf-8") as stream:
        json.load(
            stream,
            object_pairs_hook=reject_duplicate_keys,
            parse_constant=reject_non_finite,
        )


def validate_csv(path: Path) -> None:
    """Require every CSV row to have the same positive column count."""
    with path.open(encoding="utf-8", newline="") as stream:
        rows = csv.reader(stream, strict=True)
        expected_columns: int | None = None
        for line_number, row in enumerate(rows, start=1):
            if expected_columns is None:
                expected_columns = len(row)
                if expected_columns == 0:
                    fail(f"{path}:{line_number}: empty header")
            elif len(row) != expected_columns:
                fail(
                    f"{path}:{line_number}: expected {expected_columns} columns, "
                    f"found {len(row)}"
                )
        if expected_columns is None:
            fail(f"{path}: empty CSV file")


def validation_failure(
    root: Path, path: Path, validator: Callable[[Path], None]
) -> str | None:
    """Return a stable diagnostic for one file, or None when it is valid."""
    try:
        validator(path)
    except (
        csv.Error,
        json.JSONDecodeError,
        OSError,
        UnicodeError,
        ValueError,
    ) as error:
        return f"{path.relative_to(root)}: {error}"
    return None


def main() -> int:
    """Validate all repository-owned JSON and CSV files."""
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []
    validated = 0

    for suffix, validator in ((".json", validate_json), (".csv", validate_csv)):
        for path in sorted(owned_files(root, suffix)):
            failure = validation_failure(root, path, validator)
            if failure is None:
                validated += 1
            else:
                failures.append(failure)

    if failures:
        for failure in failures:
            sys.stderr.write(f"error: {failure}\n")
        return 1

    sys.stdout.write(f"validated {validated} repository JSON/CSV files\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
