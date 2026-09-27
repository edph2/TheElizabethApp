#!/usr/bin/env python3
"""Checks a voice banking dataset exported from The Elizabeth App before training.

Usage: python3 verify_dataset.py DATASET_DIR

DATASET_DIR is the unzipped training export (wavs/, metadata.csv, manifest.json).
Checks: consent is present; every WAV listed in the manifest exists and matches its
SHA-256; every WAV is 16-bit mono PCM at the manifest's sample rate; metadata.csv and the
manifest agree. Prints the total amount of speech. Uses only the Python standard library.
"""
import hashlib
import json
import os
import sys
import wave


def verify(dataset: str) -> list:
    problems = []
    with open(os.path.join(dataset, "manifest.json"), encoding="utf-8") as f:
        manifest = json.load(f)
    consent = manifest.get("consent") or {}
    if not consent.get("statement") or not consent.get("speakerName"):
        problems.append("No consent record: do not train on these recordings.")
    rate = manifest.get("sampleRate", 22050)

    listed = {}
    with open(os.path.join(dataset, "metadata.csv"), encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            parts = line.rstrip("\n").split("|")
            if len(parts) != 3 or not parts[1].strip():
                problems.append(f"metadata.csv line {n} is malformed")
                continue
            listed[f"wavs/{parts[0]}.wav"] = parts[1]

    files = manifest.get("files", {})
    if set(files) != set(listed):
        problems.append("metadata.csv and manifest.json list different recordings")

    total_frames = 0
    for name, expected in files.items():
        path = os.path.join(dataset, name)
        if not os.path.exists(path):
            problems.append(f"{name} is missing")
            continue
        with open(path, "rb") as f:
            digest = hashlib.sha256(f.read()).hexdigest()
        if digest != expected:
            problems.append(f"{name} does not match its checksum")
        with wave.open(path, "rb") as w:
            if w.getnchannels() != 1 or w.getsampwidth() != 2 or w.getframerate() != rate:
                problems.append(f"{name} is not 16-bit mono at {rate} Hz")
            total_frames += w.getnframes()

    minutes = total_frames / rate / 60
    print(f"Speaker: {manifest.get('speakerName')}  Recordings: {len(files)}  Speech: {minutes:.1f} minutes")
    if minutes < 20:
        print("Note: under 20 minutes of speech. Fine-tuning will work, but the voice will improve with more recordings.")
    return problems


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    problems = verify(sys.argv[1])
    for p in problems:
        print("PROBLEM:", p)
    print("OK: the dataset is ready for training." if not problems else "Fix the problems above before training.")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
