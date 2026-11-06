#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SANDBOX="$TMP/project"
BIN="$TMP/bin"
mkdir -p "$SANDBOX/nflix/scripts" "$SANDBOX/nflix/tests" "$SANDBOX/app/build/outputs" "$BIN" "$TMP/home/nflix/keys" "$TMP/home/nflix/brand"
cp "$ROOT"/nflix/scripts/{build-nflix,release-nflix,store-publish,promote-nflix}.sh "$SANDBOX/nflix/scripts/"
cp "$ROOT"/gradle.properties "$SANDBOX/gradle.properties"
printf 'fixture-apk\n' > "$TMP/fixture.apk"
printf 'fixture-icon\n' > "$TMP/home/nflix/brand/icon-512.png"
printf 'MIRA_PUBLISH_TOKEN=FIXTURE_TOKEN\nMIRA_STORE_URL=https://mock.invalid/store\n' > "$TMP/home/nflix/keys/publish.env"
chmod 600 "$TMP/home/nflix/keys/publish.env"
cat > "$BIN/curl" <<'CURL'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$MOCK_CURL_LOG"
url="${!#}"
case "$url" in
  */nflix/manifest.json) data='{"versionCode":2000099,"packageName":"fr.miracloud.nflix","signerSha256":"3c7e2866a82adea668857ee07b46995975c0096ea1642d590689a9ec1b320a64"}' ;;
  */nflix-beta/manifest.json)
    digest="$(sha256sum "$MOCK_FIXTURE_APK" | cut -d' ' -f1)"
    [[ "${MOCK_PROMOTION_MISMATCH:-0}" == 1 ]] && digest=0000000000000000000000000000000000000000000000000000000000000000
    data='{"versionCode":3000099,"packageName":"fr.miracloud.nflix","signerSha256":"3c7e2866a82adea668857ee07b46995975c0096ea1642d590689a9ec1b320a64","sha256":"'"$digest"'","changelog":"fixture"}' ;;
  */app.apk) data='' ;;
  */icon.png) data='fixture-icon' ;;
  *) data='{}' ;;
esac
out=''
while (($#)); do
  if [[ "$1" == -o ]]; then out="$2"; shift 2; else shift; fi
done
if [[ -n "$out" ]]; then
  if [[ "$url" == */app.apk ]]; then cp "$MOCK_FIXTURE_APK" "$out"; else printf '%s\n' "$data" > "$out"; fi
fi
if [[ "$url" == */manifest.json && "$data" != '{}' ]]; then
  printf '%s\n' "$data"
fi
CURL
cat > "$BIN/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$MOCK_DOCKER_LOG"
case "$1" in
  create)
    container=""
    while (($#)); do
      if [[ "$1" == --name ]]; then container="$2"; shift 2; else shift; fi
    done
    printf '%s\n' "$container" > "$MOCK_CONTAINER_FILE" ;;
  start)
    if [[ "${MOCK_DOCKER_FAIL_START:-0}" == 1 ]]; then exit 17; fi
    if [[ "$*" == *" -ai "* ]]; then
      rm -rf "$MOCK_STAGE"
      mkdir -p "$MOCK_STAGE"
      tar -xzf - -C "$MOCK_STAGE"
      args="$(cat "$MOCK_STAGE/.nflix-args.sh")"
      printf '%s\n' "$args" > "$MOCK_ARGS_FILE"
      mkdir -p "$MOCK_STAGE/app/build/outputs/apk/proprietary/release"
      version="$(sed -n 's/.*-Pjellyfin.version=\([^ ]*\).*/\1/p' "$MOCK_ARGS_FILE")"
      [[ -n "$version" ]] || version=0.0.0-dev.1
      if [[ "${MOCK_APK_COUNT:-1}" == 1 ]]; then
        cp "$MOCK_FIXTURE_APK" "$MOCK_STAGE/app/build/outputs/apk/proprietary/release/nflix-android-v${version}-proprietary-release.apk"
      elif [[ "${MOCK_APK_COUNT:-1}" == 2 ]]; then
        cp "$MOCK_FIXTURE_APK" "$MOCK_STAGE/app/build/outputs/apk/proprietary/release/nflix-android-v${version}-proprietary-release.apk"
        : > "$MOCK_STAGE/app/build/outputs/apk/proprietary/release/nflix-android-v${version}-proprietary-release-duplicate.apk"
      fi
    fi
    ;;
  wait) printf '%s\n' "${MOCK_DOCKER_WAIT_STATUS:-0}" ;;
  cp)
    src="$2"; dest="$3"
    if [[ "$src" == *test-results* ]]; then exit 1; fi
    mkdir -p "$dest"
    cp -R "$MOCK_STAGE/app/build/outputs/." "$dest" ;;
  rm) ;;
  *) echo "unexpected docker invocation: $*" >&2; exit 90 ;;
esac
DOCKER
chmod +x "$BIN/curl" "$BIN/docker"
export PATH="$BIN:$PATH" HOME="$TMP/home" NFLIX_KEYS_DIR="$TMP/home/nflix/keys" MOCK_FIXTURE_APK="$TMP/fixture.apk"
export MOCK_DOCKER_LOG="$TMP/docker.log" MOCK_CURL_LOG="$TMP/curl.log" MOCK_CONTAINER_FILE="$TMP/container" MOCK_STAGE="$TMP/stage" MOCK_ARGS_FILE="$TMP/args"
: > "$MOCK_DOCKER_LOG"; : > "$MOCK_CURL_LOG"

pass() { printf 'PASS %s\n' "$1"; }
fail() { printf 'FAIL %s: %s\n' "$1" "$2" >&2; exit 1; }

# Missing signing material must fail before any Docker command.
if "$SANDBOX/nflix/scripts/build-nflix.sh" assembleProprietaryRelease >"$TMP/missing.out" 2>&1; then fail missing-key-refusal 'unexpected success'; fi
[[ ! -s "$MOCK_DOCKER_LOG" ]] || fail missing-key-refusal 'Docker was invoked'
pass missing-key-refusal

# Release gate must reject a versionCode not above both live manifests.
if "$SANDBOX/nflix/scripts/release-nflix.sh" 2.0.0 fixture >"$TMP/stale.out" 2>&1; then fail stale-version 'unexpected success'; fi
grep -q 'must exceed nflix-beta versionCode 3000099' "$TMP/stale.out" || fail stale-version 'wrong rejection'
[[ ! -s "$MOCK_DOCKER_LOG" ]] || fail stale-version 'Docker was invoked'
pass stale-version

# Placeholder key material satisfies only file-presence checks; mock Docker never signs.
printf 'PLACEHOLDER ONLY\n' > "$NFLIX_KEYS_DIR/nflix-release.jks"
printf 'KEYSTORE_PASSWORD=PLACEHOLDER\nSIGNING_KEY_ALIAS=PLACEHOLDER\nSIGNING_KEY_PASSWORD=PLACEHOLDER\n' > "$NFLIX_KEYS_DIR/keystore.env"

# Successful beta path must propagate requested version, choose beta, and find one exact APK.
# Stub publisher at copied script boundary to ensure no publication occurs.
cat > "$SANDBOX/nflix/scripts/store-publish.sh" <<'PUBLISH'
#!/usr/bin/env bash
printf '%s\n' "$*" > "$MOCK_PUBLISH_ARGS"
PUBLISH
chmod +x "$SANDBOX/nflix/scripts/store-publish.sh"
export MOCK_PUBLISH_ARGS="$TMP/publish.args"
printf '{"versionCode":2000099}\n' > "$TMP/stable.json"
: > "$MOCK_DOCKER_LOG"; : > "$MOCK_CURL_LOG"
if ! "$SANDBOX/nflix/scripts/release-nflix.sh" 4.0.0 fixture >"$TMP/release.out" 2>&1; then cat "$TMP/release.out" >&2; fail beta-version-arg 'release failed'; fi
grep -q -- '-Pjellyfin.version=4.0.0' "$MOCK_ARGS_FILE" || fail beta-version-arg 'Gradle version override missing'
grep -q '^nflix-beta ' "$MOCK_PUBLISH_ARGS" || fail beta-version-arg 'default channel was not beta'
grep -q 'nflix-android-v4.0.0-proprietary-release.apk' "$MOCK_PUBLISH_ARGS" || fail beta-version-arg 'APK path mismatch'
pass beta-version-arg-and-single-apk

# Duplicate APK-like outputs must be rejected before invoking publisher.
export MOCK_APK_COUNT=2
if "$SANDBOX/nflix/scripts/release-nflix.sh" 3.2.0 fixture >"$TMP/duplicate.out" 2>&1; then fail exact-apk-count 'unexpected success'; fi
grep -q 'expected exactly one release APK' "$TMP/duplicate.out" || fail exact-apk-count 'wrong rejection'
pass exact-apk-count
unset MOCK_APK_COUNT

# Docker build failure must propagate as a failed build.
export MOCK_DOCKER_FAIL_START=1
if "$SANDBOX/nflix/scripts/build-nflix.sh" assembleProprietaryRelease >"$TMP/docker-failure.out" 2>&1; then fail docker-exit-failure 'unexpected success'; fi
grep -q 'Build failed' "$TMP/docker-failure.out" || fail docker-exit-failure 'failure was not reported'
pass docker-exit-failure
unset MOCK_DOCKER_FAIL_START

# Promotion must reject beta content whose APK digest does not match manifest.
cp "$ROOT/nflix/scripts/store-publish.sh" "$SANDBOX/nflix/scripts/store-publish-real.sh"
mv "$SANDBOX/nflix/scripts/store-publish-real.sh" "$SANDBOX/nflix/scripts/store-publish.sh"
# Mock manifest deliberately has mismatched digest; no Docker or PUT should occur.
export MOCK_PROMOTION_MISMATCH=1
if "$SANDBOX/nflix/scripts/promote-nflix.sh" fixture >"$TMP/promotion.out" 2>&1; then fail promotion-integrity 'unexpected success'; fi
grep -q 'beta APK does not match its manifest' "$TMP/promotion.out" || fail promotion-integrity 'wrong rejection'
if grep -q -- '-X PUT' "$MOCK_CURL_LOG"; then fail promotion-integrity 'publication request occurred'; fi
pass promotion-integrity

printf 'All isolated release-script tests passed. No live publication performed.\n'
