# Kuro — Stage 1: In-App Virtual Engine

Kuro runs clones inside its own virtual space. Cloning is Stage 1 of a larger
product plan; this step is intentionally minimal and extensible.

> No work profiles, no device admin, no root, no Xposed/LSPosed, no custom hooking
> framework, no `QUERY_ALL_PACKAGES`. See `docs/ENGINE.md`.

## What Stage 1 includes

- Zero mocks, zero placeholders. Fresh installs show polished empty states.
- Real installed-app list via `PackageManager.queryIntentActivities` (LAUNCHER) +
  `<queries>` block. Icons cached in an in-memory LRU cache, loaded off-main.
- `:engine` module with the future-proof `VirtualEngine` interface
  (`status`, `init`, `installIntoSpace`, `launch`, `listSpacePackages`, `remove`;
  `CloneSource = InstalledApp | ApkFile`, APK-file staged for later).
  UI talks only to this interface. `NoOpEngine` keeps the app crash-free.
- `:engine-blackbox` thin adapter on the vendored BlackBox core (Apache-2.0
  upstream — only build-system patches, no source rewrites): one Kuro clone =
  one engine user slot; real `installPackageAsUser` / `launchApk` /
  `getInstalledPackages` / `uninstallPackageAsUser` calls.
- Room metadata `CloneRecord(cloneId, packageName, cloneName, colorTag, createdAt,
  engineName)` + activity log.
- UI in Step 1 design language: Home (real clones, running state), New clone
  (Source = real apps; Options = name + color only; Create = real engine progress),
  tap = launch, long-press = Delete only, Settings engine card (name, version,
  supported range, status).
- Flavors: `stub` (NoOp, always builds) + `virtual` (real BlackBox engine).
- Unit tests for engine gating and record mapping.

## Tech

- Kotlin 2.0.21, AGP 8.7.3, Gradle 8.10.2, Java 17, NDK/CMake (workflow-cached)
- compileSdk 35, targetSdk 35, minSdk 26
- Jetpack Compose (BOM 2024.11.00), Material 3, Navigation Compose 2.8.4
- Room 2.6.1 (KSP 2.0.21-1.0.25), Coroutines 1.8.1, JUnit 4

## How to build

```bash
# Requires Android SDK + JDK 17 (NDK used only by future native backends)
gradle assembleStubDebug assembleVirtualDebug
gradle testDebugUnitTest
```

## How to run workflow

GitHub → Actions → **Android Build** → **Run workflow**. It installs the NDK/CMake
(cached), runs unit tests, builds both flavors, and uploads both APKs.

## Roadmap

See `docs/ROADMAP.md`. Stage 1 is done; later stages are placeholders.
Engine swap notes live in `docs/ENGINE.md`.
