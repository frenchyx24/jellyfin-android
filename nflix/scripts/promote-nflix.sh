#!/usr/bin/env bash
# Promote the release currently in the nflix-beta channel to the main nflix channel
# (Nflix update prompt + Mira Connect automatic updates).
# Usage: promote-nflix.sh "<changelog>"
set -euo pipefail
CHANGELOG="${1:-}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APK="$(mktemp --suffix=.apk)"
STAGE="$(mktemp -d)"
trap 'rm -f "$APK"; rm -rf "$STAGE"' EXIT
BASE="https://nflix.miracloud.fr/NflixAuth/Store/nflix-beta"
UA='MiraStorePublisher/1.1 (Android build tools)'
curl -fsS -A "$UA" -o "$STAGE/manifest.json" "$BASE/manifest.json"
curl -fsS -A "$UA" -o "$APK" "$BASE/app.apk"
python3 - "$STAGE/manifest.json" "$APK" <<'VERIFY'
import hashlib, json, sys
with open(sys.argv[1]) as source:
    manifest = json.load(source)
with open(sys.argv[2], 'rb') as apk:
    digest = hashlib.sha256(apk.read()).hexdigest()
if digest.lower() != manifest.get('sha256', '').lower():
    raise SystemExit('Refused: beta APK does not match its manifest.')
if manifest.get('packageName') != 'fr.miracloud.nflix' or manifest.get('signerSha256', '').lower() != '3c7e2866a82adea668857ee07b46995975c0096ea1642d590689a9ec1b320a64':
    raise SystemExit('Refused: beta package or certificate mismatch.')
if int(manifest['versionCode']) <= 2000099:
    raise SystemExit('Refused: beta versionCode does not exceed stable Nflix 2.0.0.')
print(manifest.get('changelog', ''))
VERIFY
if [ -z "$CHANGELOG" ]; then
  CHANGELOG="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get("changelog",""))' "$STAGE/manifest.json")"
fi
curl -fsS -A "$UA" -o "$STAGE/icon.png" "$BASE/icon.png"
NFLIX_PROMOTION=1 "$SCRIPT_DIR/store-publish.sh" nflix "$APK" "$STAGE/icon.png" \
  3C7E2866A82ADEA668857EE07B46995975C0096EA1642D590689A9EC1B320A64 "$CHANGELOG"
