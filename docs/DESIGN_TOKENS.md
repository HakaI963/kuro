# Design Tokens — Kuro

## Colors (fallback, dark-first)

| Token | Value |
|---|---|
| background | #0A0E14 |
| surface | #101620 |
| surface-container | #151D2A |
| surface-elevated | #1B2534 |
| primary | #7C6CFF |
| on-primary | #FFFFFF |
| secondary | #49D6C2 |
| tertiary | #5DB2FF |
| success | #3DDC97 |
| warning | #FFC861 |
| error | #FF6B6B |
| text-primary | #EAF0F8 |
| text-secondary | #9AA7B8 |
| outline | #263244 |

- Semantic use only; no hardcoded colors in composables (except avatar tints derived from data).
- Dynamic color supported via `dynamicDarkColorScheme` / `dynamicLightColorScheme` on API 31+, toggleable.

## Typography

Material 3 scale customized in `Type.kt`: displaySmall 32/40 semi-bold, headlineMedium 24/32, headlineSmall 20/28, titleLarge 18/26 medium, titleMedium 16/24 medium, bodyLarge 16/24, bodyMedium 14/20, labelLarge 14/20 medium, labelMedium 12/16, labelSmall 11/14.

## Shape

extraSmall 14dp, small 16dp, medium 20dp, large 24dp, extraLarge 28dp. Cards default to medium (20dp).

## Spacing (4/8 grid)

4, 8, 12, 16, 20, 24, 32. Screen horizontal 20dp, card padding 16dp, list gap 12dp. Exposed via `LocalKuroSpacing`.

## Motion

fast 150ms, medium 250ms, slow 400ms, shimmer 1200ms, splash 900ms. Easings: Standard (0.2,0,0,1), Emphasized (0.05,0.7,0.1,1). `reduceMotion` → durations 0, shimmer static, press-scale 1.0, nav fade 0ms.

## Elevation / Borders

- Cards: 0dp elevation, `surfaceContainer` fill, 1dp outline border only for selectable cards.
- Selected state: 1.5dp primary border + 8% primary fill.
- Chips/badges: 999dp pill for status, 16dp for chips.
