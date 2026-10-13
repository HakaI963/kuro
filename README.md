# Kuro — Step 1: Modern UI Foundation

Kuro is a modern app-cloner / sandbox-style utility concept. Step 1 builds a complete,
production-quality UI foundation with mock data only.

> No real cloning, virtualization, APK injection, package manipulation, root, exploits,
> or runtime behavior is implemented in this step. All flows are simulated with in-memory data.

## What Step 1 includes

- Premium dark-first Material 3 design system (`KuroTheme`, tokens, spacing, motion)
- App shell with bottom navigation (Home, Library, Activity, Settings) + FAB (New Clone)
- Splash with animated entry
- 3-page onboarding (skip / get started)
- Home dashboard: greeting, search, quick actions, recent clones, active status, storage summary
- Add Clone multi-step mock flow: Source → Options → Progress/Success/Error
- Clone details: header, status, actions (Open mock, Freeze, Settings, Delete), overview, activity preview
- Library: search, filter chips (All/Running/Frozen/Private), long-press action sheet
- Activity: timeline feed (mock)
- Settings: appearance, behavior, security (mock), about
- Reusable components, empty/loading/error states, skeleton shimmer, reduce-motion support
- Manual DI via `ServiceLocator`, `StateFlow` + `ViewModel`, Navigation Compose

## Tech

- Kotlin 2.0.21, AGP 8.7.3, Gradle 8.10.2, Java 17
- compileSdk 35, targetSdk 35, minSdk 26
- Jetpack Compose (BOM 2024.11.00), Material 3, Navigation Compose 2.8.4
- No Hilt, Room, KSP, WorkManager, Firebase, ads, analytics, backend

## How to build (local)

```bash
# Requires Android SDK + JDK 17
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

If you have the Gradle wrapper jar, `./gradlew assembleDebug` also works.
This repo ships only `gradle-wrapper.properties` (no binary) to keep the repo clean;
CI installs Gradle directly.

## How to run workflow

1. Open GitHub → Actions → **Android Build** → **Run workflow**
2. Workflow (`/.github/workflows/android-build.yml`):
   - `ubuntu-latest`, checkout, JDK 17 (Temurin), Android SDK setup
   - Installs platform 35 + build-tools 35.0.0
   - Installs Gradle 8.10.2 directly (no wrapper binary needed)
   - Runs `gradle assembleDebug`
   - Uploads `kuro-debug-apk` artifact

## Current scope

- UI/UX foundation only, mock repositories in `data/mock/`
- All buttons navigate, change state, or show a polished mock response
- Strings in `res/values/strings.xml`, no lorem ipsum, no TODO/FIXME

## Next steps (Step 2+)

- Real data layer, persistence, clone execution contracts
- Runtime isolation design, security review
- Instrumentation, accessibility audit, performance pass
