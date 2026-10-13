# ENGINE.md — Kuro real BlackBox engine integration

## Chosen engine

| Field | Value |
|---|---|
| Primary repo | `FBlackBox/BlackBox` (upstream; content removed by its owner — README only) |
| Maintained fork integrated | `ALEX5402/NewBlackbox` |
| Upstream commit vendored | `89b59836c66f173756a4ae258cf379a957649820` |
| License | Apache License 2.0 (`third_party/NewBlackbox/LICENSE`) |
| Supported Android range | Android 5.0–14.0+ (API 21+), ARM64/ARMv7/x86 |
| Kuro minSdk | 26, so the sub-26 `Incompatible` path is unreachable in practice |

Why this fork over VirtualApp (`asLody/VirtualApp`): the VirtualApp community
source is stale and its full current source requires a purchased business license,
which is incompatible with frictionless open-source integration. Kuro ships no
`LICENSE` file of its own, so Apache-2.0 code with attribution is compatible.
No VirtualApp code is used anywhere.

## Vendoring layout (nothing rewritten, only compile patches)

- `third_party/NewBlackbox/Bcore` → Gradle module `:blackbox` (core library:
  Java + AIDL + native `cpp/` via `ndkBuild`, proxy manifest with stub components).
- `third_party/NewBlackbox/black-reflection` → `:blackbox-reflection` (pure Java).
- `third_party/NewBlackbox/compiler` → `:blackbox-compiler` (annotation processor).
- Upstream sample `app/` is NOT vendored.

Patches vs upstream (each marked `KURO-PATCH` in place):
1. `Bcore/build.gradle`: `rootProject.ext` literals (`compileSdk 35`, `minSdk 21`),
   Java 21 → 17 (no Java-21 APIs in the tree, verified by grep),
   `ndkVersion` 29.0.13846066 → 27.0.12077973 (matches CI),
   `lintOptions {}` → `lint {}` (removed in AGP 8),
   `packagingOptions {}` → `packaging {}` (AGP 8 rename),
   `:black-reflection`/`:compiler` → `:blackbox-reflection`/`:blackbox-compiler`.
2. Nothing else. No Java/C++ source file is modified.

## Initialization (mirrors the engine's own sample app)

Upstream sample (`App.kt` → `AppManager` → `BlackBoxLoader`):
`BlackBoxCore.get().doAttachBaseContext(context, ClientConfiguration {
getHostPackageName() = packageName, ... })` in `attachBaseContext`,
`BlackBoxCore.get().doCreate()` in `onCreate`.

Kuro does exactly this in `KuroApplication` via flavor-resolved `EngineBootstrap`
(`app/src/virtual/...` delegates to `BlackBoxBootstrap` in `:engine-blackbox`;
`app/src/stub/...` is a no-op), including the sample's `closeCodeInit()` call.
Failures are captured, never crash, and surface as `EngineStatus.Error`.

## Adapter (`BlackBoxEngine : VirtualEngine`, `:engine-blackbox`)

The only place UI meets the engine. One Kuro clone = one engine user slot
(`allocateUserSlot` skips slot 0; `cloneId` is `"u<userId>"`; Room stores the
mapping in `CloneRecord.engineUserId` — metadata only).

- `installIntoSpace(InstalledApp)`: `createUser(slot)` →
  `BlackBoxCore.installPackageAsUser(packageName, slot)` (reads the host
  `ApplicationInfo.sourceDir` itself) → verify `isInstalled` → `Done`.
  Progress events fire at these real checkpoints; there are no timers.
  `ApkFile` honestly fails as a later stage.
- `launch`: `BlackBoxCore.launchApk(package, slot)` → `Started` opens the guest
  UI with nothing intercepting it; `false`/exception → `Failed` with the real
  outcome, shown in a snackbar.
- `listSpacePackages`: `getUsers()` + `getInstalledPackages(0, user)` — the
  source of truth for ACTIVE (present) vs ERROR (Room record without engine
  backing). Room reconciliation happens in `RoomCloneRepository`.
- `remove`: `uninstallPackageAsUser` + `deleteUser`, verified by `isInstalled`.

## Flavors

- `stub`: no `:blackbox` dependency; `NoOpEngine` with honest `Incompatible`
  status; Create disabled; diagnostics explains the stub.
- `virtual`: `:blackbox` + real adapter. CI builds and uploads both APKs.

## Known limits (expected behavior)

- Apps with virtualization/root detection may refuse to run inside the engine.
- Guest apps needing Google Play Services need GMS installation (not bundled).
- Some games/heavy apps may crash; engine behavior varies by Android version
  (fork targets 14.0+, best effort on 15).
- The `:blackbox` manifest merges ~150 proxy components (activities/services/
  providers in `:p0`… processes) plus baseline permissions (no
  `QUERY_ALL_PACKAGES`); this is how the engine hosts guests.
- Force-stopping Kuro kills guest processes (they are Kuro `:pN` processes).

## Device verification (definition of done)

1. Clone a test app: system Settings → Apps must NOT list it; only Kuro's storage
   grows (~APK size, visible in Settings → Engine Diagnostics).
2. Open: the guest app's real UI launches; no Kuro dialog intercepts.
3. Force-stop Kuro: the running clone dies with it.
4. Diagnostics lists the clone as reported by `getUsers`/`getInstalledPackages`.
5. Delete: engine space removed, storage freed, engine list empty.
