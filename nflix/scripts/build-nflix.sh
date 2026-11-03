#!/usr/bin/env bash
# Build Nflix Android inside the docker Android SDK image (source piped via tar, gradle cache in volume).
# Usage: build-nflix.sh <gradle tasks...>
# Release signing: the private keystore in ~/nflix/keys is streamed in, never committed.
set -euo pipefail
SRC="$HOME/nflix/jellyfin-android"
OUT="$HOME/nflix/out"
KEYS="$HOME/nflix/keys"
mkdir -p "$OUT"
TASKS="${*:-assembleProprietaryRelease}"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
tar -C "$SRC" --exclude='.git' --exclude='build' --exclude='.gradle' -cf - . | tar -xf - -C "$STAGE"
SIGN_ENV=""
if [ -f "$KEYS/nflix-release.jks" ]; then
  mkdir -p "$STAGE/.nflix-keys" && cp "$KEYS/nflix-release.jks" "$STAGE/.nflix-keys/"
  SIGN_ENV="export KEYSTORE_FILE=/src/.nflix-keys/nflix-release.jks; $(sed 's/^/export /' "$KEYS/keystore.env" | tr '\n' ';')"
fi
docker rm -f nflix-build >/dev/null 2>&1 || true
tar -C "$STAGE" -czf - . | \
docker run --name nflix-build -i -v nflix-gradle:/root/.gradle -e JELLYFIN_VERSION="${JELLYFIN_VERSION:-}" mira-android-builder:v02 bash -lc "
set -e
export ANDROID_HOME=/opt/android-sdk-linux ANDROID_SDK_ROOT=/opt/android-sdk-linux
[ -z \"\$JELLYFIN_VERSION\" ] && unset JELLYFIN_VERSION
mkdir -p /src && tar -xzf - -C /src
SDKM=/opt/android-sdk-linux/cmdline-tools/latest/bin/sdkmanager
yes | \$SDKM --install 'platforms;android-37' >/dev/null 2>&1 || true
$SIGN_ENV
cd /src
chmod +x gradlew
./gradlew --no-daemon --stacktrace $TASKS >&2
rm -rf /src/.nflix-keys
"
rm -rf "$OUT/outputs"; docker cp nflix-build:/src/app/build/outputs "$OUT/outputs" 2>/dev/null || true
docker rm -f nflix-build >/dev/null 2>&1 || true
echo "BUILD OK"
