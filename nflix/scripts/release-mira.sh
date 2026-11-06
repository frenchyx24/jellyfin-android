#!/usr/bin/env bash
# One-command Mira Connect release: set version, build + test + lint, sign, publish to the Mira store.
# Usage: release-mira.sh <versionName, e.g. 0.6.0> <versionCode, e.g. 6> "<changelog>"
# Installed Mira Connect apps update themselves from the store (automatically on Wi-Fi).
set -euo pipefail
VERSION="$1"; CODE="$2"; CHANGELOG="${3:-}"
SRC="$HOME/mira-connect-build"
KEYS="$HOME/nflix/keys"
OUT="$HOME/nflix/out"
sed -i -E "s/versionCode = [0-9]+/versionCode = $CODE/; s/versionName = \"[^\"]+\"/versionName = \"$VERSION\"/" "$SRC/app/build.gradle.kts"
STAGE="$(mktemp -d)"; trap 'rm -rf "$STAGE"' EXIT
tar -C "$SRC" --exclude='app/build' --exclude='.gradle' --exclude='*.keystore' --exclude='*.jks' --exclude='*.apk' -cf - . | tar -xf - -C "$STAGE"
mkdir -p "$STAGE/.keys" && cp "$KEYS/mira-connect-release.jks" "$STAGE/.keys/"
cat > "$STAGE/mirror.init.gradle" <<'EOF'
def mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
settingsEvaluated { s ->
    [s.dependencyResolutionManagement.repositories, s.pluginManagement.repositories].each { repos ->
        repos.withType(MavenArtifactRepository).configureEach { r ->
            if (r.url.toString().contains("repo.maven.apache.org")) r.url = mirror
        }
    }
}
EOF
docker rm -f mira-release >/dev/null 2>&1 || true
tar -C "$STAGE" -czf - . | docker run --name mira-release -i --env-file "$KEYS/mira-keystore.env" \
  -e MIRA_KEYSTORE_FILE=/work/.keys/mira-connect-release.jks \
  -e JAVA_TOOL_OPTIONS="-Drobolectric.dependency.repo.url=https://maven-central.storage-download.googleapis.com/maven2/" \
  mira-android-builder:v02 bash -lc '
set -e
mkdir -p /work && tar -xzf - -C /work
/tmp/gradle-8.13/bin/gradle -p /work -I /work/mirror.init.gradle assembleRelease testDebugUnitTest lintDebug --no-daemon >&2
rm -rf /work/.keys
'
docker cp mira-release:/work/app/build/outputs/apk/release/app-release.apk "$OUT/mira-connect-$VERSION.apk"
docker rm -f mira-release >/dev/null
"$HOME/nflix/store-publish.sh" mira-connect "$OUT/mira-connect-$VERSION.apk" "$HOME/nflix/brand/mira-icon-512.png" \
  9D6174BB6C20EC8EE349062DCA5B812AC261088578820BCAF14014E6259959FB "$CHANGELOG"
