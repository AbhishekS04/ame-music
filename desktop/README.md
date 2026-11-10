# Ame Desktop (Linux)

The Linux desktop player. Everything visible in the window comes from
`sharedUi`; everything it plays comes from `shared`. This module supplies only
what those two cannot: the window, the audio engine, and the platform interfaces
the shared UI reads.

## Requirements

| | |
|---|---|
| JDK | **21** (Temurin or Fedora's `java-21-openjdk`). Gradle 8.11.1 cannot build on 25. |
| ffmpeg | on `PATH` — the audio decoder |
| Android SDK | not needed for this module |

## Run

```bash
./gradlew :desktop:run
```

## Package

```bash
./gradlew :desktop:packageRpm
```

Writes `desktop/build/compose/binaries/main/rpm/ame-<version>-1.x86_64.rpm`. Install
with `sudo dnf install ./ame-0.1.1-1.x86_64.rpm`. The package registers a menu
entry, an icon, and the `ame://` scheme handler for party invites.

`./gradlew :desktop:packageAppImage` produces a single-file build instead, useful
for testing without installing.

## What works

- Home feed and search, live from YouTube Music over Innertube (the same client
  path the phone uses).
- Playing a track: `StreamResolver` resolves the URL, ffmpeg decodes to 48 kHz
  stereo PCM, Java Sound writes it to the output.
- Artwork (Coil with an OkHttp fetcher).

## What does not, yet

| Area | Why | Where it would go |
|---|---|---|
| Sign-in | No cookie source is wired; browsing is anonymous | `PlayerStore` — read a browser's cookie store, as the phone's WebView does |
| Queue, crossfade, EQ | `AudioEngine` plays one track at a time | needs a PCM mixer; `shared`'s `TransitionFilter` already does the DSP |
| Lyrics | The panel needs a provider chain run against the playing clock | `shared`'s lyrics package is complete; it needs wiring |
| Library | Needs a session | — |
| Discord / Listen Together | Not wired | `shared` has both |
| Animated canvas, music video | No video decoder | `PlayerHost.CanvasVideo` / `MusicVideo` |
| Settings UI | Values are the phone's defaults; two persist via `java.util.prefs` | `DesktopSettings` |
| Volume | Drives `MASTER_GAIN` when the mixer has one | PipeWire's own control would be better |

## Design notes

- **No Rust.** The data layer is 21k lines of Kotlin that already compiles for the
  JVM; rewriting it would discard the only thing that makes this feasible.
- **ffmpeg over a native binding** because gst1-java is not on Maven Central and
  vlcj pulls in libvlc. The shared UI has no decoder reference, so the only
  requirement is that PCM comes out.
- `sharedUi` pins Compose desktop `1.12.0` on Linux and `1.10.3` on Windows, for
  the skiko `Matrix33` reason documented in `sharedUi/build.gradle.kts`. This
  module resolves `compose.desktop.currentOs` and inherits that.
