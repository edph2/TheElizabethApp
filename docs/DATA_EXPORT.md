# Export file formats

The app writes two kinds of encrypted `.elizbak` file, both only when a carer asks:

- **Export all data** (Settings → Privacy and data) is used to move to a new tablet, with
  **Restore from export**, and to give Elizabeth a copy of her data (the GDPR rights of
  access and portability).
- **Export recordings for voice training** (Settings → Voice banking) contains only the
  voice banking recordings, laid out for training a voice model (see
  [tools/voice-training](../tools/voice-training/README.md)).

## Encryption (both files)

A ZIP file is encrypted as a stream of 64 KiB chunks, so large exports never have to fit in
memory:

```
header: "ELIZBAK2" | iterations (uint32 BE) | salt (16 bytes) | nonce prefix (8 bytes)
chunk:  final flag (1 byte: 0 or 1) | length (uint32 BE) | AES-256-GCM ciphertext + 16-byte tag
```

- **Key:** PBKDF2-HMAC-SHA256 of the UTF-8 passphrase and the salt, run for `iterations`
  rounds (currently 310,000), producing 32 bytes.
- **Nonce:** the nonce prefix followed by the chunk index (uint32 BE).
- **Associated data:** `"ELIZBAK2"`, then the chunk index, then the final flag.

Because of this, reordered, altered, removed or truncated chunks all fail to decrypt, and
so does data after the final chunk. The passphrase must be at least 8 characters. Without
it the file cannot be opened.

When restoring, the app reads and checks the whole file before it changes anything on the
tablet.

To decrypt a file without the app, on a trusted computer:

```
pip install cryptography
python3 tools/decrypt_export.py elizabeth-export.elizbak elizabeth-export.zip
```

## Contents of "Export all data"

| File | Contents |
|---|---|
| `README.txt` | Short description of the files |
| `appdata.json` | Settings, phrases and categories, message history, voice banking progress and consent, and the privacy log (JSON) |
| `words.txt` | Words learned for prediction, one per line as `order<TAB>words…<TAB>count` |
| `recordings/<id>.wav` | Phrases recorded in her voice (message banking). `appdata.json` links each phrase to its recording id. |
| `voicebank/<id>.wav` | Voice banking recordings. `appdata.json` (`voiceBank.takes`) links each one to its sentence. |

All audio is 16-bit mono PCM WAV at 22.05 kHz.

## Contents of "Export recordings for voice training" (LJSpeech layout)

| File | Contents |
|---|---|
| `wavs/uttNNNNN.wav` | One recording per sentence |
| `metadata.csv` | `uttNNNNN|sentence|sentence`, one line per recording |
| `manifest.json` | Speaker name, the consent statement and when it was given, recording count and length, and a SHA-256 checksum of every WAV |
| `README.txt` | What to do next |
