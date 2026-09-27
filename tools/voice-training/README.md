# Making a voice model from her recordings

This turns the voice banking recordings into a synthetic voice that sounds like her. The
voice then runs on the tablet, offline. Training needs a graphics card (GPU), so it happens
on a separate computer. That computer should be one the family owns and trusts.

**Status:** the checking and packaging scripts in this folder are tested. The Piper
training commands below follow Piper's own training documentation, but have not yet been
run on real recordings in this project. Expect to adjust batch size and epochs.

## Privacy while training

The recordings are her voice, which is personal data. While they are on the training
computer:

- Use a computer with full-disk encryption, and do not sync the working folder to a cloud
  service (iCloud, OneDrive, Google Drive, Dropbox).
- Training does not need the internet. The software and base model can be downloaded
  first, and the network then switched off.
- When you are finished, delete the decrypted recordings and the working folder. Keep only
  the encrypted `.elizbak` export and the encrypted `.elizvoice` result.

## What you need

- Linux (Ubuntu 22.04 or later) with an NVIDIA GPU with 8 GB or more of memory. A CPU
  alone works, but takes days rather than hours.
- Python 3.10 or later, and `pip install cryptography`.
- [Piper](https://github.com/rhasspy/piper) training code. The original repository is MIT
  licensed and archived; development continues at
  [OHF-Voice/piper1-gpl](https://github.com/OHF-Voice/piper1-gpl), which is GPL-3.0.
  Follow the training setup in whichever you use.
- A British English Piper **checkpoint** (`.ckpt`) to fine-tune from, of the same quality
  level you want (`medium` is a good balance for tablets). Fine-tuning from an existing
  voice needs far less of her speech than training from scratch. **Check the checkpoint's
  model card**: the licence of the dataset it was trained on must allow your use. Prefer a
  voice with a similar accent, and the same sex, as her.

## Steps

1. **Export** the recordings on the tablet: Settings → Voice banking → *Export recordings
   for voice training*. Copy the file to the training computer by USB.

2. **Decrypt and unzip:**
   ```
   python3 tools/decrypt_export.py voice-training.elizbak voice-training.zip
   unzip voice-training.zip -d dataset
   ```

3. **Check the recordings.** This confirms the consent record is present, every file
   matches its checksum and is in the right format, and reports how much speech there is.
   ```
   python3 tools/voice-training/verify_dataset.py dataset
   ```

4. **Preprocess** (Piper):
   ```
   python3 -m piper_train.preprocess --language en-gb --input-dir dataset \
       --output-dir work --dataset-format ljspeech --single-speaker --sample-rate 22050
   ```

5. **Fine-tune.** `--max_epochs` counts from the checkpoint's own epoch, so set it to
   roughly 1,000 more than the checkpoint has already done. Stop earlier if it already
   sounds right.
   ```
   python3 -m piper_train --dataset-dir work --accelerator gpu --devices 1 \
       --batch-size 16 --validation-split 0.0 --num-test-examples 0 \
       --max_epochs <checkpoint epoch + 1000> --resume_from_checkpoint base.ckpt \
       --checkpoint-epochs 1 --precision 32 --quality medium
   ```

6. **Export to ONNX** and copy the config next to it:
   ```
   python3 -m piper_train.export_onnx work/lightning_logs/version_0/checkpoints/<last>.ckpt her-voice.onnx
   cp work/config.json her-voice.onnx.json
   ```

7. **Listen.** Try sentences she did *not* record, and let her and the family judge it:
   ```
   echo "Hello, it's lovely to see you. Would you like a cup of tea?" | \
       piper -m her-voice.onnx --output_file test.wav
   ```

8. **Package** it for the tablet. You will need the pronunciation data `espeak-ng-data`,
   from `espeak-ng-data.tar.bz2` in the sherpa-onnx
   [tts-models release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models).
   Packaging adds the metadata the tablet's speech engine needs, and creates an encrypted
   file. Inside it, a manifest records whose voice it is, her consent, the base model, and
   checksums of the model and the training data.
   ```
   pip install -r tools/voice-training/requirements.txt
   python3 tools/voice-training/package_voice.py --model her-voice.onnx \
       --config her-voice.onnx.json --dataset dataset --espeak-data espeak-ng-data \
       --base-model "<name of the checkpoint>" --name "Elizabeth's voice" --out her-voice.elizvoice
   ```

9. **Import** `her-voice.elizvoice` on the tablet: Settings → Voice → *Import a voice*.

10. **Delete** `dataset/`, `work/`, `voice-training.zip`, `her-voice.onnx` and `test.wav`
    from the training computer.

## A good voice to use in the meantime

Until her own voice is ready, a published British voice sounds far more natural than most
built-in tablet voices, and runs the same way, on the tablet. sherpa-onnx publishes Piper
voices ready to use. They are in the same
[tts-models release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models), for
example `vits-piper-en_GB-alba-medium.tar.bz2` or
`vits-piper-en_GB-southern_english_female-medium.tar.bz2`. Read each voice's `MODEL_CARD`
for its licence and the dataset it was trained on, then package it:

```
tar xjf vits-piper-en_GB-alba-medium.tar.bz2
python3 tools/voice-training/package_voice.py --sherpa-dir vits-piper-en_GB-alba-medium \
    --licence "<licence from MODEL_CARD>" --name "Alba (Scottish English)" --out alba.elizvoice
```

## If her voice has already changed

- Use older recordings of her as well (videos, voicemails, speeches). Cut them into
  sentences and transcribe them, cleaning up background noise first. Add them to
  `dataset/wavs` and `metadata.csv`, and update the manifest.
- *Zero-shot* voice cloning models can imitate a voice from about a minute of audio. Some
  of their model licences are non-commercial only. They are too heavy to run on a tablet,
  but on the training computer they can generate extra sentences in her voice to
  fine-tune Piper with. Check the licence first.

## AI Act note

The packaged manifest states that the voice is AI-generated and was made with the
speaker's consent. The app speaks live through the speaker and never saves synthesised
audio to files, which limits how a cloned voice could be misused.
