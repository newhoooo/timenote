#!/usr/bin/env sh
# Lightweight bootstrap, not the generated Gradle wrapper. Uses the pinned official distribution.
set -eu
cd "$(dirname "$0")"
VERSION=8.11.1
if command -v gradle >/dev/null 2>&1; then
  installed_version=$(gradle --version)
  case "$installed_version" in *"Gradle $VERSION"*) exec gradle "$@" ;; esac
fi
DIST="$(pwd)/.gradle-dist"
BIN="$DIST/gradle-$VERSION/bin/gradle"
if [ ! -x "$BIN" ]; then
  command -v curl >/dev/null 2>&1 || { echo 'Install curl or Gradle 8.11.1 first.' >&2; exit 1; }
  command -v unzip >/dev/null 2>&1 || { echo 'Install unzip or Gradle 8.11.1 first.' >&2; exit 1; }
  mkdir -p "$DIST"
  URL="https://downloads.gradle.org/distributions/gradle-$VERSION-bin.zip"
  curl --fail --location --retry 2 "$URL" --output "$DIST/gradle.zip"
  curl --fail --location --retry 2 "$URL.sha256" --output "$DIST/gradle.sha256"
  if command -v sha256sum >/dev/null 2>&1; then
    actual=$(sha256sum "$DIST/gradle.zip")
  else
    actual=$(shasum -a 256 "$DIST/gradle.zip")
  fi
  expected=$(tr -d '[:space:]' < "$DIST/gradle.sha256")
  case "$actual" in "$expected "*) ;; *) echo 'Gradle checksum mismatch; download rejected.' >&2; exit 1 ;; esac
  unzip -q -o "$DIST/gradle.zip" -d "$DIST"
  rm "$DIST/gradle.zip" "$DIST/gradle.sha256"
fi
exec "$BIN" "$@"
