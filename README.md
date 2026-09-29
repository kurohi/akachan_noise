# Akachan Noise

**Free, private, open-source white noise for babies** — あかちゃんのための無料・安心・オープンソースなホワイトノイズ。

Akachan Noise (赤ちゃん = baby) helps newborns sleep with the sounds they know
best: a mother's heartbeat, the blood flow of the womb, and gentle noise.
Built for parents, not for profit.

## Principles

- **Private by construction.** The app has no `INTERNET` permission, so it
  physically cannot send your data anywhere. No analytics, no ads, no
  accounts, no tracking. A build check enforces this.
- **Free forever.** Licensed under the GNU GPL-3.0. Source at
  [github.com/kurohi/akachan_noise](https://github.com/kurohi/akachan_noise),
  releases on GitHub and F-Droid.
- **Calm and safe.** Every sound change fades gently. The sleep timer is on
  by default, and a "soothe → settle" mode lowers the volume once your baby
  is asleep. See the in-app Safety guide.

## Features

- All built-in sounds are **synthesized in real time** — no loops with seams,
  no audio downloads, tiny app size:
  - Womb: heartbeat (adjustable BPM), blood-flow whoosh synced to the
    heartbeat, deep womb ambience
  - Noise: white (TV static), pink, brown
  - Nature: ocean waves, rain, stream, wind
  - Home: fan, hair dryer, vacuum, car ride, plastic-bag rustle
  - Voice-like: shush
- Mix up to 8 sounds, save mixes, share them as text codes
- Import your own audio or record your voice with an optional
  "womb filter" (muffled, as heard from inside)
- Sleep timer with fade-out; opt-in cry-activated restart (on-device only)
- Night-red sleep screen with touch lock; home-screen widget and Quick
  Settings tile
- English & Japanese

## Build

Requirements: JDK 17+ and the Android SDK (API 37).

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew test                        # unit tests
./gradlew lint spotlessCheck          # lint + format check
./gradlew :app:checkMergedPermissions # verify the permission allowlist
./gradlew :engine:renderSamples       # render every sound to WAV (desktop listening)
```

`local.properties` must point at your SDK:
`sdk.dir=/path/to/Android/Sdk`

## Releasing

The app is built and signed by F-Droid from this source, and released on
GitHub as well. Before tagging, build the minified release and smoke-test it
(R8 is where release-only crashes hide):

```bash
./gradlew :app:assembleRelease
# sign with a throwaway key, install, then check that it launches, plays,
# and opens an akachannoise://mix?d=... share link
```

Store metadata and screenshots live in `fastlane/metadata/android/`.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). Sound synthesis is written from
scratch for this project; third-party components are AndroidX, Media3 and
Kotlin (Apache-2.0).
