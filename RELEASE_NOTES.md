# Ame v0.1.1

Privacy-first crash reports, Android TV and Android phone builds, and a more
reliable CI pipeline.

## Mobile (Android)

| Build | File |
|---|---|
| Universal (recommended) | `ame-0.1.1.apk` |
| arm64-v8a | `ame-0.1.1_arm64-v8a.apk` |
| armeabi-v7a | `ame-0.1.1_armeabi-v7a.apk` |
| x86_64 (emulator / Chromebook) | `ame-0.1.1_x86_64.apk` |

## Android TV

| Build | File |
|---|---|
| Universal (recommended) | `ame-tv-0.1.1.apk` |
| arm64-v8a | `ame-tv-0.1.1_arm64-v8a.apk` |
| armeabi-v7a | `ame-tv-0.1.1_armeabi-v7a.apk` |
| x86_64 (emulator) | `ame-tv-0.1.1_x86_64.apk` |

## Crash reports

Crash reports are **local only**. When Ame crashes, the next launch offers to
create a report containing:

- app version and package
- Android version and API level
- device manufacturer, model and ABI
- stack trace / native trace / ANR trace
- a best-effort logcat excerpt

Nothing is uploaded automatically. Tapping **Share** opens the system share
sheet, so the listener chooses where the report goes. Reports can also be
shared later from **Settings → Miscellaneous → Crash report**.

## Other changes

- Wear OS support has been removed from this repository.
- CI now downloads Gradle from a checksum-verified mirror instead of the
  GitHub distribution endpoint that was returning HTTP 504.
