# The Elizabeth App

An offline, privacy-first communication app (AAC) for an Android tablet. It is for someone
who is losing her speech and whose touch is becoming less accurate. She selects words and
phrases, or types, and the app speaks them aloud. All processing happens on the tablet.

See [docs/DESIGN.md](docs/DESIGN.md) for the full design and roadmap.

## What works now

**Communicating**
- **Main screen that never scrolls:** message bar, Speak, Undo, Delete word, Clear, Say
  again, and a Call chime.
- **Quick replies always visible:** Yes, No, Wait, Help and Thank you.
- **Other ways to build a message:** phrase pages by topic, Recent messages, and a large
  ABC or QWERTY keyboard.
- **Prediction:** word completion and next-word prediction learn her words on the tablet.
  Whole previous messages are suggested as she types.

**Touch, for inaccurate and declining motor control**
- **One touch layer covers the whole screen.** Near misses count for the nearest button.
- **Select on release:** she can slide to the right button before lifting.
- **Filters:** slip grace, a minimum touch time, a repeat guard and palm rejection.
- **Other ways to press:** dwell (rest on a button to press it) and first-contact modes.
- **Buttons never move**, and empty cells keep their place.
- **Touch suggestions (optional):** the app counts corrections and misses (numbers only)
  and suggests setting changes for a carer to approve.

**Switch access and keyguard**
- **Switch scanning** with one switch (the highlight moves by itself) or two (one moves,
  one chooses). Switches connect as a USB or Bluetooth keyboard.
- **Keyguard template:** saves an SVG cutting template of the current layout at real
  size, for laser-cut acrylic.
- **Full-screen option.**

**Voice**
- **Offline speech only:** the Android speech engine, restricted to voices that work
  offline.
- **Installed voices:** her own voice, a donor's voice, or a published open voice,
  synthesised on the tablet with sherpa-onnx. A voice is only installed if its consent
  record (or licence) and checksums check out.
- **If speech fails,** the message is shown full-screen.
- **Message banking:** any phrase can be recorded in her voice, checked for quality, and
  played back exactly as recorded.
- **Voice banking:** consent (her own voice or a donor's), a 314-sentence script, an
  on-device quality check with automatic keep, progress towards 20 and 60 minutes of
  speech, rest reminders, and her own sentences.
- **Export for training:** an encrypted LJSpeech export for training a voice model. See
  [tools/voice-training](tools/voice-training/README.md).

**Privacy and security**
- **No internet permission.** CI fails the build if one appears.
- **Encrypted data:** everything is encrypted with an Android Keystore key, and cloud
  backup is disabled.
- **Her data controls:** a "What the app knows" screen. Learning, history and touch
  counting can be switched off, and single words forgotten.
- **Export and restore:** one encrypted, streaming export and restore. The whole file is
  checked before anything changes.
- **Erase everything:** deletes all data and destroys the encryption key.
- **Tamper-evident privacy log.**
- **Carer settings:** opened by holding the Settings button for 2 seconds, with an
  optional PIN.

**Resilience**
- **Speech in installed voices starts after the first sentence** instead of the whole
  message.
- **Home-screen mode (optional):** the app comes back after a crash, a restart or a press
  of Home.
- **Paper board:** a printable board of her phrases and the alphabet, as a back-up.
- **Carer PIN lockout:** after 5 wrong attempts, entry locks for a doubling period.

**Not tested on a tablet yet:** everything above builds, passes the checks in CI, and
runs in device tests on an Android emulator, including real speech from a published Piper
voice. It has not yet been used on a real tablet, or by Elizabeth. No voice has yet been
trained from real voice-banking recordings. See [docs/TESTING.md](docs/TESTING.md). See also
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): there is a licence decision to make
before sharing the app outside the family.

## Documents

- [docs/DESIGN.md](docs/DESIGN.md): the design, and the regulatory map.
- [docs/GUIDE.md](docs/GUIDE.md): a guide for Elizabeth, her family and carers,
  including what the AI does.
- [docs/DPIA.md](docs/DPIA.md): the Data Protection Impact Assessment (a draft for sign-off).
- [docs/RELEASE.md](docs/RELEASE.md): signed release builds and installing on her tablet.
- [docs/TESTING.md](docs/TESTING.md): the automated tests, and the checks people need to do.
- [docs/DATA_EXPORT.md](docs/DATA_EXPORT.md): the export file formats.
- [tools/voice-training/README.md](tools/voice-training/README.md): making her voice.
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): licences, including a decision to make.

## Install on the tablet

For real use, install a **signed release build**; see [docs/RELEASE.md](docs/RELEASE.md).
For trying it out, every CI run also builds a debug APK. Download `elizabeth-debug-apk` from the latest successful
run on the repository's **Actions** tab. Copy it to the tablet, allow installing from that
source, and open it.

For a natural-sounding voice straight away, package a published British Piper voice, as
described in [tools/voice-training](tools/voice-training/README.md#a-good-voice-to-use-in-the-meantime),
and import it in Settings → Voice. Otherwise choose an offline Android voice there.

## Build

Requirements: JDK 17 or later. The Android SDK is only needed for the app itself.

```
./gradlew :core:test            # core logic tests; no Android SDK needed
./gradlew :app:assembleDebug    # needs ANDROID_HOME or local.properties with sdk.dir;
                                # downloads the pinned sherpa-onnx AAR from GitHub once
tools/check_no_network.sh app/build/outputs/apk/debug/app-debug.apk
```

## Layout

- `core/`: pure Kotlin, unit-tested. Contains touch filtering, the message editor, word
  prediction, the phrase model, settings, the privacy log, PIN hashing, audio quality
  checks and the encrypted export format.
- `app/`: the Android app (Jetpack Compose). Contains the UI, the Keystore-encrypted
  storage, text-to-speech, recording and playback.
- `tools/`: the network-permission check, a script that decrypts exports without the
  app, and `voice-training/` (dataset check, Piper training guide, voice packaging).
- `docs/`: design and data-format documentation.
