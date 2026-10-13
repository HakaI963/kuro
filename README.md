# Kuro — Step 2: Real Work-Profile Engine Foundation

Kuro is a modern app-cloner utility. Step 2 removes all Step 1 placeholder data and
replaces the mock layer with a real, legitimate clone engine based on Android managed
work profiles (same mechanism as Island and Shelter).

> Clones are real installs inside the isolated space (work profile). No virtualization,
> hooks, reflection, Xposed, or root. See `docs/STEP_2_ENGINE.md`.

## What Step 2 includes

- Zero placeholder clones or fake apps. Fresh installs show polished empty states.
- Real installed-app list via `PackageManager.queryIntentActivities` (LAUNCHER) + `<queries>` block.
  No `QUERY_ALL_PACKAGES`. Icons cached in an in-memory LRU cache, loaded off-main with Flow + IO.
- Work-profile provisioning: `KuroDeviceAdminReceiver` + `ProvisioningManager`
  (NotSetup / ProfileExistsNotOwner / Active / Blocked with clear UI states).
- `CloneEngine` with `WorkProfileCloneEngine` (PackageInstaller sessions, `setPackagesSuspended`,
  `setApplicationHidden`, badged labels) and `UnavailableCloneEngine` safe no-op.
- Minimal strongly-typed cross-profile bridge (`CrossProfileBridge` + `CloneCommandReceiver`).
- Room persistence: `CloneRecord`, `ActivityRecord`, DAOs, `KuroDatabase`. All in-memory state replaced.
- Real install progress from `PackageInstaller` byte-copy + result broadcast. No fake timers.
- UI wiring: Home/Library/Activity from Room; Add Clone with real search + gated Create;
  Details with real Open/Freeze/Hide/Delete + snackbars; Settings engine status card + explainer.
- Unit tests for provisioning state machine and record mapping.
- Docs: `docs/STEP_2_ENGINE.md` (limitations + manual + adb steps).

## Tech

- Kotlin 2.0.21, AGP 8.7.3, Gradle 8.10.2, Java 17
- compileSdk 35, targetSdk 35, minSdk 26
- Jetpack Compose (BOM 2024.11.00), Material 3, Navigation Compose 2.8.4
- Room 2.6.1 (KSP 2.0.21-1.0.25), Coroutines 1.8.1, JUnit 4
- No Firebase, ads, analytics, backend. No `QUERY_ALL_PACKAGES`.

## How to build (local)

```bash
# Requires Android SDK + JDK 17
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
gradle testDebugUnitTest
```

CI installs Gradle directly (no wrapper binary in repo).

## How to run workflow

1. Open GitHub → Actions → **Android Build** → **Run workflow**
2. Workflow (`/.github/workflows/android-build.yml`):
   - `ubuntu-latest`, checkout, JDK 17 (Temurin), Android SDK setup
   - Installs platform 35 + build-tools 35.0.0
   - Installs Gradle 8.10.2 directly
   - Runs `gradle assembleDebug`
   - Uploads `kuro-debug-apk` artifact (compile only; engine needs a device)

## Current scope

- Real engine foundation, Room-backed, work-profile gated. Unavailable features show
  explicit disabled / setup-required states. Never fakes success or data.

## Roadmap

- Step 3: harden cross-profile transport (`setCrossProfilePackages` / provider),
  badged icons in lists, profile-pause handling, backup rules.
- Security review, accessibility audit, performance pass.
