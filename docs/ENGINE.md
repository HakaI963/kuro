# ENGINE.md — Kuro Stage 1 virtual engine

## Decision (recorded before integration)

| Option | Repo | License | Android range | Verdict |
|---|---|---|---|---|
| BlackBox (preferred) | `FBlackBox/BlackBox` | Apache License 2.0 (permissive; attribution only) | Android 5.0+ (API 21+); Kuro targets API 26–35 | **Chosen** |
| VirtualApp (fallback) | `asLody/VirtualApp` | Community source is stale/limited; full latest source requires a purchased business license | Claims up to Android 14 in business version | **Rejected** — commercial-license gate makes it unsuitable for this repo, which carries no license purchase |

Kuro itself currently ships no `LICENSE` file, so vendoring Apache-2.0 code with
attribution is compatible. No VirtualApp business-licensed code is used anywhere.

## What is vendored

- Nothing is copied from upstream in this step. `:engine-blackbox` is Kuro's own
  thin Stage 1 adapter implementing `:engine`'s `VirtualEngine` with a file-backed
  virtual space (`filesDir/virtualspace/<cloneId>` holding `base_<i>_<name>.apk`
  copies plus `clone.meta`). It is modified as little as possible *by construction*:
  it contains only adapter logic, so a future native-core drop-in touches one module.
- Upstream clone (for reference only, not vendored):
  `git clone https://github.com/FBlackBox/BlackBox.git` (Apache-2.0, see its `LICENSE`).

## Supported Android versions

- Upstream BlackBox: API 21+.
- Kuro Stage 1 adapter: API 26–35 (Kuro `minSdk 26`, `compileSdk/targetSdk 35`).
- Below API 26 the engine reports `EngineStatus.Incompatible` and the UI shows an
  honest status card: “Engine not compatible with Android X on this device”.
  The rest of the app stays fully usable.

## Known limits (expected behavior)

- Apps that detect virtualization may refuse to run. This is expected and surfaced
  as an honest launch failure, never a fake success.
- Stage 1 executes installs/copies, integrity-checked launch validation
  (`getPackageArchiveInfo` + package-name match), listing, and deletion.
  Full in-process APK execution is the next swap (see below).
- APK-file import (`CloneSource.ApkFile`) is defined in the interface but returns an
  explicit “later stage” failure in Stage 1.
- No root, no Xposed/LSPosed, no Magisk, no custom hooking framework, no
  `QUERY_ALL_PACKAGES`, no work profile / device admin anywhere.

## How to swap the engine behind `VirtualEngine` later

1. Add the new backend as a sibling module (e.g. `:engine-cedric`) implementing
   `com.kuro.engine.VirtualEngine` from `:engine`. Keep all backend imports inside
   that module — app UI must never import backend classes directly.
2. Point the `virtual` flavor at it: change `app/src/virtual/.../EngineProvider.kt`
   to return the new implementation. `stub` keeps returning `NoOpEngine`.
3. If the backend needs native code, add its `CMakeLists.txt` inside its own module
   and set `ndkVersion` there — never in `:app`. The workflow already installs and
   caches the NDK/CMake (see `.github/workflows/android-build.yml`).
4. Keep `CloneSource`, `CloneHandle`, `InstallEvent`, `LaunchResult`, and
   `EngineStatus` shapes stable; extend with new sealed variants instead of
   changing existing ones so Room records and UI keep working.
5. Update this file's decision table and the Settings card constants
   (`settings_engine_name/version/range`) in the same PR.

## Flavors

- `stub`: `:engine` only (`NoOpEngine`, status `Uninitialized`). Always builds,
  even without any backend. Use for CI smoke and incompatible devices.
- `virtual`: `:engine` + `:engine-blackbox` (Stage 1 file-backed adapter).
  This is the Stage 1 product. Both flavors are built in CI.
