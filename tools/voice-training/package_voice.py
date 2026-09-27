#!/usr/bin/env python3
"""Packages a Piper voice for The Elizabeth App (".elizvoice"), encrypted with a passphrase.

Her own (or a donor's) trained voice:
  python3 package_voice.py --model her-voice.onnx --config her-voice.onnx.json \\
      --dataset DATASET_DIR --espeak-data espeak-ng-data --base-model "en_GB-..." \\
      --name "Elizabeth's voice" --out her-voice.elizvoice

A published open voice, from a sherpa-onnx "vits-piper-..." folder (a good voice to use
until hers is ready):
  python3 package_voice.py --sherpa-dir vits-piper-en_GB-alba-medium \\
      --licence "see MODEL_CARD" --name "Alba (Scottish English)" --out alba.elizvoice

The package (an ELIZBAK2-encrypted ZIP) contains:
  voice/model.onnx         the model, with the metadata the sherpa-onnx engine needs
  voice/tokens.txt         the phoneme table
  voice/espeak-ng-data/    pronunciation data (from espeak-ng / sherpa-onnx releases)
  voice/manifest.json      whose voice it is, their consent, licence, base model and
                           SHA-256 checksums of the model, tokens and training data
Needs: pip install cryptography onnx
"""
import argparse
import getpass
import hashlib
import io
import json
import os
import shutil
import sys
import tempfile
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


def sherpa_metadata(config: dict) -> dict:
    """The metadata sherpa-onnx reads from a Piper model (the same keys as its own Piper voices)."""
    return {
        "model_type": "vits",
        "comment": "piper",
        "language": config.get("language", {}).get("name_english", "English"),
        "voice": config["espeak"]["voice"],
        "has_espeak": 1,
        "n_speakers": config.get("num_speakers", 1),
        "sample_rate": config["audio"]["sample_rate"],
    }


def write_tokens(config: dict, path: str) -> None:
    with open(path, "w", encoding="utf-8") as f:
        for symbol, ids in config["phoneme_id_map"].items():
            f.write(f"{symbol} {ids[0]}\n")


def add_metadata(src: str, dst: str, meta: dict) -> None:
    import onnx  # only needed for newly trained models

    model = onnx.load(src)
    while len(model.metadata_props):
        model.metadata_props.pop()
    for key, value in meta.items():
        prop = model.metadata_props.add()
        prop.key, prop.value = key, str(value)
    onnx.save(model, dst)


def prepare_trained(args, work: str):
    with open(args.config, encoding="utf-8") as f:
        config = json.load(f)
    model, tokens = os.path.join(work, "model.onnx"), os.path.join(work, "tokens.txt")
    add_metadata(args.model, model, sherpa_metadata(config))
    write_tokens(config, tokens)
    with open(os.path.join(args.dataset, "manifest.json"), encoding="utf-8") as f:
        data_manifest = json.load(f)
    consent = data_manifest.get("consent")
    if not consent:
        raise SystemExit("The training data has no consent record. Refusing to package this voice.")
    manifest = {
        "kind": "own" if consent.get("isSelf") else "donor",
        "name": args.name or f"{consent['speakerName']}'s voice",
        "speakerName": consent["speakerName"],
        "consent": consent,
        "baseModel": args.base_model,
        "sampleRate": config["audio"]["sample_rate"],
        "trainingDataManifestSha256": sha256_file(os.path.join(args.dataset, "manifest.json")),
        "trainingUtterances": data_manifest.get("utterances"),
        "trainingSeconds": data_manifest.get("totalSeconds"),
        "syntheticVoiceNotice": "This is an AI-generated voice made, with consent, from recordings of the named speaker.",
    }
    return model, tokens, args.espeak_data, manifest


def prepare_stock(args):
    d = args.sherpa_dir
    models = [n for n in os.listdir(d) if n.endswith(".onnx")]
    if len(models) != 1:
        raise SystemExit(f"Expected one .onnx model in {d}")
    config_path = os.path.join(d, models[0] + ".json")
    rate = 22050
    if os.path.exists(config_path):
        with open(config_path, encoding="utf-8") as f:
            rate = json.load(f).get("audio", {}).get("sample_rate", rate)
    if not args.licence:
        raise SystemExit("--licence is required for a published voice (see its MODEL_CARD)")
    manifest = {
        "kind": "stock",
        "name": args.name or models[0][:-5],
        "licence": args.licence,
        "baseModel": models[0][:-5],
        "sampleRate": rate,
        "syntheticVoiceNotice": "This is an AI-generated voice.",
    }
    return os.path.join(d, models[0]), os.path.join(d, "tokens.txt"), os.path.join(d, "espeak-ng-data"), manifest


def build_package(model: str, tokens: str, espeak: str, manifest: dict) -> bytes:
    if not espeak or not os.path.isdir(espeak):
        raise SystemExit("espeak-ng-data folder not found (download espeak-ng-data.tar.bz2 from the sherpa-onnx tts-models release)")
    manifest = dict(manifest)
    manifest.update({
        "format": "elizabeth-voice-1",
        "engine": "piper-vits",
        "createdAtMillis": int(time.time() * 1000),
        "modelSha256": sha256_file(model),
        "tokensSha256": sha256_file(tokens),
    })
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("voice/manifest.json", json.dumps(manifest, indent=2))
        z.write(model, "voice/model.onnx")
        z.write(tokens, "voice/tokens.txt")
        for root, _, files in os.walk(espeak):
            for name in sorted(files):
                path = os.path.join(root, name)
                z.write(path, "voice/espeak-ng-data/" + os.path.relpath(path, espeak).replace(os.sep, "/"))
    return buffer.getvalue()


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--model", help="trained Piper .onnx")
    p.add_argument("--config", help="its .onnx.json")
    p.add_argument("--dataset", help="the unzipped training export (for its manifest and consent)")
    p.add_argument("--espeak-data", help="espeak-ng-data folder")
    p.add_argument("--base-model", help="name of the Piper checkpoint that was fine-tuned")
    p.add_argument("--sherpa-dir", help="a sherpa-onnx vits-piper-* folder (published voice)")
    p.add_argument("--licence", help="licence of a published voice")
    p.add_argument("--name", help="display name")
    p.add_argument("--out", required=True)
    args = p.parse_args()

    with tempfile.TemporaryDirectory() as work:
        if args.sherpa_dir:
            parts = prepare_stock(args)
        else:
            missing = [a for a in ("model", "config", "dataset", "espeak_data", "base_model") if not getattr(args, a)]
            if missing:
                raise SystemExit("Missing: " + ", ".join("--" + m.replace("_", "-") for m in missing))
            parts = prepare_trained(args, work)
        package = build_package(*parts)

    passphrase = getpass.getpass("Passphrase for the voice file (at least 8 characters): ")
    if passphrase != getpass.getpass("Type it again: "):
        raise SystemExit("The passphrases do not match")
    with open(args.out, "wb") as out:
        encrypt_stream(io.BytesIO(package), out, passphrase)
    print(f"Wrote {args.out}. Copy it to the tablet and import it in Settings → Voice.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
