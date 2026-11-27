#!/usr/bin/env python3
"""Check the native libraries and embedded bootstrap in a rustermux APK."""

import argparse
from pathlib import Path, PurePosixPath
import struct
from zipfile import BadZipFile, ZipFile


ABI = "arm64-v8a"
EM_AARCH64 = 183
PT_LOAD = 1
MIN_LOAD_ALIGNMENT = 16384
EXPECTED_LIBRARIES = {
    "liblocal-socket.so",
    "libtermux.so",
    "libtermux-api-client.so",
    "libtermux-bootstrap.so",
}


def verify_aarch64(name, binary):
    if binary[:6] != b"\x7fELF\x02\x01":
        raise ValueError(f"{name} is not a 64-bit little-endian ELF")
    if struct.unpack_from("<H", binary, 18)[0] != EM_AARCH64:
        raise ValueError(f"{name} does not target AArch64")



def verify_elf(name, binary):
    verify_aarch64(name, binary)
    table_offset = struct.unpack_from("<Q", binary, 32)[0]
    entry_size, entry_count = struct.unpack_from("<HH", binary, 54)
    if entry_size != 56 or table_offset + entry_size * entry_count > len(binary):
        raise ValueError(f"{name} has an invalid ELF program header table")

    load_count = 0
    for offset in range(table_offset, table_offset + entry_size * entry_count, entry_size):
        segment_type, _, file_offset, virtual_address, _, _, _, alignment = (
            struct.unpack_from("<II6Q", binary, offset)
        )
        if segment_type == PT_LOAD:
            load_count += 1
            if alignment < MIN_LOAD_ALIGNMENT or (
                virtual_address - file_offset
            ) % MIN_LOAD_ALIGNMENT:
                raise ValueError(f"{name} has a load segment incompatible with 16 KiB pages")
    if load_count == 0:
        raise ValueError(f"{name} has no ELF load segments")


def verify_apk(apk_path, bootstrap_path):
    bootstrap = bootstrap_path.read_bytes()
    bootstrap_elfs = 0
    with ZipFile(bootstrap_path) as archive:
        archive.getinfo("bin/bash")
        for entry in archive.infolist():
            with archive.open(entry) as file:
                header = file.read(4)
                if header == b"\x7fELF" or entry.filename == "bin/bash":
                    verify_elf(f"bootstrap {entry.filename}", header + file.read())
                    bootstrap_elfs += 1

    with ZipFile(apk_path) as apk:
        libraries = set()
        for entry in apk.infolist():
            parts = PurePosixPath(entry.filename).parts
            if entry.is_dir() or not parts or parts[0] != "lib":
                continue
            if len(parts) != 3 or parts[1] != ABI or parts[2] in libraries:
                raise ValueError(f"Unexpected APK native entry {entry.filename}")
            library = parts[2]
            libraries.add(library)
            binary = apk.read(entry)
            verify_elf(entry.filename, binary)
            if library == "libtermux-bootstrap.so" and binary.count(bootstrap) != 1:
                raise ValueError("The bootstrap library must contain one copy of the AArch64 archive")

        missing = EXPECTED_LIBRARIES - libraries
        if missing:
            raise ValueError(f"Missing native libraries {', '.join(sorted(missing))}")
    print(
        f"Verified {len(libraries)} AArch64 APK libraries and "
        f"{bootstrap_elfs} AArch64 ELF files in its embedded bootstrap"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument(
        "--bootstrap", type=Path, default=Path("app/build/generated/bootstrap/bootstrap-aarch64.zip")
    )
    args = parser.parse_args()
    try:
        verify_apk(args.apk, args.bootstrap)
    except (BadZipFile, OSError, ValueError, KeyError, struct.error) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
