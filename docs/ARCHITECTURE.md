# Architecture

FluidCapsule converts source notifications into a small internal event model and publishes them through the best notification surface available on the device.

## Pipeline

1. `CapsuleNotificationListenerService` receives posted notifications.
2. `NotificationNormalizer` extracts stable text, icons, sender information, actions, and the source content intent.
   When notification-history recording is enabled for the source app, the normalized external notification is written to the local history database before whitelist routing. The final local routing decision and explanation are attached to the same row. Updates to the same active notification refresh one history entry; removal closes that notification lifecycle.
3. `OtpParser` handles likely verification codes before the generic whitelist route.
4. `KnownNotificationAdapter` recognizes LocalSend transfer stages, Meituan order stages, and Speedtest's final `Test Complete` notification. Recognized Meituan marketing notifications are suppressed; Speedtest download and upload results are compacted into one line.
5. The listener applies user privacy settings and creates a `CapsuleEvent`, optionally including progress.
6. `CapsuleCoordinator` inserts the event into an in-memory queue and selects one visible winner. OTP events outrank ongoing custom states, which outrank ordinary notifications; events of the same priority use newest-first display.
7. `PublisherRouter` publishes an Android 16 promoted ongoing notification for the supported CPH2797 target.
8. `NotificationFactory` builds the notification, click action, forwarded source actions, actionless-event close fallback, and public lock-screen version.

## Queue and restoration

ColorOS still receives one stable capsule notification ID. Up to eight source events are retained only in process memory, so a newer WeChat message can replace the visible Telegram capsule without destroying Telegram's original click and reply context.

Opening the source, copying an OTP, replying, forwarding a source action, source-notification removal, or the selected visible timeout consumes that event and immediately republishes the next valid winner. Ordinary mirrored messages remain eligible for restoration for the configured display duration. Updating the same source notification key replaces its existing queue entry instead of creating a duplicate. On listener reconnection, recent still-active source notifications are normalized again to rebuild the queue.

If a source notification exposes no action, FluidCapsule adds a local `关闭` action with a distinct trash icon even when it also adds an app-specific action such as `打开回复`. It asks the connected notification-listener service to dismiss the source notification by its Android notification key, consumes the current capsule event, and restores the next queued event. If the source is already gone or Android rejects cancellation, the capsule still closes normally.

## Source action forwarding

FluidCapsule never fabricates a private API for another application. It can only forward capabilities that the source notification exposes:

- `contentIntent` for opening the original destination;
- ordinary `Notification.Action` instances such as mark-as-read;
- `RemoteInput` actions for native notification replies.

The custom reply activity exists because some ColorOS surfaces filter actions that directly contain `RemoteInput`. It collects text locally and forwards the result into the source application's original action.

## Data lifetime

- Notification content is processed in memory by default.
- When notification-history recording is explicitly enabled, normalized external notification content is stored in a local SQLite database. Turning recording off stops future writes but retains existing rows until their configured retention deadline.
- The history list displays a bounded recent window for UI performance. Retention and entry/app/all deletion are enforced locally and are available through both UI and ADB CLI.
- Pending capsule events are capped at eight and are never serialized to disk.
- Diagnostic storage contains state and timing metadata, not message bodies or OTP values.
- Reply text is forwarded to the original action and is not persisted by FluidCapsule.
- OTP copy actions place the code in the Android clipboard only after an explicit tap.
- Speedtest result text comes from its final source notification and is processed in memory like other whitelisted notifications.

## Compatibility strategy

The project uses public Android 16 notification APIs. OEM promotion and rendering decisions remain outside the app's control; the underlying notification remains visible when ColorOS declines capsule promotion.

## Optional server persistence

The selected future server database is PostgreSQL. Android continues to use SQLite for offline-first capture and browsing; it must synchronize through an authenticated HTTPS API rather than connect directly to PostgreSQL. The schema and remaining security prerequisites are documented in [PostgreSQL notification-history boundary](POSTGRES_SYNC.md).
