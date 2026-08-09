# ADB CLI

The CLI script exposes repeatable configuration for development and testing.

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
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set history-retention 7
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
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL history purge 7
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL history delete-package com.google.android.gm
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL history clear
```

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
