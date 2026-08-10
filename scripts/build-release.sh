#!/bin/zsh
set -euo pipefail

KEYCHAIN_SERVICE="FluidCapsule Release Signing"
KEYCHAIN_ACCOUNT="release"
DEFAULT_KEYSTORE_PATH="$HOME/Library/Application Support/FluidCapsule/signing/release.jks"

export FLUID_CAPSULE_KEYSTORE_PATH="${FLUID_CAPSULE_KEYSTORE_PATH:-$DEFAULT_KEYSTORE_PATH}"
export FLUID_CAPSULE_KEY_ALIAS="${FLUID_CAPSULE_KEY_ALIAS:-fluidcapsule}"

if [[ ! -f "$FLUID_CAPSULE_KEYSTORE_PATH" ]]; then
  print -u2 "Release keystore not found: $FLUID_CAPSULE_KEYSTORE_PATH"
  exit 2
fi

signing_password="$(security find-generic-password \
  -s "$KEYCHAIN_SERVICE" \
  -a "$KEYCHAIN_ACCOUNT" \
  -w)"
export FLUID_CAPSULE_KEYSTORE_PASSWORD="$signing_password"
export FLUID_CAPSULE_KEY_PASSWORD="$signing_password"

JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}" \
  ./gradlew testDebugUnitTest lintDebug assembleRelease

unset signing_password FLUID_CAPSULE_KEYSTORE_PASSWORD FLUID_CAPSULE_KEY_PASSWORD
