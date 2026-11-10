# Ame v0.1.3

Official brand vector identity, fluid elastic drawer physics, high-fidelity share cards, and "Play audio first" playback mode with on-demand video conversion.

## Mobile (Android)

| Build | File |
|---|---|
| Universal (recommended) | `ame-0.1.3.apk` |
| arm64-v8a | `ame-0.1.3_arm64-v8a.apk` |
| armeabi-v7a | `ame-0.1.3_armeabi-v7a.apk` |
| x86_64 (emulator / Chromebook) | `ame-0.1.3_x86_64.apk` |

## Android TV

| Build | File |
|---|---|
| Universal (recommended) | `ame-tv-0.1.3.apk` |
| arm64-v8a | `ame-tv-0.1.3_arm64-v8a.apk` |
| armeabi-v7a | `ame-tv-0.1.3_armeabi-v7a.apk` |
| x86_64 (emulator) | `ame-tv-0.1.3_x86_64.apk` |

## What's Changed

### 🎨 Brand Identity & Vector Assets
- Reorganized brand assets into `/logo` with clean high-resolution wordmarks and vector SVGs.
- Converted Ame logo into native Android Vector Drawables (`ic_logo.xml`) for razor-sharp rendering on any screen density.
- Re-generated launcher icons across all mipmap densities (`mdpi` through `xxxhdpi`).
- Cleaned app icon selector to official default branding.
- Converted notification small icon to the crisp white Ame wordmark (Samsung Now Bar, lockscreen, AOD, and status bar).

### 📐 Elastic Bottom Sheet Physics (Vaul Curve)
- Redesigned player drawer drag mechanics: 1:1 finger tracking with velocity-seeded dismiss.
- Anchored sheet base (`TransformOrigin(0.5f, 1.0f)`) preventing black empty voids and gaps when pulled upward.
- Smooth elastic resistance when dragged upward beyond maximum height with a spring settle bounce.

### ✨ Vector Share Cards
- Lyrics share card now displays the standalone Ame vector wordmark with exact proportions.
- Replay story and stats poster cards optimized with 860:320 aspect ratio vector wordmarks without redundant plain-text labels.

### 🎵 "Play audio first" Mode & Instant Video Conversion
- Added dedicated **"Play audio first"** toggle in **Settings → Playback** (enabled by default).
- Prioritizes audio streams with 1:1 square music cover artwork across Home, Search, and Queue.
- Added instant on-demand **"Convert to video"** option in the 3-dots track menu to seamlessly switch to video playback on the fly.
- Added **"Convert to audio"** toggle in the track menu to revert back to audio mode anytime.

### 🌐 Web & Extension Improvements
- Skullet skeleton loading states aligned across home feed and navigation bar.
- Extension v1.0.0 released with amemusic.vercel.app routing and chunking improvements.
