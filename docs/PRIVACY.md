# Privacy and security

FluidCapsule needs powerful Android permissions because its core feature is transforming notifications. Enable only the capabilities you understand and need.

## Notification access

Notification-listener access allows the app to read notifications, including potentially sensitive message content. FluidCapsule applies the user-selected whitelist to generic notification mirroring and supported result formatting such as Speedtest's final notification; OTP parsing is limited to the device's default SMS application.

Processing is local. The manifest does not request internet access, and the project contains no analytics or advertising SDK.

## Notification posting

Posting access is used to create the capsule or its standard notification fallback. On Android versions that support promoted ongoing notifications, the user may need to enable promotion separately.

## Accessibility

The optional accessibility service is intended to support explicit keep-alive behavior on aggressive OEM background-management systems. It does not retrieve window content or automate third-party app interfaces.

Accessibility remains optional. Notification-based adapters, including Speedtest result formatting, continue to work when it is disabled.

## Clipboard

Tapping an OTP capsule copies the OTP to the system clipboard. Other apps or the operating system may display or inspect clipboard contents according to Android's clipboard policy. The masked-preview option affects the preview label, not the copied value itself.

## Logs

Do not add notification bodies, sender names, OTPs, reply text, content-intent payloads, device identifiers, or package inventories to logs. Performance logging may contain aggregate timings and counts only.

## Responsible testing

Use synthetic notifications and test accounts. Before sharing a screenshot or bug report, remove names, avatars, messages, OTPs, device serials, IP addresses, and local filesystem paths.
