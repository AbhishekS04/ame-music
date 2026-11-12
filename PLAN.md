# BitChord Performance & Latency Optimization Plan (Dev Build Focus)

## 1. Context & Objectives
- **Target:** Galaxy S24 FE / S21 FE running `com.abhishek.ame.music.dev`
- **Goal:** Maximize UI frame rates (120Hz smooth scrolling), eliminate startup delays, prevent main-thread jank, and minimize recomposition overhead without breaking liquid glass, Haze, or predictive navigation.

## 2. Technology Stack & Versions
- Kotlin: 2.3.20 (Compose Compiler Plugin: 2.3.20)
- Compose Foundation: 1.10.0 (BOM 2024.12.01)
- AGP: 8.10.1 / R8: 8.13.23
- Media3: 1.11.0
- Coil 3: 3.0.4
- Haze: 1.3.1
- CompileSdk: 37 / TargetSdk: 36 / MinSdk: 26

## 3. Implemented Optimizations (Completed & Verified)

### Chunk 1: Startup Pipeline & Asynchronous Initialization [COMPLETED]
- Moved heavy disk parsing and JSON deserialization in `Downloads.init` and `SearchHistory.init` from the main thread during `AmeApplication.onCreate()` to background coroutines (`Dispatchers.IO`), unblocking cold start to the first frame.

### Chunk 2: Compose List Recycling (`contentType` & Keys) & Allocation Pruning [COMPLETED]
- Added explicit `contentType` and stable `key` to all `LazyColumn` and `LazyRow` items across `HomeScreen`, `LibraryScreen`, and `ExploreScreen` so Compose's layout manager reuses composition slots rather than discarding and allocating them during scroll.
- Constant-pooled repeated `RoundedCornerShape` instances (`TrackThumbShape`, `ShelfCardShape`, `HeroCardShape`) to eliminate GC churn during fast scrolling.

### Chunk 3: Compose Stability Contract (`compose_stability_config.conf`) [COMPLETED]
- Created `compose_stability_config.conf` declaring multi-module models (`com.abhishek.ame.music.data.model.**`, etc.) and standard collections as stable, enabling the Compose compiler to skip recompositions across the entire UI tree.
- Configured `composeCompiler.stabilityConfigurationFiles` in both `app/build.gradle.kts` and `sharedUi/build.gradle.kts`.

### Chunk 4: Recomposition Boundary Tightening in `AmeApp` [COMPLETED]
- Removed root-level collection of playback quality caps (`audioQualityWifi`, `audioQualityCellular`, etc.) in `MainActivity.kt`, scoping `addonCeiling` directly inside the search callback.
- Wrapped `songSort` with `derivedStateOf` to prevent full `AmeApp` invalidation on sort changes.

### Chunk 5: ART Speed Compilation & Dev Build Native Performance [COMPLETED]
- Updated `verifyDevInstall` in `app/build.gradle.kts` from `-m verify` to `-m speed` so that any dev install compiles dex bytecode directly into native ARM64 machine instructions.
- AOT compiled package on connected device (`dumpsys package dexopt` reports `base.odex` compiled with `speed`).
- Verified via `dumpsys gfxinfo`: 90th/95th/99th GPU percentile is 3ms (comfortably within 120Hz 8.3ms frame budget).
