#!/usr/bin/env bash
# Publish a signed APK to the Mira store channel (Nflix Auth plugin).
# Usage: store-publish.sh <slug> <apk> <icon.png> <expected signer sha256> "<changelog>"
# Needs ~/nflix/keys/publish.env (MIRA_PUBLISH_TOKEN, MIRA_STORE_URL). Never commit that file.
set -euo pipefail
SLUG="$1"; APK="$2"; ICON="$3"; SIGNER_EXPECTED="$(echo "$4" | tr 'A-F' 'a-f')"; CHANGELOG="${5:-}"
source "$HOME/nflix/keys/publish.env"
UA="MiraStorePublisher/1.0 (Android build tools)"

docker rm -f mira-store-inspect >/dev/null 2>&1 || true
docker create --name mira-store-inspect mira-android-builder:v02 bash -c '
B=/opt/android-sdk-linux/build-tools/36.0.0
$B/apksigner verify --print-certs /a.apk | sed -n "s/.*certificate SHA-256 digest: //p" | head -1
$B/aapt dump badging /a.apk | sed -n "s/^package: name=.\([^'"'"']*\). versionCode=.\([0-9]*\). versionName=.\([^'"'"']*\).*/\1 \2 \3/p"
$B/aapt dump badging /a.apk | sed -n "s/^sdkVersion:.\([0-9]*\)./\1/p"
' >/dev/null
docker cp "$APK" mira-store-inspect:/a.apk
INFO=$(docker start -a mira-store-inspect)
docker rm -f mira-store-inspect >/dev/null
SIGNER=$(echo "$INFO" | sed -n 1p)
read -r PKG CODE NAME <<<"$(echo "$INFO" | sed -n 2p)"
MINSDK=$(echo "$INFO" | sed -n 3p)
[ "$SIGNER" = "$SIGNER_EXPECTED" ] || { echo "Refused: APK signed by $SIGNER, expected $SIGNER_EXPECTED" >&2; exit 1; }
SHA=$(sha256sum "$APK" | cut -d' ' -f1 | tr 'a-f' 'A-F')
echo "Publishing $SLUG: $PKG $NAME ($CODE) sha256 $SHA"

AUTH=(-H "X-Mira-Publish-Token: $MIRA_PUBLISH_TOKEN" -A "$UA")
curl -fsS "${AUTH[@]}" -X PUT --data-binary @"$APK" -H "Content-Type: application/octet-stream" "$MIRA_STORE_URL/$SLUG/app.apk" >/dev/null
[ -f "$ICON" ] && curl -fsS "${AUTH[@]}" -X PUT --data-binary @"$ICON" -H "Content-Type: image/png" "$MIRA_STORE_URL/$SLUG/icon.png" >/dev/null
MANIFEST=$(python3 -c 'import json,sys; print(json.dumps({"packageName":sys.argv[1],"versionCode":int(sys.argv[2]),"versionName":sys.argv[3],"sha256":sys.argv[4],"minSdk":int(sys.argv[5]),"changelog":sys.argv[6],"signerSha256":sys.argv[7].upper()}, ensure_ascii=False))' \
  "$PKG" "$CODE" "$NAME" "$SHA" "$MINSDK" "$CHANGELOG" "$SIGNER")
curl -fsS "${AUTH[@]}" -X PUT --data-binary "$MANIFEST" -H "Content-Type: application/json" "$MIRA_STORE_URL/$SLUG/manifest.json"
echo
