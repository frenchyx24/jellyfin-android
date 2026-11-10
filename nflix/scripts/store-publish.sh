#!/usr/bin/env bash
# Publish a signed APK to the Mira store channel (Nflix Auth plugin).
# Usage: store-publish.sh <slug> <apk> <icon.png> <expected signer sha256> "<changelog>"
# Needs ~/nflix/keys/publish.env (MIRA_PUBLISH_TOKEN, MIRA_STORE_URL). Never commit that file.
set -euo pipefail
SLUG="${1:?Missing channel}"; APK="${2:?Missing APK}"; ICON="${3:?Missing icon}"
SIGNER_EXPECTED="$(printf '%s' "${4:?Missing signing certificate}" | tr 'A-F' 'a-f')"; CHANGELOG="${5:-}"
case "$SLUG" in
  nflix|nflix-beta)
    PACKAGE_EXPECTED=fr.miracloud.nflix
    PIN=3c7e2866a82adea668857ee07b46995975c0096ea1642d590689a9ec1b320a64 ;;
  nflix-tv|nflix-tv-beta)
    # Nflix Android TV: same release key as the phone app
    PACKAGE_EXPECTED=fr.miracloud.nflix.tv
    PIN=3c7e2866a82adea668857ee07b46995975c0096ea1642d590689a9ec1b320a64 ;;
  mira-connect|mira-connect-beta)
    PACKAGE_EXPECTED=fr.miracloud.connect
    PIN=9d6174bb6c20ec8ee349062dca5b812ac261088578820bcaf14014e6259959fb ;;
  *) echo 'Refused: unknown release channel.' >&2; exit 1 ;;
esac
[ "$SIGNER_EXPECTED" = "$PIN" ] || { echo 'Refused: signing certificate pin changed.' >&2; exit 1; }
[ -s "$APK" ] && [ -s "$ICON" ] || { echo 'Refused: APK or icon is missing.' >&2; exit 1; }
if [ -n "${NFLIX_SIGNING_MOUNT:-}" ]; then
  # Read the publishing credential from the mounted Mira signing directory, in memory only.
  PUBLISH_ENV="$(docker run --rm -v "$NFLIX_SIGNING_MOUNT:/keys:ro" mira-android-builder:v02 cat /keys/publish.env)"
  env_value() { printf '%s\n' "$PUBLISH_ENV" | sed -n "s/^$1=//p" | head -1 | sed -e "s/^[\"']//" -e "s/[\"']\$//"; }
  MIRA_PUBLISH_TOKEN="$(env_value MIRA_PUBLISH_TOKEN)"; MIRA_STORE_URL="$(env_value MIRA_STORE_URL)"
  unset PUBLISH_ENV
else
  source "${NFLIX_KEYS_DIR:-$HOME/nflix/keys}/publish.env"
fi
: "${MIRA_PUBLISH_TOKEN:?Missing publishing credential}"
: "${MIRA_STORE_URL:?Missing store URL}"
[[ "$MIRA_STORE_URL" == https://* ]] || { echo 'Refused: HTTPS store required.' >&2; exit 1; }
# Accept either the server root or the full store base; channel paths live under /NflixAuth/Store.
MIRA_STORE_URL="${MIRA_STORE_URL%/}"
[[ "$MIRA_STORE_URL" == */NflixAuth/Store ]] || MIRA_STORE_URL="$MIRA_STORE_URL/NflixAuth/Store"
UA='MiraStorePublisher/1.1 (Android build tools)'
CONTAINER="mira-store-inspect-$(date +%s)-$$"
STAGE="$(mktemp -d)"
cleanup() { docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; rm -rf "$STAGE"; }
trap cleanup EXIT
chmod 700 "$STAGE"
docker create --name "$CONTAINER" mira-android-builder:v02 bash -c '
set -euo pipefail
B=/opt/android-sdk-linux/build-tools/36.0.0
CERTS="$($B/apksigner verify --print-certs /a.apk)"
SIGNER="$(printf "%s\n" "$CERTS" | sed -n "s/^Signer #1 certificate SHA-256 digest: //p")"
[ "$(printf "%s\n" "$CERTS" | grep -c "^Signer #[0-9]* certificate SHA-256 digest:")" -eq 1 ]
BADGING="$($B/aapt dump badging /a.apk)"
! printf "%s\n" "$BADGING" | grep -q "application-debuggable"
printf "%s\n" "$SIGNER"
printf "%s\n" "$BADGING" | sed -n "s/^package: name=.\([^'"'"']*\). versionCode=.\([0-9]*\). versionName=.\([^'"'"']*\).*/\1 \2 \3/p"
printf "%s\n" "$BADGING" | sed -n "s/^sdkVersion:.\([0-9]*\)./\1/p"
' >/dev/null
docker cp "$APK" "$CONTAINER:/a.apk"
INFO="$(docker start -a "$CONTAINER")"
[ "$(docker wait "$CONTAINER")" = 0 ] || { echo 'Refused: APK signature or metadata verification failed.' >&2; exit 1; }
SIGNER="$(printf '%s\n' "$INFO" | sed -n 1p | tr 'A-F' 'a-f')"
read -r PKG CODE NAME <<<"$(printf '%s\n' "$INFO" | sed -n 2p)"
MINSDK="$(printf '%s\n' "$INFO" | sed -n 3p)"
[ "$SIGNER" = "$PIN" ] && [ "$PKG" = "$PACKAGE_EXPECTED" ] || { echo 'Refused: APK signer or package mismatch.' >&2; exit 1; }
[[ "$CODE" =~ ^[0-9]+$ && "$MINSDK" =~ ^[0-9]+$ && -n "$NAME" ]] || { echo 'Refused: invalid APK metadata.' >&2; exit 1; }
# Refuse network errors and malformed manifests, rather than interpreting either as an empty channel.
CHANNELS=("${SLUG%-beta}" "${SLUG%-beta}-beta")
if [ "${NFLIX_PROMOTION:-0}" = 1 ]; then
  [ "$SLUG" = nflix ] || { echo 'Refused: promotion applies only to Nflix stable.' >&2; exit 1; }
  CHANNELS=(nflix)
fi
for CHANNEL in "${CHANNELS[@]}"; do
  STATUS="$(curl -sS -A "$UA" -o "$STAGE/current.json" -w '%{http_code}' "$MIRA_STORE_URL/$CHANNEL/manifest.json")"
  case "$STATUS" in
    200) python3 - "$STAGE/current.json" "$CODE" "$PIN" "$PKG" "$([ "$CHANNEL" = "$SLUG" ] && echo target || echo sibling)" <<'CHECK'
import json, sys
with open(sys.argv[1]) as source:
    current = json.load(source)
if current.get('packageName') != sys.argv[4]:
    raise SystemExit('Refused: distributed package differs from the pinned identity.')
# The sibling channel may still carry the retired certificate until it is promoted; the target must match the pin
# unless it is the one-time, user-approved migration away from the lost E4C459 key.
signer = current.get('signerSha256', '').lower()
retired = 'e4c459e949878607ab224b790d5bc977caf01fb238c7a0fbfadef0243649866f'
if sys.argv[5] == 'target' and signer not in (sys.argv[3], retired):
    raise SystemExit('Refused: distributed certificate differs from the pinned identity.')
if int(sys.argv[2]) <= int(current['versionCode']):
    raise SystemExit('Refused: versionCode must exceed every distributed channel.')
CHECK
      ;;
    404) ;;
    *) echo "Refused: cannot verify distributed channel (HTTP $STATUS)." >&2; exit 1 ;;
  esac
done
SHA="$(sha256sum "$APK" | cut -d' ' -f1 | tr 'a-f' 'A-F')"
python3 - "$PKG" "$CODE" "$NAME" "$SHA" "$MINSDK" "$CHANGELOG" "$SIGNER" > "$STAGE/manifest.json" <<'MANIFEST'
import json, sys
print(json.dumps(dict(packageName=sys.argv[1], versionCode=int(sys.argv[2]), versionName=sys.argv[3], sha256=sys.argv[4], minSdk=int(sys.argv[5]), changelog=sys.argv[6], signerSha256=sys.argv[7].upper()), ensure_ascii=False))
MANIFEST
# Keep the publishing token out of the process argument list.
printf 'header = "X-Mira-Publish-Token: %s"\n' "$MIRA_PUBLISH_TOKEN" > "$STAGE/curl.conf"
chmod 600 "$STAGE/curl.conf"
AUTH=(--config "$STAGE/curl.conf" -A "$UA")
curl -fsS "${AUTH[@]}" -X PUT --data-binary @"$APK" -H 'Content-Type: application/vnd.android.package-archive' "$MIRA_STORE_URL/$SLUG/app.apk" >/dev/null
curl -fsS "${AUTH[@]}" -X PUT --data-binary @"$ICON" -H 'Content-Type: image/png' "$MIRA_STORE_URL/$SLUG/icon.png" >/dev/null
curl -fsS "${AUTH[@]}" -X PUT --data-binary @"$STAGE/manifest.json" -H 'Content-Type: application/json' "$MIRA_STORE_URL/$SLUG/manifest.json" >/dev/null
curl -fsS -A "$UA" "$MIRA_STORE_URL/$SLUG/manifest.json" -o "$STAGE/published.json"
python3 - "$STAGE/manifest.json" "$STAGE/published.json" <<'VERIFY'
import json, sys
with open(sys.argv[1]) as source:
    expected = json.load(source)
with open(sys.argv[2]) as source:
    actual = json.load(source)
if any(actual.get(key) != expected[key] for key in ('packageName', 'versionCode', 'sha256', 'signerSha256')):
    raise SystemExit('Published manifest verification failed.')
VERIFY
curl -fsS -A "$UA" "$MIRA_STORE_URL/$SLUG/app.apk" -o "$STAGE/published.apk"
[ "$(sha256sum "$STAGE/published.apk" | cut -d' ' -f1 | tr 'a-f' 'A-F')" = "$SHA" ] || { echo 'Published APK verification failed.' >&2; exit 1; }
echo "Published and verified $SLUG: $PKG $NAME ($CODE) sha256 $SHA"
