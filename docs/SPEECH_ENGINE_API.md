# Piper Voice Engine: interfaces

The Piper Voice Engine (`engine/`, package `uk.elizabeth.speech`, GPL-3.0-or-later) is a separate
Android app. Other apps, including The Elizabeth App, use it only through the two interfaces
below. It has no internet permission.

## 1. Speaking: Android's standard TextToSpeech API

The engine is an ordinary Android text-to-speech engine (a `TextToSpeechService`), so any app can
use it:

```kotlin
val tts = TextToSpeech(context, onInit, "uk.elizabeth.speech")
tts.voice = tts.voices.first { it.name == voiceId }   // voices report requiresNetwork = false
tts.setSpeechRate(1.0f)
tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "id")
```

- **Voices:** each installed voice appears as a `Voice`. Its name is the voice's id and its
  locale comes from the voice manifest (e.g. `en-GB`).
- **Audio:** audio is streamed sentence by sentence as 16-bit mono PCM at the model's sample rate
  (22,050 Hz for Piper medium voices).
- **Speed:** the speech rate is supported. Pitch is not, because Piper voices do not support it.
- **Settings screen:** Android's speech settings open the engine's own screen
  (`VoicesActivity`), where voices can be imported, tried and removed.

## 2. Managing voices: a provider for companion apps

Authority `uk.elizabeth.speech.voices`, protected by the signature permission
`uk.elizabeth.speech.permission.MANAGE_VOICES`. Only apps signed with the same key as the engine
can use it.

| Operation | How | Result |
|---|---|---|
| List voices | `query(content://uk.elizabeth.speech.voices/voices)` | Columns: `id`, `name`, `kind` (own/donor/stock), `speaker_name`, `licence`, `locale`, `consent_speaker`, `consent_time_millis`, `notice` |
| Export a voice | `openInputStream(content://…/voices/<id>)` | A plain ZIP in the voice-package layout (`voice/manifest.json`, `voice/model.onnx`, `voice/tokens.txt`, `voice/espeak-ng-data/…`), streamed |
| Install a voice | Write a plain ZIP to `openOutputStream(content://…/staging/<id>, "w")`, then `call("install", id)` | `ok` true, or false with `error`. The voice is checked exactly as on import (consent or licence present, checksums match), and it replaces an installed voice with the same id only after passing. |
| Remove a voice | `call("delete", id)` | `ok` true, or false with `error` |
| Remove all voices | `call("deleteAll")` | `ok` true, or false with `error` |
| Import an encrypted `.elizvoice` file | `startActivity(Intent("uk.elizabeth.speech.action.IMPORT_VOICE", fileUri))` with `FLAG_GRANT_READ_URI_PERMISSION` | The engine asks for the file's passphrase and checks the voice |

The voice-package format and the encrypted `.elizvoice` format are defined in `voiceformat/`
(Apache-2.0) and [DATA_EXPORT.md](DATA_EXPORT.md).
