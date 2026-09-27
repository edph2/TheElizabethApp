#!/usr/bin/env python3
"""Decrypts an Elizabeth App export (.elizbak) into a plain ZIP file, without the app.

Usage: python3 decrypt_export.py elizabeth-export.elizbak output.zip
Needs: pip install cryptography

Run this on a trusted computer: the output contains her messages and voice recordings.
The format is described in docs/DATA_EXPORT.md.
"""
import getpass
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "voice-training"))
from elizbak import ExportError, decrypt_stream  # noqa: E402


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
        os.remove(out_path)
        print(e, file=sys.stderr)
        return 1
    print(f"Wrote {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
