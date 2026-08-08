# FluidCapsule / 流体胶囊

Turn Android notifications into interactive capsules on supported ColorOS devices.

将 Android 通知转换为可交互的 ColorOS 流体云胶囊，包括验证码提取、通知白名单、原通知跳转和快捷回复。

> [!IMPORTANT]
> FluidCapsule is an independent, unofficial open-source project. It is not affiliated with or endorsed by OPPO, ColorOS, Telegram, WeChat, or any other app vendor.

## Features

- Listen for notifications from user-selected apps.
- Extract one-time passwords from SMS notifications and display the code directly.
- Copy an OTP by tapping its capsule, with an optional masked clipboard preview.
- Mirror whitelisted notifications with the original app icon, sender avatar, title, and message.
- Open the source notification and, when the source exposes Android `RemoteInput`, forward reply and mark-as-read actions.
- Show one-tap smart replies and adapt the reply panel accent color to the source app icon.
- Configure most settings through an ADB-friendly CLI.
- Keep notification processing available with explicit foreground-service and accessibility options.

## Platform support

- Minimum Android version: Android 8.0 / API 26.
- Best-tested path: Android 16 / API 36 on a ColorOS device with promoted ongoing notifications enabled.
- Other Android devices can use the standard ongoing-notification fallback, but OEM capsule rendering is not guaranteed.

## Privacy model

FluidCapsule processes notification text locally. The app does not request internet access and does not upload notification content. OTPs and reply text are not written to diagnostic logs. See [Privacy and security](docs/PRIVACY.md) before enabling notification access or accessibility features.

## Build

Requirements:

- Android SDK 36
- JDK 21
- Android platform-tools for ADB commands

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is intentionally excluded from Git. Build it locally or install a signed artifact from a future GitHub Release.

## Initial setup

1. Install and open FluidCapsule.
2. Grant notification-listener and notification-posting access.
3. Select trusted source apps in the notification whitelist.
4. On supported ColorOS versions, allow promoted/live notifications for FluidCapsule.
5. Optionally enable the explicit keep-alive controls if your device stops the listener in the background.

For repeatable device configuration, see [ADB CLI](docs/CLI.md).

## How it works

```text
Source notification
        ↓
NotificationListenerService
        ↓
Normalize → OTP parser / whitelist policy
        ↓
CapsuleEvent
        ↓
Promoted live notification or standard fallback
```

More detail is available in [Architecture](docs/ARCHITECTURE.md) and [ColorOS notes](docs/COLOROS.md).

## Important limitations

- A direct reply is only possible when the source notification supplies a valid `RemoteInput` action. FluidCapsule cannot invent a private sending API for another app.
- Smart replies are sent immediately when tapped. Manually typed replies still require the Send button.
- OEM live-notification behavior can change between ColorOS releases.
- The accessibility service in the current public version is for user-requested keep-alive support. Experimental UI automation for apps without native reply actions is not implemented.
- The project does not include third-party APKs, decompiled source, proprietary assets, private protocols, or account-bypass features.

## Contributing

Bug reports and pull requests are welcome. Please remove notification text, OTPs, usernames, avatars, device serials, and local paths from logs or screenshots before posting. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE) © 2026 FluidCapsule contributors.
