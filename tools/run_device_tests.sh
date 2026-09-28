#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Runs the engine's device tests, then the app's with the engine installed (as on a real tablet).
# On failure, prints the failing tests and the device log, so CI logs show why.
set -u

report() {
    echo "==== Failed tests ===="
    python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
for f in glob.glob("*/build/outputs/androidTest-results/connected/**/*.xml", recursive=True):
    for case in ET.parse(f).getroot().iter("testcase"):
        for bad in list(case.iter("failure")) + list(case.iter("error")):
            print(f"--- {case.get('classname')}.{case.get('name')}")
            print((bad.text or bad.get("message") or "")[:4000])
PY
    echo "==== Crashes ===="
    adb logcat -d -b crash | tail -n 200
    echo "==== Recent log (speech engine) ===="
    adb logcat -d | grep -iE "uk\.elizabeth|sherpa|onnx|TextToSpeech|TTS|AndroidRuntime|DEBUG|libc" | tail -n 300
    exit 1
}

adb logcat -c || true
./gradlew --no-daemon :engine:connectedDebugAndroidTest || report
./gradlew --no-daemon :engine:installDebug :app:connectedDebugAndroidTest || report
