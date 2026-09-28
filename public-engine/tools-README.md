# Packaging voices

`package_voice.py` makes an encrypted `.elizvoice` package that the Piper Voice Engine can import.

```
pip install -r requirements.txt

# A published open voice, from a sherpa-onnx "vits-piper-..." release folder:
python3 package_voice.py --sherpa-dir vits-piper-en_GB-alba-medium \
    --licence "see MODEL_CARD" --name "Alba (Scottish English)" --out alba.elizvoice

# A trained Piper voice (with the speaker's consent, from the training dataset's manifest):
python3 package_voice.py --model voice.onnx --config voice.onnx.json --dataset DATASET_DIR \
    --espeak-data espeak-ng-data --base-model "en_GB-..." --name "..." --out voice.elizvoice
```

`elizbak.py` implements the ELIZBAK2 encryption (see [VOICE_FILES.md](../../docs/VOICE_FILES.md)).
