# Contributing to Ame

Thanks for your interest in Ame. This guide covers how to build the project,
run its tests and submit a change that is easy to review.

## Before you start

- **Small fixes and documentation** can go straight to a pull request.
- **Substantial features, refactors or UI changes** should be discussed in an
  issue first, so the approach is agreed before the work is done.
- **Security issues** should not be reported in a public issue. See
  [SECURITY.md](SECURITY.md).

## Prerequisites

| Tool | Version / notes |
|---|---|
| JDK | 17 (Eclipse Temurin 17 recommended). Newer JDKs may not be supported by the Android Gradle Plugin. |
| Android SDK | `compileSdk = 37`, `targetSdk = 36`, `minSdk = 26` |
| Android NDK + CMake | CMake 3.22.1 or newer, for the native DSP modules under `app/src/main/cpp` |
| Go | 1.27 or newer, for the Listen Together server in `backend/` |
| Node.js | 22 or newer, for the web player and extension in `web/` |
| pnpm | The version declared in `web/package.json`; Corepack selects it automatically |

## Get the source

```bash
git clone <your-fork-url>
cd BitChord
```

Create `local.properties` in the repository root with the path to your Android
SDK. It is gitignored and must never be committed:

```properties
sdk.dir=/path/to/Android/Sdk
```

Release signing is optional for development. The real `keystore.properties` and
the `.jks` file are gitignored; use
[keystore.properties.example](keystore.properties.example) as the template if
you are producing a signed build. Read the secrets rules in
[SECURITY.md](SECURITY.md) before adding any credential.

## Build and test

Run Gradle commands from the repository root.

### Android app

```bash
./gradlew assembleDevDebug
./gradlew testDevDebugUnitTest
```

Run a single test:

```bash
./gradlew testDevDebugUnitTest --tests "com.abhishek.ame.music.playback.audio.DirectAudioStreamingRegressionTest"
```

On Windows use `gradlew.bat` in place of `./gradlew`.

### Android TV

```bash
./gradlew :tv:assembleDevDebug
```

### Web player and extension

```bash
cd web
corepack enable
pnpm install --frozen-lockfile
pnpm typecheck
pnpm test
pnpm build
```

### Listen Together server

```bash
cd backend
go build ./...
go vet ./...
go test ./...
```

## Code guidelines

- Follow idiomatic Kotlin and Jetpack Compose conventions, and the architecture
  already in the module you are editing.
- Keep diffs focused. Do not mix unrelated refactors, formatting changes or
  dependency upgrades into a functional fix.
- Add or update tests for new logic and edge cases. Do not leave the suite with
  new failures; mention any pre-existing failure in the pull request.
- Avoid new third-party dependencies unless they are necessary. Any new
  dependency must be licence-compatible with GPLv3 and worth its size.
- Do not commit generated output, build caches, API keys, keystores or `.env`
  files. The pre-commit hook and CI will reject them.

## Audio and DSP changes

For audio changes, distinguish clearly between source metadata, decoder format,
internal DSP format, AudioTrack output, AudioFlinger/HAL state, advertised
device capability and the behaviour of the physical endpoint. Application
telemetry is not proof of hardware behaviour unless that behaviour was actually
verified on the hardware.

## Commit and pull request

1. Fork the repository and create a descriptive branch, for example
   `fix/streaming-buffer` or `docs/addon-guide`.
2. Keep the branch reasonably up to date with the target branch.
3. Write a clear pull request description: what changed, why, and how it was
   tested.
4. Keep review discussion focused on the code and the problem.
5. A pull request needs approval from a maintainer before it is merged.

## Licensing and attribution

Ame is licensed under the **GNU General Public License v3.0 (GPLv3)**. By
contributing, you agree that your contribution is provided under the same
licence.

Ame is a rebranded derivative of [BitChord](https://github.com/kushagrasinghx/BitChord)
by Kushagra Singh, used under the GPLv3. Keep the upstream copyright notices
and licence attribution intact when modifying files. Removing them is a
licence violation, not a cleanup.

## Release assets

The in-app updater finds assets by name, so keep this scheme when publishing:

- Phone universal: `ame-<version>.apk`
- Phone per-ABI: `ame-<version>_<abi>.apk`
- TV universal: `ame-tv-<version>.apk`
- TV per-ABI: `ame-tv-<version>_<abi>.apk`

The phone APK is the primary release asset. TV assets use the `ame-tv-` prefix.

## Questions

Open a GitHub issue for build questions, or read
[ADDITIONAL.md](ADDITIONAL.md) for the rest of the project documentation.
