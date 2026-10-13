# MOTION.md — Kuro motion system (single source of truth)

All animation goes through `MotionTokens` (`ui/theme/Motion.kt`). No one-off durations,
easings, or transition specs in screens.

## Token table

| Token | Value | Use |
|---|---|---|
| `FAST_MS` | 150ms | Small fades, chip fades |
| `MEDIUM_MS` | 300ms | Reserved |
| `SLOW_MS` | 450ms | Reserved (not currently used by any transition) |
| `REDUCED_MS` | 100ms | Every animation under Reduce motion (alpha only) |
| `TAB_MS` | 180ms | Tab switches (160-200ms range) |
| `TAB_SLIDE_DP` | 24dp | Tab switch slide (navigation direction) |
| `INDICATOR_MS` | 200ms | Bottom-nav pill glide (tween) |
| `PUSH_ENTER_MS` | 250ms | Detail / Add-clone enter (fade + 24dp slide up) |
| `PUSH_EXIT_MS` | instant | Detail / Add-clone exit is instant (no blend) |
| `POP_ENTER_MS` | 250ms | Pop enter (fade) |
| `POP_EXIT_MS` | instant | Pop exit is instant (no blend) |
| `DIALOG_MS` | 180ms | Dialogs |
| `LIST_FADE_MS` | 120ms | First-load whole-list fade (whole list only) |
| `PRESS_SCALE` | 0.98 | Card / quick-action / button press |
| `DIALOG_ENTER_SCALE` | 0.92 → 1.0 | Dialogs |
| `PUSH_ENTER_SLIDE` | 24dp | Detail push slide-up |
| `Standard` | cubic(0.2, 0, 0, 1.0) | Push movement |
| `Emphasized` | cubic(0.05, 0.7, 0.1, 1.0) | Tab slide, sheet motion |
| `pressSpring()` | spring(0.8, 700) | Card / button press-release |

## Transition contract

### Tabs (Step 3c: zero ghosting)
`ShellScreen` keeps **no** `AnimatedContent` around the tab host (tabs are not
separate NavHost destinations). Old tab is removed the same frame (plain `when`);
only the entering content animates: alpha 0->1 + translationX 24dp from the
navigation direction, 180ms, `Emphasized`, via `graphicsLayer` only.
**No scale anywhere in tab transitions. Zero blended frames.**
Direction is `ShellViewModel.direction` (+1 forward / -1 back) derived from
`BottomDestination.ordinal` comparison, so Home → Settings slides right-to-left
and back. Reduce motion: 100ms alpha only, no slide.

Tab state survival (D1):
- Each tab’s `LazyListState` is owned by `ShellViewModel` (`listStateFor`), so scroll
  position and scroll context survive tab switches instantly.
- Each tab’s data/filters live in its own ViewModel; revisiting a tab never reloads
  Room or PackageManager, and app icons come from the repository LRU cache.
- The per-tab first-load fade (120ms, whole list, once) is gated by `seenTabs` in
  `ShellViewModel`, so returning to a visited tab replays nothing.
- **No per-item stagger and no `animateItemPlacement` anywhere.** Replays and fling
  jumps both traced to those; they are removed.
- Every tab root paints an opaque `background` color, so two screens can never blend.

### Push / pop (Add Clone, Details, Diagnostics)
Enter = fade + 24dp slide up, 250ms, `Standard`. Exit = instant (`ExitTransition.None`).
Pop enter = fade 250ms, pop exit = instant. No shared elements, no matched geometry,
no morphing bounds anywhere in the codebase: clone cards and detail headers render
identical letter/color avatars independently, so nothing flies.
Reduce motion: 100ms fade only.

### Dialogs and sheets
- `KuroDialog` = `Dialog` + `Card` with scale 0.92 → 1 + fade, 180ms.
- `KuroBottomSheet` = Material 3 `ModalBottomSheet` built-in emphasized slide + scrim.

### Bottom bar (Step 3c rebuild)
One pill instance is drawn in `KuroBottomBar` (64dp × 32dp, fully rounded,
`surfaceContainerHigh`, never brown). Width = barWidth/4*0.6 (fixed 64dp).
Offset = cellWidth * index + (cellWidth - pillWidth)/2, so at rest it is exactly
centered under its tab (verified via `onGloballyPositioned`). Animation is
translationX via `graphicsLayer`, tween 200ms (100ms under reduce motion).
Selected icon/label use primary-toned contrast. Bar is `surfaceContainer` with a
1dp `outlineVariant` hairline top border. No per-tab backgrounds toggling on/off.

### FAB
Always "New Clone".

## Layer discipline

- Only `alpha` and `translation` (translationX/translationY) animate during navigation,
  tab switches, and scroll. `scale` only on press feedback (0.98) and dialogs.
- Never animate layout offsets, padding, size, or arrangement during motion — that is a
  blocking defect.
- Every screen root paints an opaque `background` color (root layout + Scaffold +
  AppShell content Box), so two screens can never blend during or after a transition.
- App icons: `RealInstalledAppRepository` holds an `LruCache` (120 entries), and
  `RealAppIcon` loads through it during composition, so fling never touches disk.

## Reduce motion

Everything collapses to 100ms alpha-only fades (tabs fade only, push/pop fade only,
dialogs fade only, list fades instant, presses instant with scale 1.0).

## Home engine chip (FROZEN — vendor-neutral)

Home header shows a small static chip reflecting engine state (Ready / Error /
Not compatible / Not initialized; "Loading" only while `init()` is actually running),
with a 150ms fade on change. No permanent spinner anywhere in the app.
UI must not name the engine vendor.
