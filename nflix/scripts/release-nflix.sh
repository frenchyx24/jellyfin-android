#!/usr/bin/env bash
# One-command Nflix release: set version, build signed APK, publish to the Mira store.
# Usage: release-nflix.sh <version, e.g. 1.2.0> "<changelog>"
# Installed apps pick it up: Nflix shows an update prompt, Mira Connect updates it (automatically on Wi-Fi).
set -euo pipefail
VERSION="$1"; CHANGELOG="${2:-}"
SRC="$HOME/nflix/jellyfin-android"
sed -i "s/^jellyfin.version=.*/jellyfin.version=$VERSION/" "$SRC/gradle.properties"
"$HOME/nflix/build-nflix.sh" assembleProprietaryRelease
APK=$(ls "$HOME"/nflix/out/outputs/apk/proprietary/release/nflix-android-v"$VERSION"-proprietary-release.apk)
"$HOME/nflix/store-publish.sh" nflix "$APK" "$HOME/nflix/brand/icon-512.png" \
  E4C459E949878607AB224B790D5BC977CAF01FB238C7A0FBFADEF0243649866F "$CHANGELOG"
