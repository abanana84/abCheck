# Source architecture

abCheck 1.2.0 retains the package `com.abanana.abattery` and the existing battery measurement logic.

| Path under `app/src/main/` | Responsibility |
| --- | --- |
| `java/com/abanana/abattery/presentation/main/` | Android activity and application entry UI |
| `java/com/abanana/abattery/presentation/device/` | Device dashboard, tabs, tools, tests and report UI |
| `java/com/abanana/abattery/presentation/battery/` | Existing battery details and battery view model |
| `java/com/abanana/abattery/data/device/` | Hardware/system sampling, CPU frequency validation and device models |
| `java/com/abanana/abattery/diagnostics/` | Monitoring service, widgets, reporting and supporting diagnostics |
| `java/com/abanana/abattery/ui/theme/` | Compose theme and color definitions |
| `res/values/device_strings.xml` | Default diagnostic labels |
| `res/values-vi/device_strings.xml` | Vietnamese diagnostic labels |

## Tests and resources

- `app/src/test/`: local unit tests, including CPU frequency parsing and device model behavior.
- `app/src/androidTest/`: device/emulator integration checks.
- `scripts/generate_locale_strings.py`: existing battery translation generation.
- `scripts/generate_device_locales.py`: diagnostic label generation.
- `fastlane/metadata/android/en-US/`: Play metadata.

## Measurement boundaries

Device values depend on Android version, OEM restrictions and available hardware. CPU sampling rejects invalid values and identifies emulator dummy drivers. Battery availability and estimation are exposed rather than silently replaced with invented measurements. Hardware confirmation requires physical-device testing.

## Validation of the submitted code 9 build

On 22 October 2026, debug assembly, release bundle generation, 12 local unit tests and lint completed successfully. Lint reported zero errors and 58 warnings. A privacy-menu smoke check launched the browser. The full instrumentation suite was not rerun for the privacy-link change; previous emulator validation does not establish real-device hardware accuracy.
