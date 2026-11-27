#!/usr/bin/env python3
"""Check the native libraries and embedded bootstrap in a rustermux APK."""

import argparse
from hashlib import sha256
import os
from pathlib import Path, PurePosixPath
import re
import struct
import subprocess
from zipfile import BadZipFile, ZipFile


ABI = "arm64-v8a"
PACKAGE = "com.termux"
MIN_SDK = 34
TARGET_SDK = 28
PUBLIC_TEST_CERTIFICATE = "b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1"
APK_SIGNING_MAGIC = b"APK Sig Block 42"
BOOTSTRAP_PIN = Path(__file__).resolve().parents[1] / "app/bootstrap.sha256"
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


def verify_manifest(apk_path, build_tools):
    badging = subprocess.check_output(
        [build_tools / "aapt2", "dump", "badging", apk_path], text=True
    ).splitlines()
    package = re.fullmatch(r"package: name='([^']+)'.*", badging[0]) if badging else None
    if package is None or package.group(1) != PACKAGE:
        raise ValueError(f"APK package must be {PACKAGE}")
    if f"minSdkVersion:'{MIN_SDK}'" not in badging or f"targetSdkVersion:'{TARGET_SDK}'" not in badging:
        raise ValueError(f"APK must declare minimum SDK {MIN_SDK} and target SDK {TARGET_SDK}")
    if [line.strip() for line in badging if line.startswith("native-code:")] != [
        f"native-code: '{ABI}'"
    ]:
        raise ValueError(f"APK must declare only the {ABI} ABI")
    if "application-debuggable" in badging:
        raise ValueError("APK is debuggable")
    print(f"Verified {PACKAGE}, minimum SDK {MIN_SDK}, target SDK {TARGET_SDK}, and a non-debuggable manifest")


def verify_signature(apk, apk_path, build_tools):
    has_jar_signature = any(
        entry.startswith("META-INF/") and entry.upper().endswith((".RSA", ".DSA", ".EC", ".SF"))
        for entry in apk.namelist()
    )
    apk.fp.seek(apk.start_dir - len(APK_SIGNING_MAGIC))
    has_apk_signature = apk.fp.read(len(APK_SIGNING_MAGIC)) == APK_SIGNING_MAGIC
    if not (has_jar_signature or has_apk_signature):
        print("Unsigned Release APK")
        return
    signers = subprocess.check_output(
        [build_tools / "apksigner", "verify", "--print-certs", apk_path], text=True
    )
    if "CN=Android Debug" in signers or PUBLIC_TEST_CERTIFICATE in signers.lower():
        raise ValueError("APK is signed with an unsupported debug or public test certificate")
    print("Verified Release APK signature")


def verify_apk(apk_path, bootstrap_path, build_tools):
    verify_manifest(apk_path, build_tools)
    bootstrap = bootstrap_path.read_bytes()
    if sha256(bootstrap).hexdigest() != BOOTSTRAP_PIN.read_text().strip():
        raise ValueError("The bootstrap archive does not match the pinned SHA-256")
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
            if entry.is_dir() or not parts:
                continue
            if parts[0] != "lib":
                with apk.open(entry) as file:
                    if file.read(4) == b"\x7fELF":
                        raise ValueError(f"Unexpected ELF file outside lib/: {entry.filename}")
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
        verify_signature(apk, apk_path, build_tools)
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
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    parser.add_argument(
        "--build-tools", type=Path, default=Path(sdk) / "build-tools/35.0.0" if sdk else None
    )
    args = parser.parse_args()
    if args.build_tools is None:
        parser.error("set ANDROID_HOME or pass --build-tools")
    try:
        verify_apk(args.apk, args.bootstrap, args.build_tools)
    except (BadZipFile, OSError, ValueError, KeyError, struct.error, subprocess.CalledProcessError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
