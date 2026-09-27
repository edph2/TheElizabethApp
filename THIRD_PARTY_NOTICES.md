# Third-party components

| Component | Used for | Licence | Where |
|---|---|---|---|
| Kotlin, kotlinx.serialization, kotlinx.coroutines | Language and libraries | Apache-2.0 | App |
| AndroidX, Jetpack Compose | User interface | Apache-2.0 | App |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 1.13.8 | On-device speech with installed voices | Apache-2.0 | App (native library) |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) | Runs voice models | MIT | Bundled inside sherpa-onnx |
| [espeak-ng](https://github.com/espeak-ng/espeak-ng) | Turns text into phonemes for Piper voices | GPL-3.0-or-later | Used by sherpa-onnx's Piper support; `espeak-ng-data` ships inside voice packages |
| [Piper](https://github.com/rhasspy/piper) / [piper1-gpl](https://github.com/OHF-Voice/piper1-gpl) | Training her voice model | MIT / GPL-3.0 | Training computer only, not in the app |
| Piper voice checkpoints and published voices | Base for fine-tuning; voices to use meanwhile | Varies by voice: see each `MODEL_CARD` | Not bundled; chosen by the family |

## Licence decision needed before sharing the app

Piper voices depend on espeak-ng, which is GPL-3.0. Distributing an APK that includes it
(or voice packages that include `espeak-ng-data`) to other people means following the
GPL. The simplest way to do that is to publish this app's source code under
**GPL-3.0-or-later** as well.

Private use within the family is not affected. The repository has no licence file yet,
so this is for the owner to decide.
