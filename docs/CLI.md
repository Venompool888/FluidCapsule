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
./scripts/fluid-capsule-cli.sh --serial DEVICE_SERIAL set speedtest-cloud true
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

`speedtest-cloud` controls the in-app consent switch. The separate `system accessibility true` helper enables the Android accessibility component during development; both must be enabled for Speedtest foreground metrics to be read.
