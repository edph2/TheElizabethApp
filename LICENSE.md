# Licensing

This repository contains three separately licensed parts.

| Part | Folder | Licence |
|---|---|---|
| **The Elizabeth App** (the communication app) and its logic | `app/`, `core/`, `docs/` | Proprietary. Copyright © 2026 the project owner. All rights reserved. |
| **Piper Voice Engine** (a separate Android speech-engine app) | `engine/` | **GPL-3.0-or-later**: see [engine/LICENSE](engine/LICENSE) |
| **Voice file format** (encrypted files, voice manifests, consent records), shared by both apps, and the voice tools | `voiceformat/`, `tools/` | **Apache-2.0**: see [voiceformat/LICENSE](voiceformat/LICENSE) |

## Why the engine is separate

The engine includes espeak-ng (GPL-3.0), through sherpa-onnx, to turn text into speech sounds for
Piper voices. It is built and shipped as its own app. The communication app contains no engine
code; CI checks this on every build with `tools/check_no_gpl.sh`. It uses the engine only:

- through Android's standard `TextToSpeech` API, as any app uses any speech engine; and
- through the engine's documented voice-management provider
  ([docs/SPEECH_ENGINE_API.md](docs/SPEECH_ENGINE_API.md)).

## Obligations when distributing

- **The engine's complete source code** (`engine/` and `voiceformat/`) must be available to
  everyone who receives the engine, under the GPL. For example, publish those folders in a public
  repository, and set `source_url` in `engine/src/main/res/values/strings.xml` to it.
- **Third-party notices:** see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- **Voices:** each published voice has its own licence, set out in its `MODEL_CARD`.

This arrangement is a common way of building on GPL software. It is not legal advice; have it
reviewed by a solicitor who knows open-source licensing before selling the app.
