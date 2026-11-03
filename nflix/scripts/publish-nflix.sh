#!/usr/bin/env bash
# Stage a signed Nflix release for the Nflix Auth plugin distribution endpoint.
# Output: ~/nflix/publish/{nflix.apk,manifest.json,icon.png} served at
# https://nflix.miracloud.fr/NflixAuth/Android/... once copied to /config/data/nflix-android.
# Usage: publish-nflix.sh "changelog text"
set -euo pipefail
APK=$(ls "$HOME"/nflix/out/outputs/apk/proprietary/release/nflix-android-v*-proprietary-release.apk | head -1)
OUT="$HOME/nflix/publish"
EXPECTED_SIGNER="e4c459e949878607ab224b790d5bc977caf01fb238c7a0fbfadef0243649866f"
CHANGELOG="${1:-}"
rm -rf "$OUT" && mkdir -p "$OUT"
cp "$APK" "$OUT/nflix.apk"

docker rm -f nflix-publish >/dev/null 2>&1 || true
docker create --name nflix-publish mira-android-builder:v02 bash -c '
B=/opt/android-sdk-linux/build-tools/36.0.0
$B/apksigner verify --print-certs /a.apk | sed -n "s/.*certificate SHA-256 digest: //p" | head -1
$B/aapt dump badging /a.apk | sed -n "s/^package: name=.\([^'"'"']*\). versionCode=.\([0-9]*\). versionName=.\([^'"'"']*\).*/\1 \2 \3/p"
$B/aapt dump badging /a.apk | sed -n "s/^sdkVersion:.\([0-9]*\)./\1/p"
' >/dev/null
docker cp "$OUT/nflix.apk" nflix-publish:/a.apk
INFO=$(docker start -a nflix-publish)
docker rm -f nflix-publish >/dev/null
SIGNER=$(echo "$INFO" | sed -n 1p)
read -r PKG CODE NAME <<<"$(echo "$INFO" | sed -n 2p)"
MINSDK=$(echo "$INFO" | sed -n 3p)
[ "$SIGNER" = "$EXPECTED_SIGNER" ] || { echo "Unexpected signer $SIGNER" >&2; exit 1; }
[ "$PKG" = "fr.miracloud.nflix" ] || { echo "Unexpected package $PKG" >&2; exit 1; }

SHA=$(sha256sum "$OUT/nflix.apk" | cut -d' ' -f1 | tr 'a-f' 'A-F')
SIZE=$(stat -c %s "$OUT/nflix.apk")
cp "$HOME/nflix/brand/icon-512.png" "$OUT/icon.png"
python3 - "$OUT/manifest.json" "$PKG" "$CODE" "$NAME" "$SHA" "$SIZE" "$MINSDK" "$CHANGELOG" <<'EOF'
import json, sys, datetime
out, pkg, code, name, sha, size, minsdk, changelog = sys.argv[1:]
json.dump({
    "packageName": pkg, "versionCode": int(code), "versionName": name,
    "apkUrl": "/NflixAuth/Android/nflix.apk", "sha256": sha, "size": int(size),
    "minSdk": int(minsdk), "changelog": changelog,
    "signerSha256": "E4C459E949878607AB224B790D5BC977CAF01FB238C7A0FBFADEF0243649866F",
    "publishedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
}, open(out, "w"), ensure_ascii=False, indent=2)
EOF
cat "$OUT/manifest.json"
