# Privacy statement

Akachan Noise is built so that your data stays yours.

## The short version

- The app **cannot access the internet**. It does not declare the `INTERNET`
  permission, and a build-time check fails if that ever changes.
- There are **no ads, no analytics, no crash reporting, no accounts**, and no
  third-party SDKs that could collect anything.
- Your mixes, settings and recordings are stored **only on your device**.
- Cloud backup is **disabled**. You can still move everything to a new phone
  with a cable (device-to-device transfer) or via manual export.
- The microphone is used **only if you enable** cry-activated restart or the
  recorder. Cry detection runs **entirely on the device**: audio is analyzed
  in a small in-memory buffer and is never written to disk or sent anywhere.
  A visible indicator appears while the microphone is in use.

## What is stored on your device

| Data | Where | Leaves the device? |
|---|---|---|
| Mixes, favorites, settings | App-private storage | Only if you export/share them yourself |
| Imported/recorded sounds | App-private storage | Only if you export them yourself |
| Cry-monitor audio | Never stored (in-memory only) | Never |

## Permissions

| Permission | Why |
|---|---|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing white noise with the screen off |
| `RECORD_AUDIO` | Optional: cry-activated restart and voice recording (opt-in) |
| `POST_NOTIFICATIONS` | Optional: playback and listening notifications (opt-in on Android 13+) |
| `WAKE_LOCK` | Keep playback stable overnight |
| `FOREGROUND_SERVICE_MICROPHONE` | Optional: keeps the cry monitor alive in the background |

No location, no contacts, no storage permissions (files are accessed only
through the system file picker), and — again — no internet.
