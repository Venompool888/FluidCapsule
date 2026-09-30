# Privacy and security

FluidCapsule needs powerful Android permissions because its core feature is transforming notifications. Enable only the capabilities you understand and need.

## Notification access

Notification-listener access allows the app to read notifications, including potentially sensitive message content. FluidCapsule applies the user-selected whitelist to generic notification mirroring and supported result formatting such as Speedtest's final notification; OTP parsing applies to the default SMS application and configured source apps, subject to their rules.

Notification processing stays local. The app requests internet access for official rule updates, and the project contains no analytics or advertising SDK.

## Official rule subscription / 官方规则订阅

The subscription is on by default and can be switched off on the Rules page. When the user opens FluidCapsule, the app may send GET requests to fixed `raw.githubusercontent.com/Venompool888/FluidCapsule/main/rules/stable/` paths to check a signed manifest. It caches a successful check for six hours. A rule pack is fetched only when the user taps update; the app verifies its Ed25519 signature and SHA-256 hash before activation. Prompts appear only inside the app. Restoring built-in rules is available on the same page.

订阅默认开启，可在“规则”页面关闭。打开流体胶囊时，应用可能向固定的官方 GitHub 路径发送 GET 请求检查已签名清单；成功检查会缓存六小时。只有用户点击更新后才会下载规则包，验证签名及哈希后才启用。提醒仅在应用内显示，同页可恢复内置规则。

These requests contain no notification text, codes, verification URLs, device identifiers, history, or installed-app inventory. Rule files contain wording patterns only; notification parsing remains on the device. 请求不包含通知正文、验证码、验证链接、设备标识、历史记录或已安装应用列表；通知解析始终在设备本地进行。

## Notification history

Notification history is disabled by default. When the user enables **Record new notifications**, FluidCapsule stores normalized notification content from external apps in a local SQLite database, including the source app, title, body, and capture time. This can include private messages, verification codes, financial alerts, and other sensitive text.

The switch controls future writes only:

- turning it on starts recording newly captured notifications;
- turning it off stops recording new notifications;
- turning it off never deletes entries that were already stored.

Updates to one still-active notification replace that notification's current history row instead of creating an entry for every progress or network-speed refresh. Once Android reports the notification as removed, a later notification with the same system key begins a new history entry.

History stays on the device unless the user explicitly exports it from the History page or through the protected ADB CLI. Both exports include complete notification text and routing reasons; routine diagnostic logs do not. The History page uses Android's document picker to save a readable JSON backup wherever the user chooses. A selected third-party document provider may store that file in its cloud service; FluidCapsule itself does not upload history. The app has Android automatic backups disabled. History follows a user-selected retention period in days, months, or years, or can be kept forever and can be deleted by entry, source app, or in full. The same lifecycle operations are exposed through the protected ADB CLI. Each source app can also opt out of local history independently.

Importing a [history backup](HISTORY_BACKUP.md) requires selecting a file and confirming the import. It merges inactive archival rows without overwriting existing records, retains original capture times, and does not enable recording or publish capsules. If a file contains expired rows, the confirmation offers importing only records within the current retention period or importing everything and changing retention to forever. Cancelling does not change history or settings. The JSON backup does not include images, notification actions, system notification keys, or application settings.

History stores the local routing outcome and a short explanation such as “not whitelisted” or “OTP-only rule did not recognize a reliable code”. This explanation is included when the user explicitly exports history.

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
