#!/usr/bin/env bash
# Promote the release currently in the nflix-beta channel to the main nflix channel
# (Nflix update prompt + Mira Connect automatic updates).
# Usage: promote-nflix.sh "<changelog>"
set -euo pipefail
CHANGELOG="${1:-}"
APK=$(mktemp --suffix=.apk)
trap 'rm -f "$APK"' EXIT
curl -fsS -A "MiraStorePublisher/1.0" -o "$APK" https://nflix.miracloud.fr/NflixAuth/Store/nflix-beta/app.apk
if [ -z "$CHANGELOG" ]; then
  CHANGELOG=$(curl -fsS -A "MiraStorePublisher/1.0" https://nflix.miracloud.fr/NflixAuth/Store/nflix-beta/manifest.json | python3 -c 'import json,sys;print(json.load(sys.stdin).get("changelog",""))')
fi
"$HOME/nflix/store-publish.sh" nflix "$APK" "$HOME/nflix/brand/icon-512.png" \
  E4C459E949878607AB224B790D5BC977CAF01FB238C7A0FBFADEF0243649866F "$CHANGELOG"
