# The Elizabeth App

An offline, privacy-first communication app (AAC) for an Android tablet. It is for someone
who is losing her speech and whose touch is becoming less accurate. She selects words and
phrases, or types, and the app speaks them aloud. All processing happens on the tablet.

See [docs/DESIGN.md](docs/DESIGN.md) for the full design and roadmap.

## What works now (Phase 1)

- **Communication screen that never scrolls:** message bar, Speak, Undo, Delete word,
  Clear, Say again, and a Call chime. Quick replies (Yes, No, Wait, Help, Thank you) are
  always visible. There are phrase pages by topic, Recent messages, and a large ABC or
  QWERTY keyboard.
- **Touch handling for inaccurate touch:** one touch layer covers the whole screen.
  - Near misses snap to the nearest button.
  - Select on release, so she can slide to the right button before lifting.
  - Short slips, brief brushes and tremor repeats are ignored.
  - Dwell (rest to press) and first-contact modes are available.
  - Palm contacts are ignored.
  - Every value is adjustable in Settings.
- **Prediction:** word completion and next-word prediction learn her words on the tablet.
  Whole previous messages are suggested as she types.
- **Speech:** uses the tablet's text-to-speech. Only voices that work offline are ever
  used. If speech fails, the message is shown full-screen.
- **Message banking:** any phrase can be recorded in her own voice. The app checks each
  recording's quality before keeping it, and plays it back exactly as recorded.
- **Privacy:**
  - No internet permission (CI fails the build if one appears).
  - All data is encrypted with an Android Keystore key.
  - Cloud backup is disabled.
  - A "What the app knows" screen.
  - Learning and history can be switched off, and individual words forgotten.
  - Encrypted export and restore, and Erase everything (which destroys the key).
  - A tamper-evident privacy log.
- **Carer settings:** opened by holding the Settings button for 2 seconds, with an
  optional PIN.

Not built yet (see the design's roadmap): voice banking with a script, training a voice
model of her voice, the embedded sherpa-onnx engine, switch scanning and keyguard templates.

## Install on the tablet

Every CI run builds a debug APK. Download `elizabeth-debug-apk` from the latest successful
run on the repository's **Actions** tab. Copy it to the tablet, allow installing from that
source, and open it.

For a good offline voice, install a speech engine with offline British English voices. Then
choose the engine and voice in Settings → Voice.

## Build

Requirements: JDK 17 or later. The Android SDK is only needed for the app itself.

```
./gradlew :core:test            # core logic tests; no Android SDK needed
./gradlew :app:assembleDebug    # needs ANDROID_HOME or local.properties with sdk.dir
tools/check_no_network.sh app/build/outputs/apk/debug/app-debug.apk
```

## Layout

- `core/`: pure Kotlin, unit-tested. Contains touch filtering, the message editor, word
  prediction, the phrase model, settings, the privacy log, PIN hashing, audio quality
  checks and the encrypted export format.
- `app/`: the Android app (Jetpack Compose). Contains the UI, the Keystore-encrypted
  storage, text-to-speech, recording and playback.
- `tools/`: the network-permission check, and a script that decrypts exports without the
  app.
- `docs/`: design and data-format documentation.
