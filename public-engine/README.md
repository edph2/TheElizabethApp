# Piper Voice Engine

An offline text-to-speech engine for Android that speaks with [Piper](https://github.com/rhasspy/piper)
voices, including a voice made from a person's own recordings (voice banking). It was made for
The Elizabeth App, a communication aid for people losing their speech, in memory of Elizabeth.

- **A standard Android speech engine.** Any app can use it through Android's `TextToSpeech` API,
  and it appears in Android's text-to-speech settings.
- **Entirely on the device.** The app has no internet permission, so Android does not let it
  open network connections. CI checks this on every build.
- **Voices with consent.** A voice is only installed if it carries a consent record (a person's
  own or donated voice) or a licence (a published voice), and its checksums match.
- **Voices are imported from encrypted `.elizvoice` files** (see [docs/VOICE_FILES.md](docs/VOICE_FILES.md)).
  `tools/voice-training/package_voice.py` makes one from a trained Piper model, or from a
  published sherpa-onnx `vits-piper-…` voice.

This repository is the complete source of the engine app, published under the GNU GPL.

## Licence

| Folder | Licence |
|---|---|
| `engine/` (the app) | GNU GPL version 3 or later: [LICENSE](LICENSE) |
| `voiceformat/` (the shared file format) | Apache-2.0: [voiceformat/LICENSE](voiceformat/LICENSE) |
| `tools/` | Apache-2.0 |

Third-party components in the app:

| Component | Licence |
|---|---|
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 1.13.8 (downloaded at build time, SHA-256 checked) | Apache-2.0 |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime), inside sherpa-onnx | MIT |
| [espeak-ng](https://github.com/espeak-ng/espeak-ng), inside sherpa-onnx | GPL-3.0-or-later |
| Kotlin, kotlinx.serialization, kotlinx.coroutines, AndroidX, Jetpack Compose | Apache-2.0 |

Voice models are not part of this repository. Each voice has its own licence or consent record,
which the engine shows.

## Building

Needs JDK 17. The Android app needs the Android SDK (`ANDROID_HOME`, or `sdk.dir` in
`local.properties`).

```
./gradlew :voiceformat:test                          # file-format tests; no Android SDK needed
./gradlew :engine:assembleDebug :engine:lintDebug    # the app
./gradlew :engine:connectedDebugAndroidTest          # on a device or emulator
```

Every dependency is checked against the SHA-256 in `gradle/verification-metadata.xml`.

The on-device speech tests need a packaged voice in `engine/src/androidTest/assets/test.elizvoice`
with the passphrase `correct horse battery`; the CI workflow shows how to make one from a
published voice. Without it they are skipped.

Release builds are signed with the key given in the environment variables
`ELIZABETH_KEYSTORE`, `ELIZABETH_KEYSTORE_PASSWORD`, `ELIZABETH_KEY_ALIAS` and
`ELIZABETH_KEY_PASSWORD`. Apps that manage the engine's voices (the voice-management provider)
must be signed with the same key as the engine.

## Using it from another app

See [docs/SPEECH_ENGINE_API.md](docs/SPEECH_ENGINE_API.md): speaking through Android's
`TextToSpeech` API (package `uk.elizabeth.speech`), and the signature-protected provider for
listing, backing up, installing and removing voices.
