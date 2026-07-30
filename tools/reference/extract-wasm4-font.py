#!/usr/bin/env python3
# Copyright 2026 W4ME Station contributors
# SPDX-License-Identifier: MIT
#
"""Extract the 224-glyph bitmap font from the official framebuffer.c."""

import pathlib
import re
import sys

ARGUMENT_COUNT = 3
FONT_SIZE = 1792


def main() -> int:
    """Extract the canonical font into the requested binary output file."""
    if len(sys.argv) != ARGUMENT_COUNT:
        sys.stderr.write(
            f"usage: {pathlib.Path(sys.argv[0]).name} FRAMEBUFFER_C OUTPUT\n"
        )
        return 2

    source_path = pathlib.Path(sys.argv[1])
    output_path = pathlib.Path(sys.argv[2])
    source = source_path.read_text(encoding="utf-8")
    match = re.search(
        r"static const uint8_t font\[1792\]\s*=\s*\{(.*?)\};",
        source,
        flags=re.DOTALL,
    )
    if match is None:
        msg = "official font array not found"
        raise SystemExit(msg)

    values = bytes(
        int(value, 16) for value in re.findall(r"0x([0-9a-fA-F]{2})", match.group(1))
    )
    if len(values) != FONT_SIZE:
        msg = f"expected {FONT_SIZE} font bytes, found {len(values)}"
        raise SystemExit(msg)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(values)
    sys.stdout.write(f"wrote {len(values)} bytes to {output_path}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
