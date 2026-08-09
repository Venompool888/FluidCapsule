# ColorOS compatibility notes

FluidCapsule uses Android's promoted ongoing notification APIs where available. ColorOS decides whether and how an eligible notification appears as a system capsule.

## Expected setup

- Android 16 / API 36 or a compatible OEM implementation.
- Notification posting enabled for FluidCapsule.
- Promoted/live notification permission enabled when the system exposes it.
- Notification listener enabled so source events can be mirrored.
- Battery settings that allow the listener to remain connected.
- For Speedtest only: the dedicated adapter switch and package-scoped accessibility service enabled.

## OEM behavior

- The system controls capsule geometry, animation, placement, and much of the expanded-card layout.
- Action filtering may differ from the standard notification shade.
- A source `RemoteInput` action may need to be represented by a normal action that opens FluidCapsule's local reply panel before the result is forwarded.
- Clicking a source content intent can be affected by Android background-activity launch rules; FluidCapsule uses a transparent activity trampoline to keep the action user-initiated.

## Safe fallback

If promotion is unavailable or rejected, FluidCapsule posts a standard ongoing notification with the same content and actions. A promoted capsule is an enhancement, not a requirement for notification processing.
