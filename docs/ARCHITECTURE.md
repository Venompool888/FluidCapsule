# Architecture

FluidCapsule converts source notifications into a small internal event model and publishes them through the best notification surface available on the device.

## Pipeline

1. `CapsuleNotificationListenerService` receives posted notifications.
2. `NotificationNormalizer` extracts stable text, icons, sender information, actions, and the source content intent.
3. `OtpParser` handles likely verification codes before the generic whitelist route.
4. The listener applies user privacy settings and creates a `CapsuleEvent`.
5. `PublisherRouter` selects promoted live notifications on supported devices or the standard fallback publisher.
6. `NotificationFactory` builds the notification, click action, forwarded source actions, and public lock-screen version.

## Source action forwarding

FluidCapsule never fabricates a private API for another application. It can only forward capabilities that the source notification exposes:

- `contentIntent` for opening the original destination;
- ordinary `Notification.Action` instances such as mark-as-read;
- `RemoteInput` actions for native notification replies.

The custom reply activity exists because some ColorOS surfaces filter actions that directly contain `RemoteInput`. It collects text locally and forwards the result into the source application's original action.

## Data lifetime

- Notification content is processed in memory.
- Diagnostic storage contains state and timing metadata, not message bodies or OTP values.
- Reply text is forwarded to the original action and is not persisted by FluidCapsule.
- OTP copy actions place the code in the Android clipboard only after an explicit tap.

## Compatibility strategy

The project uses public Android notification APIs. OEM promotion and rendering decisions remain outside the app's control, so every promoted notification path has a standard-notification fallback.
