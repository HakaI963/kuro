package com.kuro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Brand Colors — Step 6: Kuro dark-first tonal palette (names kept for call sites).
val FluxCream = Color(0xFF101620)
val FluxCreamPrimary = Color(0xFF151D2A)
// Kuro primary (tonal violet).
val BrandCream = Color(0xFF7C6CFF)

val FluxDarkGrey = Color(0xFF101620)
val FluxDarkSurface = Color(0xFF0A0E14)

// Legacy / Accents (Kuro secondary + tertiary).
val FluxAccentCyan = Color(0xFF5DB2FF)
val FluxAccentMagenta = Color(0xFF49D6C2)

// Glass Defaults (Deprecated but kept for compilation until refactor complete)
val FluxBackgroundStart = Color(0xFF0F0C29)
val FluxBackgroundMid = Color(0xFF302B63)
val FluxBackgroundEnd = Color(0xFF24243E)
val GlassWhiteHigh = Color(0x26FFFFFF)
val GlassWhiteMedium = Color(0x1AFFFFFF)
val GlassWhiteLow = Color(0x0DFFFFFF)
val GlassBorder = Color(0x4DFFFFFF)
val TextWhite = Color(0xFFEAF0F8)
val TextGrey = Color(0xFF9AA7B8)
val Seed = Color(0xFF7C6CFF)

// Contrast-safe on #0A0E14 / #101620
val FluxBodyMuted = Color(0xFF9AA7B8)
val FluxHairline = Color(0xFF263244)
val FluxCardFill = Color(0xFF151D2A) // opaque surface container
val FluxDanger = Color(0xFFFF6B6B)
val FluxSwitchCheckedTrack = BrandCream
val FluxSwitchCheckedThumb = FluxDarkGrey
val FluxSwitchUncheckedTrack = Color(0xFF263244)
val FluxSwitchUncheckedThumb = Color(0xFFE8E8E8)
val FluxSwitchUncheckedBorder = Color(0xFF6B6B6B)

@Composable
fun fluxMutedText(): Color {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        FluxBodyMuted
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
}

