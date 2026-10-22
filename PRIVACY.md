# abCheck Privacy Policy

Effective date: October 20, 2026

abCheck displays device, system and battery diagnostics. It has no advertising SDK, analytics SDK or account system.

## Local device information

The app reads information that Android and the device expose: battery readings, CPU and memory data, storage, display, camera characteristics, sensors, network configuration and traffic counters. Android may restrict or redact fields. Root checks and Shizuku diagnostic commands run only when selected; Shizuku requires separate user authorization.

## Optional permissions

- Camera: live preview and flashlight diagnostics. Camera images are not saved.
- Microphone: a user-started recording of at most 30 seconds, stored temporarily in app cache for playback and deleted when the test closes.
- Location: Wi-Fi identifiers/scans and GPS/GNSS tests. GPS readings are displayed locally and are not uploaded.
- Bluetooth: discovering nearby Bluetooth LE devices and reading paired devices.
- Usage access: Android app-usage information for the screen-time tool, enabled separately in system settings.
- Display over other apps and foreground services: a user-started floating monitor or local battery recording. Both can be stopped from the app or the service notification.
- Notifications: the user may allow notifications to see and stop an active monitor.
- Internet/network access: DNS/TCP connectivity checks and local-network discovery that the user starts. The target host/network receives ordinary connection probes. There is no automatic telemetry or upload endpoint.

Declining an optional permission leaves unrelated diagnostics available.

## Stored data

Theme, language and estimated battery-cycle progress remain in app storage. Benchmark history retains up to 30 runs. Optional battery monitoring retains up to 1,440 local samples. A home-screen widget reads local battery and memory data. Android backup is enabled, so the operating system may include app preferences in the user's configured backup.

Uninstalling normally removes app-private data. Reports that the user exports to a chosen destination, shares with another application or copies to the clipboard may persist outside app storage.

## Reports and sharing

Reports are created only through an explicit save, copy or share action. Reports can include local network addresses and device/build information. Review the report before sending it to someone else. abCheck does not upload reports or benchmark scores automatically.

## Third-party services

Google Play, the operating system, Shizuku and any sharing destination operate under their own terms and privacy policies. abCheck does not access DevCheck's servers or database. Play Integrity and online device rankings require additional service configuration and are not represented as working signed verdicts or live rankings.

## Questions

Open a GitHub Discussion for general privacy questions. Do not post sensitive information in a public issue or discussion.
