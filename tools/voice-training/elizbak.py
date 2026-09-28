# SPDX-License-Identifier: Apache-2.0
"""The Elizabeth App's encrypted file format (ELIZBAK2), for the training tools.

See docs/VOICE_FILES.md. Kept identical to voiceformat/.../Backup.kt.
"""
import hashlib
import os
import struct

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"ELIZBAK2"
ITERATIONS = 310_000
CHUNK = 64 * 1024


class ExportError(Exception):
    pass


def _aes(passphrase: str, salt: bytes, iterations: int) -> AESGCM:
    return AESGCM(hashlib.pbkdf2_hmac("sha256", passphrase.encode("utf-8"), salt, iterations, 32))


def encrypt_stream(src, dst, passphrase: str) -> None:
    """Encrypts everything read from src (a file object) into dst."""
    if len(passphrase) < 8:
        raise ExportError("The passphrase must be at least 8 characters")
    salt, prefix = os.urandom(16), os.urandom(8)
    aes = _aes(passphrase, salt, ITERATIONS)
    dst.write(MAGIC + struct.pack(">I", ITERATIONS) + salt + prefix)
    index = 0
    chunk = src.read(CHUNK)
    while True:
        following = src.read(CHUNK) if len(chunk) == CHUNK else b""
        final = not following
        aad = MAGIC + struct.pack(">I", index) + bytes([1 if final else 0])
        sealed = aes.encrypt(prefix + struct.pack(">I", index), chunk, aad)
        dst.write(bytes([1 if final else 0]) + struct.pack(">I", len(sealed)) + sealed)
        if final:
            return
        chunk = following
        index += 1


def decrypt_stream(src, dst, passphrase: str) -> None:
    """Decrypts an ELIZBAK2 stream from src into dst, checking every chunk and the end."""
    if src.read(8) != MAGIC:
        raise ExportError("Not an Elizabeth App file")
    (iterations,) = struct.unpack(">I", src.read(4))
    salt, prefix = src.read(16), src.read(8)
    aes = _aes(passphrase, salt, iterations)
    index = 0
    while True:
        header = src.read(5)
        if len(header) < 5:
            raise ExportError("The file is incomplete")
        flag, length = header[0], struct.unpack(">I", header[1:])[0]
        sealed = src.read(length)
        if flag > 1 or len(sealed) != length:
            raise ExportError("The file is damaged or incomplete")
        aad = MAGIC + struct.pack(">I", index) + bytes([flag])
        try:
            dst.write(aes.decrypt(prefix + struct.pack(">I", index), sealed, aad))
        except InvalidTag:
            raise ExportError("Wrong passphrase, or the file is damaged")
        index += 1
        if flag == 1:
            if src.read(1):
                raise ExportError("Unexpected data after the end of the file")
            return
