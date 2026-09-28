# Installing on a tablet (signed release builds)

There are two apps: **The Elizabeth App** and the **Piper Voice Engine**. Both must be signed
with **the same key**, because only apps signed with the engine's key may manage its voices.

Use **signed release builds** for real use:

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
3. checks that each is signed, not debuggable, and requests no network permission, and that
   the communication app contains no GPL engine code;
4. publishes `elizabeth-release-apk`: both APKs, plus their SHA-256 checksums.

## Installing

1. Download the artifact and check both APKs' SHA-256 against the `.sha256` file.
2. Copy both APKs to the tablet by USB. Open each, and allow installing from that source
   when asked. The order does not matter.
3. Set a screen lock on the tablet (Android Settings → Security).
4. Open the app. In Settings → Always available, consider making it the home screen.
5. Updates: install newer release APKs the same way. Data is kept.

**Publishing the engine's source:** the engine is GPL-licensed. Everyone who receives it must be
able to get its complete source (`engine/` and `voiceformat/`), for example from a public
repository. Set `source_url` in `engine/src/main/res/values/strings.xml` to that address. See
[LICENSE.md](../LICENSE.md).
