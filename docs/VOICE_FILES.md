# Voice files

## Voice packages (`.elizvoice`)

A voice package is a ZIP, encrypted as described below, containing:

| File | Contents |
|---|---|
| `voice/manifest.json` | Whose voice it is, their consent or the voice's licence, the locale, and SHA-256 checksums of the model and tokens |
| `voice/model.onnx` | A Piper (VITS) model with the metadata sherpa-onnx needs |
| `voice/tokens.txt` | The phoneme symbol table |
| `voice/espeak-ng-data/…` | Pronunciation data (from espeak-ng) used to turn text into phonemes |

The manifest is defined by [VoiceManifest.kt](../voiceformat/src/main/kotlin/uk/elizabeth/voiceformat/VoiceManifest.kt):
`format` is `elizabeth-voice-1`, `engine` is `piper-vits`, and `kind` is `own`, `donor` or `stock`.
An `own` or `donor` voice must carry a consent record; a `stock` (published) voice must carry
licence information. The engine refuses a voice otherwise, or if a checksum does not match.

The voice-management provider exchanges the same layout as a plain (unencrypted) ZIP, since it
only passes between apps on the same device.

## Encryption (ELIZBAK2)

The ZIP is encrypted as a stream of 64 KiB chunks:

```
header: "ELIZBAK2" | iterations (uint32 BE) | salt (16 bytes) | nonce prefix (8 bytes)
chunk:  final flag (1 byte: 0 or 1) | length (uint32 BE) | AES-256-GCM ciphertext + 16-byte tag
```

- **Key:** PBKDF2-HMAC-SHA256 of the UTF-8 passphrase and the salt, run for `iterations`
  rounds (currently 310,000), producing 32 bytes.
- **Nonce:** the nonce prefix followed by the chunk index (uint32 BE).
- **Associated data:** `"ELIZBAK2"`, then the chunk index, then the final flag.

Reordered, altered, removed or truncated chunks all fail to decrypt, and so does data after the
final chunk. Implementations: `voiceformat/…/Backup.kt` (Kotlin) and
`tools/voice-training/elizbak.py` (Python).
