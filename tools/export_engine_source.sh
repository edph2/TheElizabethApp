#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Assembles the public source of the Piper Voice Engine (GPL) into DEST, e.g. a clone of
# github.com/edph2/PiperVoiceEngine. Only files tracked by git are copied, so nothing private
# (the communication app, build output, local settings) can be included. Everything in DEST
# except .git is replaced, so files removed here are removed there too.
#
#   tools/export_engine_source.sh ../PiperVoiceEngine
#
# Publish the result for every engine release you distribute (tag it with the version), so
# everyone who receives the engine can get its complete source. See docs/RELEASE.md.
set -euo pipefail

dest="${1:?usage: $0 DEST}"
root="$(git -C "$(dirname "$0")/.." rev-parse --show-toplevel)"
[ -d "$dest" ] || mkdir -p "$dest"
dest="$(cd "$dest" && pwd)"
[ "$dest" != "$root" ] || { echo "DEST must not be this repository" >&2; exit 1; }

# Clear DEST, keeping its git history.
find "$dest" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +

copy() { # copy tracked files under $1 to $2 (default: same path)
    local src="$1" to="${2-$1}"
    git -C "$root" ls-files -z -- "$src" | while IFS= read -r -d '' f; do
        local rel="${f#"$src"}"
        mkdir -p "$(dirname "$dest/$to$rel")"
        cp -p "$root/$f" "$dest/$to$rel"
    done
}

copy engine/
copy voiceformat/
copy gradle/
copy gradlew
copy gradlew.bat
copy gradle.properties
copy public-engine/ ""
copy engine/LICENSE LICENSE
copy tools/check_no_network.sh
copy tools/voice-training/package_voice.py
copy tools/voice-training/elizbak.py
copy tools/voice-training/requirements.txt
mv "$dest/tools-README.md" "$dest/tools/voice-training/README.md"

copy docs/SPEECH_ENGINE_API.md
copy docs/VOICE_FILES.md

# Nothing from the communication app may be included.
if grep -rIl --exclude-dir=.git -e "uk.elizabeth.aac" "$dest"; then
    echo "FAIL: files above mention the communication app's code (uk.elizabeth.aac)" >&2
    exit 1
fi
echo "Exported the Piper Voice Engine source to $dest"
