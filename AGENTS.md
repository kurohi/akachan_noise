# AGENTS.md — build & project guide

## Project

Akachan Noise: free, private, open-source baby white-noise app for Android.
**Hard rule:** the app must never request `android.permission.INTERNET`.
The `:app:checkMergedPermissions` task enforces the permission allowlist.

## Environment

- JDK 25 (Temurin) at `/etc/java-config-2/current-system-vm` — used to run Gradle
- Android SDK at `/home/kurohi/Android/Sdk` (referenced via `local.properties`)
- Gradle wrapper 9.6.0; AGP 9.4.0 uses **built-in Kotlin** (do not apply
  `org.jetbrains.kotlin.android`; KGP is pinned via buildscript classpath)

## Commands

```bash
./gradlew :app:assembleDebug                 # build debug APK
./gradlew :app:installDebug                  # install on connected device
./gradlew test                               # all unit tests
./gradlew :engine:test                       # engine (DSP) tests
./gradlew lint                               # Android lint
./gradlew spotlessApply spotlessCheck        # formatting
./gradlew :app:checkMergedPermissions        # run AFTER assembleDebug/Release
./gradlew :engine:renderSamples              # WAVs of every sound in engine/build/samples
```

Emulator: `~/Android/Sdk/emulator/emulator -avd akachan_api37`
List devices: `adb devices -l`

## Module map

- `:engine` — pure Kotlin/JVM audio engine: DSP, generators, mixer, cry
  detector, mix models (kotlinx.serialization). NO Android imports.
- `:playback` — AudioTrack sink, render thread, Media3 service/session,
  timer, audio focus, cry monitor.
- `:data` — typed DataStore persistence, presets, import/export, recorder.
- `:app` — Compose UI (Material 3), theming, navigation, widget, QS tile.

## Conventions

- Kotlin 2.4, JVM target 17, compileSdk 37, minSdk 26.
- Version catalog in `gradle/libs.versions.toml`; verify a version exists
  (and is ≥7 days old) before pinning.
- Unidirectional data flow; ViewModels expose StateFlow.
- No allocations or locks in the audio render loop.
- User-facing strings always in resources (en + ja).
- All gain changes must ramp — never jump.
