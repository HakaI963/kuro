# MOTION.md — Kuro motion system (single source of truth)

All animation in the app goes through `MotionTokens` (`ui/theme/Motion.kt`).
Do not invent one-off durations, easings, or transition specs in screens.

## Token table

| Token | Value | Use |
|---|---|---|
| `FAST_MS` | 150ms | Exits, small fades, icon cross-fades |
| `MEDIUM_MS` | 300ms | Screen enters, dialog scale, stagger item fade |
| `SLOW_MS` | 450ms | Reserved for large celebratory motion |
| `REDUCED_MS` | 100–120ms | Every animation under Reduce motion |
| `INTRO_TOTAL_MS` | 1500ms | Cold-start orbital intro cap (skippable on tap) |
| `STAGGER_STEP_MS` | 12ms/item, max 12 items | One-time list entrance |
| `PRESS_SCALE` | 0.98 | Card/FAB press |
| `DIALOG_ENTER_SCALE` | 0.92 → 1.0 | Dialogs |
| `NAV_ENTER_SCALE` | 0.98 → 1.0 | Screen enters |
| `NAV_ENTER_SLIDE` | 24dp upward | Screen enters |
| `Standard` | cubic(0.2, 0, 0, 1.0) | General movement |
| `Emphasized` / `EmphasizedDecelerate` | cubic(0.05, 0.7, 0.1, 1.0) | Entrances, sheets |
| `interactiveSpring()` | spring(0.75, 350) | Press/release, bottom-bar pill, FAB |
| `placementSpring()` | no-bounce spring | `animateItem()` reorders |

## Transition contract (applies to every destination)

- Enter = fade + 24dp upward slide + scale 0.98 → 1.0 (`MotionTokens.navEnter`).
- Exit = fade + scale to 0.98 (`MotionTokens.navExit`).
- Never a plain full-screen cross-fade: the entering screen always carries
  directional motion, and exits run fast (150ms) so two screens never sit blended.
- Every screen root paints an opaque background (`background` on the root layout
  plus opaque Scaffold containers), so the previous screen can never show through.
- Reduce motion: everything collapses to ≤120ms fades or instant.

## Layer discipline (the jitter fix)

- Animate only `alpha`, `translation` (translationY/offset), and `scale`, always
  through `graphicsLayer` (or `Modifier.scale`, which is layer-backed).
- No layout-affecting animation during transitions or scroll: no size/padding/
  arrangement tweens, no `AnimatedVisibility` expand/shrink in lists.
- Scroll-linked effects (if added later) must use `derivedStateOf` +
  `graphicsLayer` offsets only. App icons are LRU-cached and keyed by package,
  so nothing reloads during fling.

## Shared elements

- Clone/app icon + title morph from list card into the detail header via the
  Compose shared-transition API (`SharedTransitionLayout` in `NavGraph`,
  `sharedElementModifier` helper), keyed `"${cloneId}-icon"` / `"${cloneId}-title"`.
- Skipped entirely under Reduce motion (scopes stay null).
- The nav fade/slide already provides continuity, so shared elements are
  progressive enhancement, not a load-bearing transition.

## Lists

- One-time staggered entrance (12ms/item, alpha + 14dp rise, graphicsLayer only)
  on first visit per screen (`rememberStagger` + `staggerFade`). Never re-stagger
  on scroll back. `animateItem()` with the placement spring handles reorders.

## Components

- Bottom bar: custom bar; the indicator pill glides between tabs on the
  interactive spring; icon/label cross-fade with slight scale.
- FAB: spring press-scale; icon cross-fades on destination change.
- Cards: press = scale 0.98 + tonal shift to `surfaceContainerHigh`, spring release.
- Dialogs (`KuroDialog`): scale 0.92 → 1 + fade on show.
- Bottom sheets (`KuroBottomSheet`): Material 3 `ModalBottomSheet` built-in
  emphasized slide + scrim fade; no custom spec to avoid fighting the framework.
