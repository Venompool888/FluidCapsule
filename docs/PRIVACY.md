# Privacy and security

FluidCapsule needs powerful Android permissions because its core feature is transforming notifications. Enable only the capabilities you understand and need.

## Notification access

Notification-listener access allows the app to read notifications, including potentially sensitive message content. FluidCapsule applies the user-selected whitelist to generic notification mirroring and supported result formatting such as Speedtest's final notification; OTP parsing is limited to the device's default SMS application.

Processing is local. The manifest does not request internet access, and the project contains no analytics or advertising SDK.

## Notification history

Notification history is disabled by default. When the user enables **Record new notifications**, FluidCapsule stores normalized notification content from external apps in a local SQLite database, including the source app, title, body, and capture time. This can include private messages, verification codes, financial alerts, and other sensitive text.

The switch controls future writes only:

- turning it on starts recording newly captured notifications;
- turning it off stops recording new notifications;
- turning it off never deletes entries that were already stored.

Updates to one still-active notification replace that notification's current history row instead of creating an entry for every progress or network-speed refresh. Once Android reports the notification as removed, a later notification with the same system key begins a new history entry.

History stays on the device and is not included in diagnostic output. The app has backups disabled. History follows a user-selected 1–30 day local retention period and can be deleted by entry, source app, or in full. The same lifecycle operations are exposed through the protected ADB CLI. Each source app can also opt out of local history independently.

History stores the local routing outcome and a short explanation such as “not whitelisted” or “OTP-only rule did not recognize a reliable code”. This diagnostic explanation never leaves the device.

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
