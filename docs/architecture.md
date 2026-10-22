# Source architecture

abCheck 1.3.0 retains the package `com.abanana.abattery` and the existing battery measurement logic.

| Path under `app/src/main/` | Responsibility |
| --- | --- |
| `java/com/abanana/abattery/presentation/main/` | Android activity and application entry UI |
| `java/com/abanana/abattery/presentation/device/` | Dashboard, device library, tools, tests and report UI |
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

## Navigation and presentation in 1.3.0

The 1.3.0 interface replaces the reference layout with four bottom destinations: Dashboard, Device, Tests and Tools. `CheckNavigation.kt` owns the bottom navigation and device category selector; `CheckLayout.kt` owns the dashboard, data sections, battery overview and action rows. `DeviceScreen.kt` retains routing, permissions, reports and lifecycle sampling. Per-page saveable state preserves scroll position. Back closes search before leaving the page, and nested screens return through the recorded navigation stack.

Tests are grouped by task; Tools also exposes monitors, battery history, widgets and benchmarks. App filters are inside the Apps page. Hardware/system/network/camera fields remain selectable, with labels above full-width values for long identifiers and large text. Typography and the paper/ink/teal light and dark palettes live in `ui/theme/`. Full design rationale and feature preservation mapping are in `research/independent-ui-20261022.md`. This describes a local candidate, not a newly published Google Play release.

## Validation of the submitted code 9 build

On 22 October 2026, debug assembly, release bundle generation, 12 local unit tests and lint completed successfully. Lint reported zero errors and 58 warnings. A privacy-menu smoke check launched the browser. The full instrumentation suite was not rerun for the privacy-link change; previous emulator validation does not establish real-device hardware accuracy.
