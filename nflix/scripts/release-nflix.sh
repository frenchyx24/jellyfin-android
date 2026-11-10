#!/usr/bin/env bash
# One-command Nflix release: set version, build signed APK, publish to the Mira store.
# Usage: release-nflix.sh <version, e.g. 1.2.0> "<changelog>"
# Installed apps pick it up: Nflix shows an update prompt, Mira Connect updates it (automatically on Wi-Fi).
set -euo pipefail
VERSION="${1:?Missing version}"
CHANGELOG="${2:-}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SRC="$(cd "$SCRIPT_DIR/../.." && pwd)"
VERSION="${VERSION#v}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]] || { echo 'Refused: invalid semantic version.' >&2; exit 1; }
[[ "$VERSION" != *-* ]] || { echo 'Refused: prerelease publishing requires a dedicated beta manifest policy.' >&2; exit 1; }
CODE="$(python3 - "$VERSION" <<'CODE'
import re, sys
match = re.fullmatch(r'(\d+)\.(\d+)\.(\d+)(?:-[A-Za-z][A-Za-z0-9.-]*?\.(\d+))?', sys.argv[1])
if not match:
    raise SystemExit('Refused: unsupported version format.')
major, minor, patch = map(int, match.group(1, 2, 3))
pre = int(match.group(4)) if match.group(4) else 99
if max(major, minor, patch, pre) > 99:
    raise SystemExit('Refused: Android versionCode components exceed 99.')
print(major * 1_000_000 + minor * 10_000 + patch * 100 + pre)
CODE
)"
CHANNEL="${NFLIX_RELEASE_CHANNEL:-beta}"
case "$CHANNEL" in beta) SLUG=nflix-beta ;; stable) SLUG=nflix ;; *) echo 'Refused: release channel must be beta or stable.' >&2; exit 1 ;; esac
for CHECK_CHANNEL in nflix-beta nflix; do
  BODY="$(curl -sS -A 'MiraStorePublisher/1.1' -w '\n%{http_code}' "https://nflix.miracloud.fr/NflixAuth/Store/$CHECK_CHANNEL/manifest.json")"
  case "${BODY##*$'\n'}" in
    200) CURRENT="$(printf '%s' "${BODY%$'\n'*}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["versionCode"])')" ;;
    404) CURRENT=0 ;;
    *) echo "Refused: cannot read $CHECK_CHANNEL manifest." >&2; exit 1 ;;
  esac
  [ "$CODE" -gt "$CURRENT" ] || { echo "Refused: versionCode $CODE must exceed $CHECK_CHANNEL versionCode $CURRENT." >&2; exit 1; }
done
KEYS="${NFLIX_KEYS_DIR:-$HOME/nflix/keys}"
[ -n "${NFLIX_SIGNING_MOUNT:-}" ] || { [ -s "$KEYS/nflix-release.jks" ] && [ -s "$KEYS/keystore.env" ]; } || {
  echo 'Refused: original Nflix release keystore and signing configuration are required.' >&2
  exit 1
}
# Every release must be recoverable from GitHub: clean tree, pushed branch and tag, release record.
GIT=(git -C "$SRC")
[ -z "$("${GIT[@]}" status --porcelain)" ] || { echo 'Refused: commit every change before releasing (the release must be on GitHub).' >&2; exit 1; }
grep -q "^jellyfin.version=$VERSION$" "$SRC/gradle.properties" || { echo "Refused: gradle.properties must hold jellyfin.version=$VERSION in the committed tree." >&2; exit 1; }
GIT_PUSH=("${GIT[@]}")
[ -n "${NFLIX_GIT_CREDENTIAL:-}" ] && GIT_PUSH+=(-c credential.helper= -c "credential.helper=$NFLIX_GIT_CREDENTIAL")
"${GIT_PUSH[@]}" push -q origin "HEAD:refs/heads/release/$VERSION" || { echo 'Refused: could not push the release branch to GitHub.' >&2; exit 1; }
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
NFLIX_VERSION="$VERSION" NFLIX_BUILD_OUT="$TMP/out" "$SCRIPT_DIR/build-nflix.sh" testProprietaryDebugUnitTest assembleProprietaryRelease
shopt -s nullglob
APKS=("$TMP/out"/*/apk/proprietary/release/*.apk)
[ "${#APKS[@]}" -eq 1 ] && [ -s "${APKS[0]}" ] || { echo 'Refused: expected exactly one release APK.' >&2; exit 1; }
APK="${APKS[0]}"
[ "$(basename "$APK")" = "nflix-android-v$VERSION-proprietary-release.apk" ] || { echo 'Refused: release APK filename does not match the requested version.' >&2; exit 1; }
"$SCRIPT_DIR/store-publish.sh" "$SLUG" "$APK" "${NFLIX_ICON:-$HOME/nflix/out/nflix-store-icon.png}" \
  3C7E2866A82ADEA668857EE07B46995975C0096EA1642D590689A9EC1B320A64 "$CHANGELOG"
SHA="$(sha256sum "$APK" | cut -d' ' -f1 | tr a-f A-F)"
mkdir -p "$SRC/nflix/releases"
cat > "$SRC/nflix/releases/$VERSION.md" <<EOF
# Nflix $VERSION ($CHANNEL)

- versionCode: $CODE
- Store channel: \`$SLUG\`, published $(date -u +%Y-%m-%dT%H:%MZ)
- APK SHA-256: $SHA
- Signer SHA-256: 3C7E2866A82ADEA668857EE07B46995975C0096EA1642D590689A9EC1B320A64
- Source commit: $("${GIT[@]}" rev-parse HEAD)

Changelog: $CHANGELOG

## Rebuild

    git checkout v$VERSION
    nflix/scripts/build-nflix.sh testProprietaryDebugUnitTest assembleProprietaryRelease

Signing needs the private keystore in ~/nflix/keys (never committed).
EOF
"${GIT[@]}" add "nflix/releases/$VERSION.md"
"${GIT[@]}" commit -qm "docs: Nflix $VERSION release record"
"${GIT[@]}" tag -a "v$VERSION" -m "Nflix $VERSION ($CHANNEL, $CODE)"
"${GIT_PUSH[@]}" push -q origin "HEAD:refs/heads/release/$VERSION" "v$VERSION" || { echo "WARNING: published, but pushing v$VERSION to GitHub failed: push it now." >&2; exit 1; }
echo "GitHub: release/$VERSION and tag v$VERSION pushed."
