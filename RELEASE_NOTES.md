# Ame v0.1.5

Dolby Atmos persistence fix on OEM devices, automatic audio conversion for "Play Audio First" with clean square minibar posters, instant settings-to-feed synchronization without pull-to-refresh, in-player "Add to Playlist" dialog positioning, and reliable in-app package installer handoff.

## Mobile (Android)

| Build | File |
|---|---|
| Universal (recommended) | `ame-0.1.5.apk` |
| arm64-v8a | `ame-0.1.5_arm64-v8a.apk` |
| armeabi-v7a | `ame-0.1.5_armeabi-v7a.apk` |
| x86_64 (emulator / Chromebook) | `ame-0.1.5_x86_64.apk` |

## Android TV

| Build | File |
|---|---|
| Universal (recommended) | `ame-tv-0.1.5.apk` |
| arm64-v8a | `ame-tv-0.1.5_arm64-v8a.apk` |
| armeabi-v7a | `ame-tv-0.1.5_armeabi-v7a.apk` |
| x86_64 (emulator) | `ame-tv-0.1.5_x86_64.apk` |

## What's Changed

### 🛠️ In-App Updater & In-Player Playlist Picker Fixes
- **Modern Package Installer Hand-off:** Fixed the in-app APK updater on Android 12+ / One UI where downloading succeeded but tapping "Install Now" failed to trigger the system update prompt. Replaced deprecated `ACTION_INSTALL_PACKAGE` and conflicting `EXTRA_RETURN_RESULT` flags with standard `ACTION_VIEW` and explicit URI read permissions.
- **In-Player "Add to Playlist" Sheet:** Fixed "Add to Playlist" from the full player's `⋯` menu opening in the background underneath the player. The playlist drawer now appears directly on the music playing screen with smooth animations, keyboard avoidance, and uninterrupted playback.

### 🔊 Dolby Atmos Stability & OEM DSP Decoupling
- **Decoupled Stream Preference from AOSP Spatializer:** Fixed a critical issue on Samsung and other Dolby Atmos-certified devices where a 3-second background polling loop mistakenly detected non-multichannel streams as inactive spatial audio and forcefully toggled off Dolby Atmos in the app.
- **Persistent User Preference:** Dolby Atmos stream selection is now strictly user-controlled, ensuring Atmos streams (E-AC-3 JOC) remain active without interference while Dirac and system equalizers remain fully supported.

### 🎵 "Play Audio First" Auto-Conversion & Minibar Fixes
- **Automatic Audio Version Resolution:** When "Play Audio First" is enabled, queued or played video tracks automatically resolve and swap to their official high-fidelity album release without requiring manual menu actions.
- **Clean Square Minibar Posters:** Fixed minibar poster presentation so audio tracks always maintain a crisp 1:1 aspect ratio and show official artwork instead of stretching or attempting to initialize an empty video surface.
- **Instant Feed Synchronization:** Toggling "Play Audio First" in Settings now instantly updates Home and Explore shelves without requiring a manual pull-to-refresh.

### 🔄 Clean Update Channel Logic (Stable vs. Beta)
- **Strict Channel Separation:** The Beta channel now exclusively tracks genuine pre-releases instead of falling back to the stable release.
- **Accurate Update Notifications:** Fixed the version comparison logic to only prompt for updates when a newer version exists, preventing erroneous "Update available" alerts when switching channels.
