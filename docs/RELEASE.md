# Installing on her tablet (signed release builds)

Use a **signed release build** for real use:

- **Updates keep her data.** A new version can only install over the old one, keeping
  her data, if both are signed with the same key.
- **Her data can't be read over USB.** Release builds are not debuggable. On a debug build,
  anyone with a USB cable and developer tools could read the app's files.

## One-time setup: the signing key

On a trusted computer with a JDK, create the key:

```
keytool -genkeypair -v -keystore elizabeth-release.jks -keyalg RSA -keysize 4096 \
    -validity 10000 -alias elizabeth
```

Keep `elizabeth-release.jks` and its passwords somewhere safe, such as a password manager
plus an offline copy. **If the key is lost, updates cannot be installed over the existing
app.** The app would have to be uninstalled, and her data would be lost unless it had been
exported first.

Add these repository secrets on GitHub (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `ELIZABETH_KEYSTORE_BASE64` | Output of `base64 -w0 elizabeth-release.jks` |
| `ELIZABETH_KEYSTORE_PASSWORD` | The keystore password |
| `ELIZABETH_KEY_ALIAS` | `elizabeth` |
| `ELIZABETH_KEY_PASSWORD` | The key password |

## Building a release

Go to Actions → **Release APK** → **Run workflow** (or push a tag such as `v0.1.0`). The
workflow:
1. runs the tests;
2. builds and signs the APK;
3. checks it is signed, not debuggable, and requests no network permission;
4. publishes `elizabeth-release-apk` (the APK plus its SHA-256).

## Installing

1. Download the artifact and check the APK's SHA-256 against the `.sha256` file.
2. Copy the APK to the tablet by USB. Open it and allow installing from that source
   when asked.
3. Set a screen lock on the tablet (Android Settings → Security).
4. Open the app. In Settings → Always available, consider making it the home screen.
5. Updates: install newer release APKs the same way. Her data is kept.
