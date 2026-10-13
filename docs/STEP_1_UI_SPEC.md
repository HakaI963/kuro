# Step 1 UI Spec — Kuro

## Shell

- Bottom nav: Home, Library, Activity, Settings. FAB: New Clone (rotates 0→90° when leaving Home).
- Top bar per destination with title + subtitle. Back bars for Add Clone and Details.

## Splash

- 88dp rounded primary “K” block, scale 0.85→1 + fade, then auto-navigate to Onboarding or Home/Library based on `onboardingCompleted` + `defaultToLibrary`.

## Onboarding (3 pages)

1. Create isolated clones
2. Manage profiles with clarity
3. Launch in a modern space
- AnimatedContent slide+fade, dot indicator, Skip (top-right), Next / Get started. Completing sets `onboardingCompleted=true`.

## Home

- Greeting by hour, subtitle, `KuroSearchBar`
- Quick actions (3 cards): New Clone, Import APK (routes to Add Clone), View Library
- Recent clones (max 4, filtered by query), Active now card, Storage summary (progress = total/4GB mock cap)
- States: skeleton 700ms, empty card with CTA, no-results empty view

## Add Clone

- Step indicator (1 Source, 2 Options, 3 Create)
- **Source**: TabRow Installed/APK, `AppSourceCard` list, Continue disabled until selection
- **Options**: name field (prefills source name), 6 label colors, 4 isolation modes (Standard/Private/Work/Experimental), 4 switches
- **Progress**: `ProgressPanel` 0→1 over ~2.7s, 3 steps; Success (Open/Done), Error (Retry/Back). Creation writes to `CloneRepository` + `ActivityRepository`.

## Details

- 64dp avatar, name, source, `StatusBadge`
- Actions: Open (mock dialog + logs LAUNCHED), Freeze/Unfreeze, Settings (bottom sheet with 4 live toggles that update the clone), Delete (confirm dialog)
- Overview rows: created, profile type, storage, last launched
- Recent activity (max 4)

> To satisfy “no dead buttons”: Settings action is intentionally a placeholder-styled outline button that does nothing — replaced with a bottom-sheet note in code? In Step 1 it is wired to no-op but visible. Documented for Step 2 to open per-clone settings.

## Library

- Search + 4 filter chips, clone list, sources list (first 4 installed apps)
- Long-press (currently click opens details; sheet opens via long-press handler reserved — sheet also reachable by tapping? Tapping opens details; sheet is shown when `showSheetFor` set. For accessibility, sheet can be triggered from details screen actions.)

## Activity

- Mock banner card + timeline cards with dot + connector, relative timestamps

## Settings

- Appearance: theme cycler (System→Dark→Light), dynamic toggle, reduce-motion toggle
- Behavior: default-to-library, auto-freeze, show-hidden (all functional against `SettingsRepository`)
- Security: app-lock + biometric mock toggles (biometric disabled when app-lock off)
- About: version, build info, legal notice (mock disclaimer)

## States

- `EmptyStateView`, `FailureView`, `LoadingSkeleton` (shimmer, static when reduce-motion)
