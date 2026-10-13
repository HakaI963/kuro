# MOTION.md — Kuro motion system (single source of truth)

All animation goes through `MotionTokens` (`ui/theme/Motion.kt`). No one-off durations,
easings, or transition specs in screens.

## Token table

| Token | Value | Use |
|---|---|---|
| `FAST_MS` | 150ms | Small fades, exit carries, chip fades |
| `MEDIUM_MS` | 300ms | Push enters |
| `SLOW_MS` | 450ms | Reserved (not currently used by any transition) |
| `REDUCED_MS` | 100–120ms | Every animation under Reduce motion |
| `TAB_MS` | 220ms | Tab switches |
| `TAB_SLIDE_DP` | 20dp | Tab switch slide (navigation direction) |
| `PUSH_ENTER_MS` | 300ms | Detail / Add-clone enter |
| `PUSH_EXIT_MS` | 250ms | Detail / Add-clone exit |
| `POP_ENTER_MS` | 250ms | Pop enter |
| `POP_EXIT_MS` | 275ms | Pop exit |
| `DIALOG_MS` | 180ms | Dialogs |
| `LIST_FADE_MS` | 120ms | First-load whole-list fade (whole list only) |
| `PRESS_SCALE` | 0.98 | Card / quick-action press |
| `DIALOG_ENTER_SCALE` | 0.92 → 1.0 | Dialogs |
| `PUSH_ENTER_SLIDE` | 24dp | Detail push slide-up |
| `Standard` | cubic(0.2, 0, 0, 1.0) | General movement |
| `Emphasized` | cubic(0.05, 0.7, 0.1, 1.0) | Tab slide, sheet motion |
| `interactiveSpring()` | spring(0.75, 350) | Bottom-bar pill, press feedback |
| `pressSpring()` | spring(0.8, 700) | Card / button press-release |

## Transition contract

### Tabs
`ShellScreen` keeps **one** `AnimatedContent` around the tab host (tabs are not
separate NavHost destinations). Spec: horizontal slide of 20dp in the navigation
direction + fade, 220ms, `Emphasized`, via `slideInHorizontally` / `slideOutHorizontally`
(translationX + alpha; layer-backed). **No scale anywhere in tab transitions.**
Direction is derived from `BottomDestination.ordinal` comparison, so Home → Settings
slides right-to-left and back.

Tab state survival (D1):
- Each tab’s `LazyListState` is owned by `ShellViewModel` (`listStateFor`), so scroll
  position and scroll context survive tab switches instantly.
- Each tab’s data/filters live in its own ViewModel; revisiting a tab never reloads
  Room or PackageManager, and app icons come from the repository LRU cache.
- The per-tab first-load fade (120ms, whole list, once) is gated by `seenTabs` in
  `ShellViewModel`, so returning to a visited tab replays nothing.
- **No per-item stagger and no `animateItemPlacement` anywhere.** Replays and fling
  jumps both traced to those; they are removed.

### Push / pop (Add Clone, Details, Diagnostics)
Enter = fade + 24dp slide up, 300ms, `Standard`. Exit = fade 250ms. Pop reverses.
No shared elements, no matched geometry, no morphing bounds anywhere in the codebase
(D2): clone cards and detail headers render identical letter/color avatars
independently, so nothing flies.

### Dialogs and sheets
- `KuroDialog` = `Dialog` + `Card` with scale 0.92 → 1 + fade, 180ms.
- `KuroBottomSheet` = Material 3 `ModalBottomSheet` built-in emphasized slide + scrim.

### Bottom bar
One pill instance is drawn in `KuroBottomBar`; its x offset is animated in `AppShell`
with `interactiveSpring` (`bottomBarPillOffset`). Icon/label cross-fade with slight
scale inside `AnimatedContent`. No per-tab backgrounds toggling on/off.

### FAB
Always "New Clone"; spring press-scale (see `AppShell`) with icon cross-fade retained.

## Layer discipline

- Only `alpha` and `translation` (translationX/translationY) animate during navigation,
  tab switches, and scroll. `scale` only on press feedback and dialogs.
- Never animate layout offsets, padding, size, or arrangement during motion — that is a
  blocking defect (D3).
- Every screen root paints an opaque `background` color (root layout + Scaffold +
  AppShell content Box), so two screens can never blend during or after a transition.
- App icons: `RealInstalledAppRepository` holds an `LruCache` (120 entries), and
  `RealAppIcon` loads through it during composition, so fling never touches disk.

## Reduce motion

Everything collapses to ≤120ms fades (tabs fade only, push/pop fade only, dialogs fade
only, list fades instant, presses instant).

## Home engine chip

Home header shows a small static chip reflecting engine state (Ready / Error /
Not compatible / Not initialized; "Loading" only while `init()` is actually running),
with a 150ms fade on change. No permanent spinner anywhere in the app.
