#!/usr/bin/env bash
# Fails if an APK requests any network permission. This is the app's core privacy guarantee:
# without these permissions Android does not let the app open network connections at all.
set -euo pipefail
apk="$1"
aapt2="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)/aapt2"
permissions="$("$aapt2" dump permissions "$apk")"
echo "$permissions"
if echo "$permissions" | grep -E "android.permission.(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE)"; then
  echo "FAIL: $apk requests a network permission." >&2
  exit 1
fi
echo "OK: $apk requests no network permissions."
