#!/usr/bin/env python3
"""Decrypts an Elizabeth App export (.elizbak) into a plain ZIP file, without the app.

Usage: python3 decrypt_export.py elizabeth-export.elizbak output.zip
Needs: pip install cryptography

Run this on a trusted computer: the output contains her messages and voice recordings.
Format (see docs/DATA_EXPORT.md):
  header: "ELIZBAK2" | iterations (uint32 BE) | salt (16) | nonce prefix (8)
  chunks: final flag (1 byte) | length (uint32 BE) | AES-256-GCM ciphertext + 16-byte tag
  key = PBKDF2-HMAC-SHA256(passphrase UTF-8, salt, iterations, 32 bytes)
  nonce = prefix | chunk index (uint32 BE); associated data = "ELIZBAK2" | chunk index | flag
"""
import getpass
import hashlib
import struct
import sys

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"ELIZBAK2"


class ExportError(Exception):
    pass


def decrypt_stream(src, dst, passphrase: str) -> None:
    """Reads an export from the file object src and writes the ZIP to dst."""
    if src.read(8) != MAGIC:
        raise ExportError("Not an Elizabeth App export file (or an older format)")
    (iterations,) = struct.unpack(">I", src.read(4))
    salt, prefix = src.read(16), src.read(8)
    aes = AESGCM(hashlib.pbkdf2_hmac("sha256", passphrase.encode("utf-8"), salt, iterations, 32))
    index = 0
    while True:
        header = src.read(5)
        if len(header) < 5:
            raise ExportError("The export file is incomplete")
        flag, length = header[0], struct.unpack(">I", header[1:])[0]
        sealed = src.read(length)
        if flag > 1 or len(sealed) != length:
            raise ExportError("The export file is damaged or incomplete")
        nonce = prefix + struct.pack(">I", index)
        aad = MAGIC + struct.pack(">I", index) + bytes([flag])
        try:
            dst.write(aes.decrypt(nonce, sealed, aad))
        except InvalidTag:
            raise ExportError("Wrong passphrase, or the file is damaged")
        index += 1
        if flag == 1:
            if src.read(1):
                raise ExportError("Unexpected data after the end of the export")
            return


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    passphrase = getpass.getpass("Passphrase: ")
    out_path = sys.argv[2]
    try:
        with open(sys.argv[1], "rb") as src, open(out_path, "wb") as dst:
            decrypt_stream(src, dst, passphrase)
    except ExportError as e:
        import os
        os.remove(out_path)
        print(e, file=sys.stderr)
        return 1
    print(f"Wrote {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
