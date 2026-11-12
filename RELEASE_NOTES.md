# Ame v0.1.4

Dynamic 120Hz scrolling performance, zero-jank startup pipeline, in-app update channel switching (Stable & Beta), hardware audio effects & reverb engine, and Feral UI sky shader.

## Mobile (Android)

| Build | File |
|---|---|
| Universal (recommended) | `ame-0.1.4.apk` |
| arm64-v8a | `ame-0.1.4_arm64-v8a.apk` |
| armeabi-v7a | `ame-0.1.4_armeabi-v7a.apk` |
| x86_64 (emulator / Chromebook) | `ame-0.1.4_x86_64.apk` |

## Android TV

| Build | File |
|---|---|
| Universal (recommended) | `ame-tv-0.1.4.apk` |
| arm64-v8a | `ame-tv-0.1.4_arm64-v8a.apk` |
| armeabi-v7a | `ame-tv-0.1.4_armeabi-v7a.apk` |
| x86_64 (emulator) | `ame-tv-0.1.4_x86_64.apk` |

## What's Changed

### ⚡ 120Hz High-Refresh Scrolling & Zero-Jank Startup
- **Asynchronous Disk & Cache Initialization:** Moved Downloads and Search History parsing off the main thread to background coroutines, unblocking cold startup to the very first frame.
- **Compose List Recycling & Allocation Pruning:** Added explicit `contentType` and stable keys to LazyColumn and LazyRow items across Home, Library, and Explore screens to reuse composition slots. Constant-pooled repeated `RoundedCornerShape` instances to eliminate GC churn during fast flings.
- **Compose Stability Contract:** Implemented `compose_stability_config.conf` declaring multi-module models and collections as stable, enabling Compose to skip unnecessary recompositions across the entire UI tree.
- **Recomposition Boundary Tightening:** Scoped playback quality caps within search callbacks and wrapped sort state with `derivedStateOf` to prevent full app root recompositions.

### 🔄 In-App Updates & Release Channels
- **Release Channel Switching:** Toggle between **Stable** (verified daily builds) and **Beta** (bleeding-edge preview builds) directly within Settings.
- **Direct In-App Updates:** Check for updates, inspect full release notes/changelogs, download APKs in the background with progress tracking, and trigger automatic package installer handoffs.

### 🎛️ Audio Effects & Reverb Engine
- **Hardware Audio Capabilities:** Dynamic detection of device audio hardware capabilities (Dolby Atmos, high-resolution 32-bit float PCM output).
- **Audio Effects Panel & Presets:** Dedicated hardware audio effects control panel with custom acoustic reverb presets and tuning.

### 🌌 Dynamic Feral UI Sky Shader
- Added dynamic sky shader backdrop with hardware AGSL rendering on modern Android devices and graceful JVM fallback.
