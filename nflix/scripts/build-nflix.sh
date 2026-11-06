#!/usr/bin/env bash
# Build Nflix Android inside the docker Android SDK image (source piped via tar, gradle cache in volume).
# Usage: build-nflix.sh <gradle tasks...>
# Release signing: the private keystore in ~/nflix/keys is streamed in, never committed.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SRC="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT="${NFLIX_BUILD_OUT:-$HOME/nflix/out}"
KEYS="${NFLIX_KEYS_DIR:-$HOME/nflix/keys}"
[ "$#" -gt 0 ] || set -- assembleProprietaryRelease
RELEASE=false
for task in "$@"; do
  case "$task" in *Release*|build|assemble|bundle) RELEASE=true ;; esac
done
if "$RELEASE"; then
  [ -s "$KEYS/nflix-release.jks" ] && [ -s "$KEYS/keystore.env" ] || {
    echo 'Refused: original Nflix release keystore and signing configuration are required.' >&2
    exit 1
  }
fi
mkdir -p "$OUT"
STAGE="$(mktemp -d)"
CONTAINER="nflix-build-$(date +%s%N)-$$"
cleanup() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$STAGE"
}
trap cleanup EXIT
chmod 700 "$STAGE"
tar -C "$SRC" --exclude='.git' --exclude='build' --exclude='.gradle' --exclude='.nflix-keys' -cf - . | tar -xf - -C "$STAGE"
if "$RELEASE"; then
  mkdir -m 700 "$STAGE/.nflix-keys"
  cp "$KEYS/nflix-release.jks" "$STAGE/.nflix-keys/"
  cp "$KEYS/keystore.env" "$STAGE/.nflix-keys/signing.env"
  chmod 600 "$STAGE/.nflix-keys/"*
fi
cat > "$STAGE/.nflix-build.sh" <<'BUILD'
set -euo pipefail
export ANDROID_HOME=/opt/android-sdk-linux ANDROID_SDK_ROOT=/opt/android-sdk-linux
trap 'rm -rf /src/.nflix-keys' EXIT
cd /src
if [ -f .nflix-keys/signing.env ]; then
  set -a
  source .nflix-keys/signing.env
  set +a
  export KEYSTORE_FILE=/src/.nflix-keys/nflix-release.jks
  : "${KEYSTORE_PASSWORD:?Missing original keystore password}"
  : "${SIGNING_KEY_ALIAS:?Missing original signing alias}"
  : "${SIGNING_KEY_PASSWORD:?Missing original signing key password}"
  export KEYSTORE_PASSWORD SIGNING_KEY_ALIAS SIGNING_KEY_PASSWORD
  keytool -exportcert -keystore "$KEYSTORE_FILE" -alias "$SIGNING_KEY_ALIAS" \
    -storepass:env KEYSTORE_PASSWORD -file /tmp/nflix-signing.der >/dev/null 2>&1
  CERT="$(sha256sum /tmp/nflix-signing.der | cut -d' ' -f1)"
  rm -f /tmp/nflix-signing.der
  [ "$CERT" = 3c7e2866a82adea668857ee07b46995975c0096ea1642d590689a9ec1b320a64 ] || {
    echo 'Refused: keystore certificate differs from the production Nflix certificate.' >&2
    exit 1
  }
fi
chmod +x gradlew
./gradlew --no-daemon --stacktrace "$@" >&2
BUILD
printf 'set --' > "$STAGE/.nflix-args.sh"
if [ -n "${NFLIX_VERSION:-}" ]; then
  printf ' -Pjellyfin.version=%q' "${NFLIX_VERSION#v}" >> "$STAGE/.nflix-args.sh"
fi
printf ' %q' "$@" >> "$STAGE/.nflix-args.sh"
printf '\nsource /src/.nflix-build.sh\n' >> "$STAGE/.nflix-args.sh"
# The command contains no signing passwords; the private configuration travels in stdin only.
docker create --name "$CONTAINER" -i -v nflix-gradle:/root/.gradle mira-android-builder:v02 \
  bash -lc 'set -e; mkdir -p /src; tar -xzf - -C /src; exec bash /src/.nflix-args.sh' >/dev/null
if ! tar -C "$STAGE" -czf - . | docker start -ai "$CONTAINER"; then
  echo 'Build failed.' >&2
  exit 1
fi
[ "$(docker wait "$CONTAINER")" = 0 ] || { echo 'Build failed.' >&2; exit 1; }
mkdir -p "$OUT/$CONTAINER"
docker cp "$CONTAINER:/src/app/build/outputs/." "$OUT/$CONTAINER/"
if docker cp "$CONTAINER:/src/app/build/test-results/." "$OUT/$CONTAINER/test-results/"; then
  echo "TEST RESULTS: $OUT/$CONTAINER/test-results"
fi
echo "BUILD OK: $OUT/$CONTAINER"
