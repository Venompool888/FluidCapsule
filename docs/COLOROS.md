# ColorOS compatibility notes

FluidCapsule uses Android's promoted ongoing notification APIs where available. ColorOS decides whether and how an eligible notification appears as a system capsule.

## Supported baseline

- OPPO CPH2797.
- Android 16 / API 36.
- Verified firmware: `CPH2797_16.0.9.400(EX01)`.
- Notification posting enabled for FluidCapsule.
- Promoted/live notification permission enabled when the system exposes it.
- Notification listener enabled so source events can be mirrored.
- Battery settings that allow the listener to remain connected.

## OEM behavior

- The system controls capsule geometry, animation, placement, and much of the expanded-card layout.
- Action filtering may differ from the standard notification shade.
- A source `RemoteInput` action may need to be represented by a normal action that opens FluidCapsule's local reply panel before the result is forwarded.
- Clicking a source content intent can be affected by Android background-activity launch rules; FluidCapsule uses a transparent activity trampoline to keep the action user-initiated.

## Notification fallback

The Android notification remains available even when ColorOS declines to render it as a capsule. This preserves the content and actions, but lack of capsule rendering on another firmware or device is outside the supported 1.0 scope.
