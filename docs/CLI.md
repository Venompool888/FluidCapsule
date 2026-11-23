# ADB CLI

The CLI script exposes repeatable configuration for development and testing.

Version 1.x is supported on OPPO CPH2797 running API 36. Always pass the exact serial when more than one ADB target or duplicate wireless-mDNS entry is present.

```bash
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL status
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL whitelist list
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL whitelist add org.example.app
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL whitelist remove org.example.app
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set show-otp true
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set mask-clipboard false
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set show-whitelist-content true
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set keep-alive true
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set display-duration 8
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set history-enabled true
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set history-sort app_count
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set history-retention 30 days
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set history-retention 6 months
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set history-retention 2 years
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set history-retention forever
```

Every app-specific rule shown in the UI is also available through the CLI:

```bash
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL app-rule com.google.android.gm get
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL app-rule com.google.android.gm set ttl 10
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL app-rule com.google.android.gm set priority high
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL app-rule com.google.android.gm set content hide
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL app-rule com.google.android.gm set max-body 120
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL app-rule com.google.android.gm set include '验证码,security code'
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL app-rule com.google.android.gm set exclude '广告,promotion'
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL app-rule com.google.android.gm set record-history false
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL app-rule com.google.android.gm set otp-only true
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL app-rule com.google.android.gm reset
```

`ttl 0` inherits the global duration. Priority accepts `low`, `normal`, or `high`; content accepts `inherit`, `show`, or `hide`. Keyword values are comma-separated and evaluated locally.

History lifecycle operations:

```bash
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL history count
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL history purge 30 days
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL history delete-package com.google.android.gm
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL history clear
# The export page is capped at 25 rows and about 64 KiB of UTF-8 JSON to keep
# the protected broadcast response small. A long single row returns an error.
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL history export 0 25
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL history export AFTER_ID 25 SNAPSHOT_MAX_ID
```

`history export` returns normalized rows, their final local `decision` and
`decisionDetail`, plus `snapshotMaxId`, `nextAfterId`, and `hasMore`. Start with
`afterId=0` and keep the returned `snapshotMaxId` for every following page;
send the returned `nextAfterId` as the next `afterId` until `hasMore` is false.
The `snapshotMaxId` is an insertion upper bound, not a database transaction
snapshot: active rows may still be updated or deleted while pages are being
read. The export is read-only and is available through the same protected ADB
receiver as the other history commands.

System-level helpers are also available:

```bash
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL system notification-listener true
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL system post-notifications true
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL system accessibility true
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL system battery-optimization true
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL system sensitive-notifications true
```

If exactly one authorized Android device is connected, `--serial` may be omitted. Otherwise specify it explicitly or set `ANDROID_SERIAL`.

The exported CLI receiver requires Android's signature-level `DUMP` permission, which the ADB shell holds. Ordinary third-party applications cannot invoke it.

## Receive-only SMS cloud sync

Use the protected receiver directly for the sync commands (all results use the existing `ok`/`command` JSON format):

```bash
adb -s DEVICE_SERIAL shell am broadcast -a io.github.venompool888.fluidcapsule.CLI -n io.github.venompool888.fluidcapsule/.cli.FluidCapsuleCliReceiver --es command sync-connect --es server https://sync.example.com --es code ABCD-EFGH
# Alternatively replace --es code ... with --es username ACCOUNT --es password PASSWORD.
adb -s DEVICE_SERIAL shell am broadcast -a io.github.venompool888.fluidcapsule.CLI -n io.github.venompool888.fluidcapsule/.cli.FluidCapsuleCliReceiver --es command sync-receive-all
adb -s DEVICE_SERIAL shell am broadcast -a io.github.venompool888.fluidcapsule.CLI -n io.github.venompool888.fluidcapsule/.cli.FluidCapsuleCliReceiver --es command sync-pull
adb -s DEVICE_SERIAL shell am broadcast -a io.github.venompool888.fluidcapsule.CLI -n io.github.venompool888.fluidcapsule/.cli.FluidCapsuleCliReceiver --es command sync-status
adb -s DEVICE_SERIAL shell am broadcast -a io.github.venompool888.fluidcapsule.CLI -n io.github.venompool888.fluidcapsule/.cli.FluidCapsuleCliReceiver --es command sync-disconnect
```

`sync-status` reports locally cached connection/account/device/receiveFrom names and IDs, `realtimeConnected`, and the persistent `lastError`; it never exposes the token. `sync-pull` also reports `published`.
`sync-receive-all` clears `pushTo` and selects every other registered device. Only incoming SMS produce capsules; sent messages and remote send commands are acknowledged without execution.
Non-loopback HTTP requires the one-time warning to be accepted for that server in the settings screen before CLI connection. Debug permits cleartext only to `127.0.0.1` and `localhost`; release network policy is unchanged.
