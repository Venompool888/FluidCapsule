#!/bin/zsh
set -euo pipefail

ADB_BIN="${ADB_BIN:-$(command -v adb || true)}"
APP_PACKAGE="io.github.venompool888.fluidcapsule"
EXPECTED_MODEL="CPH2797"
EXPECTED_SDK="36"
EXPECTED_VERSION="1.0.0"
serial="${ANDROID_SERIAL:-}"

if [[ "${1:-}" == "--serial" ]]; then
  serial="${2:?--serial requires a device serial}"
  shift 2
fi

if [[ -z "$ADB_BIN" || -z "$serial" ]]; then
  print -u2 "Set ANDROID_SERIAL or pass --serial for the target CPH2797."
  exit 2
fi

read_prop() {
  "$ADB_BIN" -s "$serial" shell getprop "$1" | tr -d '\r'
}

model="$(read_prop ro.product.model)"
sdk="$(read_prop ro.build.version.sdk)"
if [[ "$model" != "$EXPECTED_MODEL" || "$sdk" != "$EXPECTED_SDK" ]]; then
  print -u2 "Refusing target: expected $EXPECTED_MODEL/API $EXPECTED_SDK, got $model/API $sdk."
  exit 3
fi

package_dump="$($ADB_BIN -s "$serial" shell dumpsys package "$APP_PACKAGE")"
version="$(print -r -- "$package_dump" | sed -n 's/.*versionName=//p' | head -n 1 | tr -d '\r')"
if [[ "$version" != "$EXPECTED_VERSION" ]]; then
  print -u2 "Expected installed version $EXPECTED_VERSION, got ${version:-not installed}."
  exit 4
fi

launch_output="$($ADB_BIN -s "$serial" shell am start -W -n "$APP_PACKAGE/.MainActivity")"
print -r -- "$launch_output" | grep -q 'Status: ok'

status_output="$("${0:A:h}/fluid-capsule-cli.sh" --serial "$serial" status)"
print -r -- "$status_output" | grep -q '"ok":true'

if "$ADB_BIN" -s "$serial" logcat -d -v brief AndroidRuntime:E '*:S' |
  grep -q "$APP_PACKAGE"; then
  print -u2 "Recent AndroidRuntime output references $APP_PACKAGE."
  exit 5
fi

print "CPH2797 smoke check passed: $EXPECTED_VERSION on API $EXPECTED_SDK."
