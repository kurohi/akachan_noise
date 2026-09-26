# Contributing to Akachan Noise

Thank you for helping build a calm, private app for parents!

## Ground rules

- **No tracking, no ads, no proprietary dependencies.** The app must never
  request `INTERNET`; `:app:checkMergedPermissions` enforces the permission
  allowlist and runs in CI.
- **Privacy is a feature.** If your change touches the microphone or any
  user data, explain in the PR exactly what is stored and what is not.
- **Sound design is UX.** Nothing may startle a baby: fades everywhere, no
  sudden loud events, no abrupt gain changes.
- All new user-facing strings go through `res/values/strings.xml` and
  `res/values-ja/strings.xml`.

## Development

```bash
./gradlew spotlessApply        # format before committing
./gradlew test lint            # must pass
./gradlew :engine:renderSamples  # listen to your sound changes in build/samples
```

Sound-engine code lives in `:engine` (pure Kotlin, no Android). Please add a
unit test for DSP changes (see the existing tests for the harnesses).

## Licensing

By contributing, you agree that your contributions are licensed under the
GPL-3.0-or-later, like the rest of the project.
