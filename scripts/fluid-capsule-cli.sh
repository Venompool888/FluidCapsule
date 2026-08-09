#!/bin/zsh
set -euo pipefail

ADB_BIN="${ADB_BIN:-$(command -v adb || true)}"
APP_ACTION="io.github.venompool888.fluidcapsule.CLI"
APP_PACKAGE="io.github.venompool888.fluidcapsule"
LISTENER_COMPONENT="$APP_PACKAGE/io.github.venompool888.fluidcapsule.notification.CapsuleNotificationListenerService"
ACCESSIBILITY_COMPONENT="$APP_PACKAGE/io.github.venompool888.fluidcapsule.keepalive.KeepAliveAccessibilityService"
serial="${ANDROID_SERIAL:-}"

if [[ "${1:-}" == "--serial" ]]; then
  serial="${2:?--serial requires a device serial}"
  shift 2
fi

if [[ -z "$ADB_BIN" ]]; then
  print -u2 "adb was not found. Install Android platform-tools or set ADB_BIN."
  exit 2
fi

if [[ -z "$serial" ]]; then
  serial="$($ADB_BIN devices | awk 'NR > 1 && $2 == "device" {serial=$1; count++} END {if (count == 1) print serial}')"
fi

if [[ -z "$serial" ]]; then
  print -u2 "Select a device with ANDROID_SERIAL or --serial SERIAL."
  exit 2
fi

broadcast() {
  "$ADB_BIN" -s "$serial" shell am broadcast \
    --include-stopped-packages \
    -a "$APP_ACTION" \
    -p "$APP_PACKAGE" \
    "$@"
}

usage() {
  cat <<'EOF'
Usage:
  fluid-capsule-cli.sh [--serial SERIAL] status
  fluid-capsule-cli.sh [--serial SERIAL] whitelist list
  fluid-capsule-cli.sh [--serial SERIAL] whitelist add PACKAGE
  fluid-capsule-cli.sh [--serial SERIAL] whitelist remove PACKAGE
  fluid-capsule-cli.sh [--serial SERIAL] whitelist clear
  fluid-capsule-cli.sh [--serial SERIAL] set show-otp true|false
  fluid-capsule-cli.sh [--serial SERIAL] set mask-clipboard true|false
  fluid-capsule-cli.sh [--serial SERIAL] set show-whitelist-content true|false
  fluid-capsule-cli.sh [--serial SERIAL] set keep-alive true|false
  fluid-capsule-cli.sh [--serial SERIAL] system notification-listener true|false
  fluid-capsule-cli.sh [--serial SERIAL] system post-notifications true|false
  fluid-capsule-cli.sh [--serial SERIAL] system accessibility true|false
  fluid-capsule-cli.sh [--serial SERIAL] system battery-optimization true|false
  fluid-capsule-cli.sh [--serial SERIAL] system sensitive-notifications true|false
EOF
}

require_boolean() {
  case "$1" in
    true|false) ;;
    *) print -u2 "Value must be true or false."; exit 2 ;;
  esac
}

set_accessibility() {
  local enabled="$1"
  local current updated
  local -a services
  current="$($ADB_BIN -s "$serial" shell settings get secure enabled_accessibility_services | tr -d '\r')"
  [[ "$current" == "null" ]] && current=""
  services=("${(@s/:/)current}")
  services=("${(@)services:#}")

  if [[ "$enabled" == "true" ]]; then
    if (( ${services[(Ie)$ACCESSIBILITY_COMPONENT]} == 0 )); then
      services+=("$ACCESSIBILITY_COMPONENT")
    fi
  else
    services=("${(@)services:#$ACCESSIBILITY_COMPONENT}")
  fi

  updated="${(j/:/)services}"
  "$ADB_BIN" -s "$serial" shell settings put secure enabled_accessibility_services "$updated"
  if (( ${#services} == 0 )); then
    "$ADB_BIN" -s "$serial" shell settings put secure accessibility_enabled 0
  else
    "$ADB_BIN" -s "$serial" shell settings put secure accessibility_enabled 1
  fi
}

case "${1:-}" in
  status)
    broadcast --es command status
    ;;
  whitelist)
    case "${2:-}" in
      list) broadcast --es command whitelist-list ;;
      add) broadcast --es command whitelist-add --es package "${3:?package required}" ;;
      remove) broadcast --es command whitelist-remove --es package "${3:?package required}" ;;
      clear) broadcast --es command whitelist-clear ;;
      *) usage; exit 2 ;;
    esac
    ;;
  set)
    setting="${2:-}"
    value="${3:-}"
    case "$setting" in
      show-otp) command="set-show-otp-directly" ;;
      mask-clipboard) command="set-mask-clipboard" ;;
      show-whitelist-content) command="set-show-whitelist-content" ;;
      keep-alive) command="set-keep-alive" ;;
      *) usage; exit 2 ;;
    esac
    broadcast --es command "$command" --es value "$value"
    ;;
  system)
    setting="${2:-}"
    value="${3:-}"
    require_boolean "$value"
    case "$setting" in
      notification-listener)
        if [[ "$value" == "true" ]]; then
          "$ADB_BIN" -s "$serial" shell cmd notification allow_listener "$LISTENER_COMPONENT"
        else
          "$ADB_BIN" -s "$serial" shell cmd notification disallow_listener "$LISTENER_COMPONENT"
        fi
        ;;
      post-notifications)
        if [[ "$value" == "true" ]]; then
          "$ADB_BIN" -s "$serial" shell pm grant "$APP_PACKAGE" android.permission.POST_NOTIFICATIONS
        else
          "$ADB_BIN" -s "$serial" shell pm revoke "$APP_PACKAGE" android.permission.POST_NOTIFICATIONS
        fi
        ;;
      accessibility)
        set_accessibility "$value"
        ;;
      battery-optimization)
        if [[ "$value" == "true" ]]; then
          "$ADB_BIN" -s "$serial" shell dumpsys deviceidle whitelist +"$APP_PACKAGE"
        else
          "$ADB_BIN" -s "$serial" shell dumpsys deviceidle whitelist -"$APP_PACKAGE"
        fi
        ;;
      sensitive-notifications)
        if [[ "$value" == "true" ]]; then
          "$ADB_BIN" -s "$serial" shell cmd appops set \
            "$APP_PACKAGE" RECEIVE_SENSITIVE_NOTIFICATIONS allow
        else
          "$ADB_BIN" -s "$serial" shell cmd appops set \
            "$APP_PACKAGE" RECEIVE_SENSITIVE_NOTIFICATIONS ignore
        fi
        "$ADB_BIN" -s "$serial" shell cmd notification disallow_listener "$LISTENER_COMPONENT"
        "$ADB_BIN" -s "$serial" shell cmd notification allow_listener "$LISTENER_COMPONENT"
        ;;
      *) usage; exit 2 ;;
    esac
    broadcast --es command status
    ;;
  *)
    usage
    exit 2
    ;;
esac
