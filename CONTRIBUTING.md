# Contributing

Thank you for helping improve FluidCapsule.

## Before opening an issue

- Reproduce the problem on the latest default branch.
- State the Android version, OEM skin version, and whether promoted notifications are available.
- Replace message content, OTPs, names, avatars, device serials, IP addresses, and local paths with synthetic values.
- Do not attach third-party APKs, decompiled source, signing keys, or proprietary assets.

## Pull requests

1. Create a focused branch.
2. Keep privacy-sensitive text out of logs and fixtures.
3. Run:

   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```

4. Describe the user-visible behavior and the devices or Android versions tested.
5. Document OEM-specific assumptions and safe fallback behavior.

By contributing, you agree that your contribution is licensed under the MIT License.
