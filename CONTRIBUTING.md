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
   ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
   ```

4. For device-sensitive changes, run the four instrumentation tests and `scripts/verify-cph2797.sh` on CPH2797/API 36.
5. Describe the user-visible behavior and the exact CPH2797 firmware tested.
6. Document OEM-specific assumptions and notification fallback behavior.

By contributing, you agree that your contribution is licensed under the MIT License.
