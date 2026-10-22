# Contributing to abCheck

Thank you for helping improve abCheck. Contributions can be code, device compatibility reports, translations, tests, documentation, or design feedback.

## Before you start

- Use GitHub Issues for reproducible bugs and feature proposals.
- Use GitHub Discussions for questions and early ideas.
- Keep pull requests focused on one problem.
- Do not include signing keys, API keys, local configuration, or personal device data.

## Development setup

You need JDK 17, Android SDK 36, and an Android Studio version compatible with the Gradle files in this repository.

```bash
git clone https://github.com/abanana84/abCheck.git
cd abCheck
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Pull request checklist

- [ ] The change has a clear issue or motivation.
- [ ] The app builds with `./gradlew assembleDebug`.
- [ ] Unit tests and lint pass.
- [ ] New behavior includes tests where practical.
- [ ] UI changes include before/after screenshots.
- [ ] User-facing text is added to the default resources and relevant translations.
- [ ] Battery values are described as reported, calculated, or estimated accurately.

## Translations

Translation source data lives in `scripts/generate_locale_strings.py`. Update the translation map, then regenerate resources from the repository root:

```bash
python3 scripts/generate_locale_strings.py
```

Please have generated text reviewed by a fluent speaker when possible.

## Device compatibility reports

Android manufacturers expose different battery signals. A useful report includes:

- Device manufacturer and model
- Android version
- App version
- Which field is missing or incorrect
- Whether the phone is charging and which charger type is used
- A screenshot with unrelated personal information removed

Do not post device identifiers, account details, serial numbers, or private logs.
