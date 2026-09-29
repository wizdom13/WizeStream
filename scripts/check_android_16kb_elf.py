#!/usr/bin/env python3
"""Verify 64-bit ELF shared libraries in an APK support 16 KB pages."""

import io
import struct
import sys
import zipfile

MIN_ALIGN = 0x4000
PT_LOAD = 1


def load_alignments(data: bytes) -> list[int]:
    if data[:4] != b"\x7fELF" or data[4] != 2:
        return []
    endian = "<" if data[5] == 1 else ">"
    phoff = struct.unpack_from(endian + "Q", data, 32)[0]
    phentsize = struct.unpack_from(endian + "H", data, 54)[0]
    phnum = struct.unpack_from(endian + "H", data, 56)[0]
    alignments = []
    for index in range(phnum):
        offset = phoff + index * phentsize
        p_type = struct.unpack_from(endian + "I", data, offset)[0]
        if p_type == PT_LOAD:
            alignments.append(struct.unpack_from(endian + "Q", data, offset + 48)[0])
    return alignments


def main() -> int:
    apk = sys.argv[1]
    failures = []
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not name.endswith(".so") or not (
                name.startswith("lib/arm64-v8a/") or name.startswith("lib/x86_64/")
            ):
                continue
            alignments = load_alignments(archive.read(name))
            if not alignments or min(alignments) < MIN_ALIGN:
                failures.append((name, alignments))

    if failures:
        for name, alignments in failures:
            print(f"FAIL {name}: PT_LOAD alignments={alignments}", file=sys.stderr)
        return 1

    print("All packaged 64-bit native libraries are 16 KB page-size compatible.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
