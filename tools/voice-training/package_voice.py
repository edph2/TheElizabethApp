#!/usr/bin/env python3
"""Packages a trained Piper voice for The Elizabeth App, encrypted with a passphrase.

Usage:
  python3 package_voice.py --model her-voice.onnx --config her-voice.onnx.json \
      --dataset DATASET_DIR --base-model "en_GB-example-medium" --out her-voice.elizvoice

The output is an ELIZBAK2-encrypted ZIP containing:
  voice/model.onnx        the Piper (VITS) model
  voice/model.onnx.json   its Piper config
  voice/manifest.json     whose voice it is, their consent, the base model it was fine-tuned
                          from, and SHA-256 checksums of the model, config and training data

The app checks the checksums before using the voice, and shows whose voice it is.
Needs: pip install cryptography
"""
import argparse
import getpass
import hashlib
import io
import json
import os
import sys
import time
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from elizbak import encrypt_stream  # noqa: E402


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def build_manifest(args) -> dict:
    with open(os.path.join(args.dataset, "manifest.json"), encoding="utf-8") as f:
        data_manifest = json.load(f)
    consent = data_manifest.get("consent")
    if not consent:
        raise SystemExit("The training data has no consent record. Refusing to package this voice.")
    with open(args.config, encoding="utf-8") as f:
        config = json.load(f)
    return {
        "format": "elizabeth-voice-1",
        "engine": "piper-vits",
        "name": args.name or f"{consent['speakerName']}'s voice",
        "speakerName": consent["speakerName"],
        "consent": consent,
        "baseModel": args.base_model,
        "sampleRate": config.get("audio", {}).get("sample_rate", 22050),
        "createdAtMillis": int(time.time() * 1000),
        "modelSha256": sha256_file(args.model),
        "configSha256": sha256_file(args.config),
        "trainingDataManifestSha256": sha256_file(os.path.join(args.dataset, "manifest.json")),
        "trainingUtterances": data_manifest.get("utterances"),
        "trainingSeconds": data_manifest.get("totalSeconds"),
        "syntheticVoiceNotice": "This is an AI-generated voice made, with consent, from recordings of the named speaker.",
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--model", required=True)
    p.add_argument("--config", required=True)
    p.add_argument("--dataset", required=True, help="the unzipped training export (for its manifest and consent)")
    p.add_argument("--base-model", required=True, help="name of the Piper checkpoint that was fine-tuned")
    p.add_argument("--name", help="display name, e.g. \"Elizabeth's voice\"")
    p.add_argument("--out", required=True)
    args = p.parse_args()

    manifest = build_manifest(args)
    passphrase = getpass.getpass("Passphrase for the voice file (at least 8 characters): ")
    if passphrase != getpass.getpass("Type it again: "):
        raise SystemExit("The passphrases do not match")

    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(args.model, "voice/model.onnx")
        z.write(args.config, "voice/model.onnx.json")
        z.writestr("voice/manifest.json", json.dumps(manifest, indent=2))
    buffer.seek(0)
    with open(args.out, "wb") as out:
        encrypt_stream(buffer, out, passphrase)
    print(f"Wrote {args.out}. Copy it to the tablet and import it in Settings → Voice.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
