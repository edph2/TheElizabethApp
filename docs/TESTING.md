# Testing

## Automated (every push, in GitHub Actions)

| Level | What | Where |
|---|---|---|
| Core unit tests (JVM) | Touch filtering, message editing, prediction, phrases, paging, history, privacy log, PIN hashing and lockout, audio quality checks, WAV, encrypted export format (tampering, truncation, wrong passphrase), voice banking, training export, voice manifests, scanning, keyguard, touch suggestions, sentence splitting, paper board | `core/src/test` |
| Python tool tests | Encryption compatible with the app (both directions), dataset checks, packaging trained and published voices with a real ONNX model | `tools/voice-training/test_tools.py` |
| Build checks | Debug and release builds, Android lint, SBOM, **no network permission in the APK** | `.github/workflows/ci.yml` |
| Device tests (Android emulator) | See below | `app/src/androidTest` |

The device tests run on a real Android system (an emulator, API 34):

- **No network permission,** and **no network connection can be opened**.
- **Cloud backup is disabled.**
- **Encryption with the Android Keystore:** no plain text is left on disk, and changed or
  renamed files are detected.
- **Export → erase → restore gives back identical data.** A wrong passphrase or a
  truncated file changes nothing.
- **A real published Piper voice** (packaged by `package_voice.py`) imports and produces
  speech with the sherpa-onnx engine. A wrong passphrase installs nothing.
- **The main screen, through the real touch layer:** a deliberate press adds a phrase, a
  brief brush is ignored, and Undo works. Accessibility actions (as used by TalkBack and
  Switch Access) work, as do the keyboard and word prediction.

Run locally with an emulator or a tablet connected:

```
./gradlew :core:test
python3 -m unittest discover tools/voice-training
./gradlew :app:connectedDebugAndroidTest
```

The end-to-end voice test is skipped unless
`app/src/androidTest/assets/test.elizvoice` exists. CI creates it; see the device-tests
job for the commands.

## By people (needed before relying on it)

Automated tests cannot tell whether it works for Elizabeth. In order:

1. **Carer run-through:**
   - Speak phrases, type with predictions, Undo, and the call chime.
   - Turn the volume down and check the warning.
   - Record a phrase and play it back.
   - Export, then erase everything, then restore.
   - Set a PIN and check the lockout.
   - Print the paper board.
2. **Voice:** package and import a published British voice. Check its speed and sound in
   the room where she will use it.
3. **With Elizabeth,** in short sessions:
   - Watch how she touches the screen, and adjust Settings → Touch.
   - Switch on touch counting for a week, then review the suggestions.
   - Check whether the button size and text size suit her.
4. **Accessibility:**
   - Check with TalkBack and with Android Switch Access.
   - Check the colours with a colour-blindness simulator.
   - If using a keyguard, test-cut the template in card before acrylic.
5. **Voice banking:** record one session, export it, and train a small test model before
   investing hours of her energy.
6. **Resilience:**
   - Turn on home-screen mode, then force-stop the app (Android Settings → Apps) and check
     it returns when Home is pressed.
   - Restart the tablet.
