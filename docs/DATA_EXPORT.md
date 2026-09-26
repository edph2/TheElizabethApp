# Export file format

**Export all data** (Settings → Privacy and data) writes one `.elizbak` file. It is used to
move to a new tablet (with **Restore from export**) and to give Elizabeth a copy of her
data (GDPR rights of access and portability).

## Encryption

```
"ELIZBAK1" (8 bytes) | iterations (uint32, big-endian) | salt (16 bytes) | IV (12 bytes) | ciphertext + 16-byte GCM tag
```

- Key: PBKDF2-HMAC-SHA256 of the UTF-8 passphrase and the salt, `iterations` rounds
  (currently 310,000), 32 bytes.
- Cipher: AES-256-GCM, with the 8-byte header string as associated data.
- The passphrase must be at least 8 characters. Without it the file cannot be opened.

To decrypt it without the app, on a trusted computer:

```
pip install cryptography
python3 tools/decrypt_export.py elizabeth-export.elizbak elizabeth-export.zip
```

## Contents (a ZIP of open formats)

| File | Contents |
|---|---|
| `README.txt` | Short description of the files |
| `appdata.json` | Settings, phrases and categories, message history, privacy log (JSON) |
| `words.txt` | Words learned for prediction: `order<TAB>words…<TAB>count` per line |
| `recordings/<id>.wav` | Her recorded phrases: 16-bit mono PCM WAV, 22.05 kHz. `appdata.json` links each phrase to its recording id. |

The recordings are also the starting point for training a voice model of her voice
(Phase 2 in [DESIGN.md](DESIGN.md)).
