# Third-party components

| Component | Used for | Licence | Where |
|---|---|---|---|
| Kotlin, kotlinx.serialization, kotlinx.coroutines | Language and libraries | Apache-2.0 | App |
| AndroidX, Jetpack Compose | User interface | Apache-2.0 | App |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 1.13.8 | On-device speech with installed voices | Apache-2.0 | **Piper Voice Engine app only** (native library) |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | Runs voice models | MIT | Piper Voice Engine app only (inside sherpa-onnx) |
| [espeak-ng](https://github.com/espeak-ng/espeak-ng) | Turns text into phonemes for Piper voices | GPL-3.0-or-later | Piper Voice Engine app only (inside sherpa-onnx); `espeak-ng-data` ships inside voice packages |
| [Piper](https://github.com/rhasspy/piper) / [piper1-gpl](https://github.com/OHF-Voice/piper1-gpl) | Training her voice model | MIT / GPL-3.0 | Training computer only, not in the app |
| Piper voice checkpoints and published voices | Base for fine-tuning; voices to use meanwhile | Varies by voice: see each `MODEL_CARD` | Not bundled; chosen by the family |

## How the GPL is handled

espeak-ng is GPL-3.0, so everything that includes it is kept in the separate **Piper Voice
Engine** app (`engine/`). That app is licensed GPL-3.0-or-later, and its source must be published
with it. The communication app contains none of it (CI checks every build), and uses the engine
only through Android's standard text-to-speech API and a documented voice-management provider.
See [LICENSE.md](LICENSE.md) and [docs/SPEECH_ENGINE_API.md](docs/SPEECH_ENGINE_API.md). Have this
reviewed by a solicitor before selling the app.
