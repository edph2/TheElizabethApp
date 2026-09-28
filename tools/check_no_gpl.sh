#!/usr/bin/env bash
# Fails if the proprietary app's APK contains any part of the GPL speech engine (sherpa-onnx with
# espeak-ng, or ONNX Runtime). Those belong only in the separate Piper Voice Engine app.
set -euo pipefail
apk="$1"
found=0
if unzip -l "$apk" | grep -Ei "lib/.*(sherpa|onnxruntime|espeak)"; then found=1; fi
if unzip -l "$apk" | grep -Ei "espeak-ng-data"; then found=1; fi
for dex in $(unzip -Z1 "$apk" | grep -E '^classes[0-9]*\.dex$'); do
  if unzip -p "$apk" "$dex" | grep -aq "com/k2fsa/sherpa"; then echo "$dex contains sherpa-onnx classes"; found=1; fi
done
if [ "$found" -ne 0 ]; then
  echo "FAIL: $apk contains parts of the GPL speech engine." >&2
  exit 1
fi
echo "OK: $apk contains no part of the GPL speech engine."
