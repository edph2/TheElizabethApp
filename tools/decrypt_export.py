#!/usr/bin/env python3
"""Decrypts an Elizabeth App export (.elizbak) into a plain ZIP file, without the app.

Usage: python3 decrypt_export.py elizabeth-export.elizbak output.zip
Needs: pip install cryptography

Run this on a trusted computer: the output contains her messages and voice recordings.
Format (see docs/DATA_EXPORT.md):
  "ELIZBAK1" | iterations (uint32 big-endian) | salt (16) | IV (12) | AES-256-GCM ciphertext + tag
  key = PBKDF2-HMAC-SHA256(passphrase UTF-8, salt, iterations, 32 bytes); AAD = "ELIZBAK1"
"""
import getpass
import hashlib
import struct
import sys

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"ELIZBAK1"


def decrypt(data: bytes, passphrase: str) -> bytes:
    if not data.startswith(MAGIC):
        raise ValueError("Not an Elizabeth App export file")
    (iterations,) = struct.unpack(">I", data[8:12])
    salt, iv, sealed = data[12:28], data[28:40], data[40:]
    key = hashlib.pbkdf2_hmac("sha256", passphrase.encode("utf-8"), salt, iterations, 32)
    return AESGCM(key).decrypt(iv, sealed, MAGIC)


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    with open(sys.argv[1], "rb") as f:
        data = f.read()
    passphrase = getpass.getpass("Passphrase: ")
    try:
        plain = decrypt(data, passphrase)
    except InvalidTag:
        print("Wrong passphrase, or the file is damaged.", file=sys.stderr)
        return 1
    with open(sys.argv[2], "wb") as f:
        f.write(plain)
    print(f"Wrote {sys.argv[2]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
