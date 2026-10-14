package com.kuro.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Kuro dark tonal scheme (dark-first; light kept consistent but unused —
// the app forces dark mode).
private val DarkColorScheme = darkColorScheme(
    primary = BrandCream,
    onPrimary = Color.White,
    secondary = Color(0xFF49D6C2),
    onSecondary = FluxDarkGrey,
    tertiary = FluxAccentCyan,
    background = FluxDarkSurface,
    surface = FluxDarkGrey,
    surfaceContainer = Color(0xFF151D2A),
    surfaceContainerHigh = Color(0xFF1B2534),
    onBackground = TextWhite,
    onSurface = TextWhite,
    onSurfaceVariant = TextGrey,
    outline = Color(0xFF263244),
    outlineVariant = Color(0xFF263244),
    error = FluxDanger,
)

// Fallback Light Scheme (Soft Cream Primary, Dark Text)
private val LightColorScheme = lightColorScheme(
    primary = BrandCream,
    onPrimary = FluxDarkGrey,
    secondary = FluxDarkGrey,
    onSecondary = Color.White,
    tertiary = FluxAccentMagenta,
    background = Color(0xFFFAFAFA), // Very light grey/white for clean look
    onBackground = FluxDarkGrey,
    surface = Color.White, // Clean white surface for glass effect
    onSurface = FluxDarkGrey,
)

@Composable
fun KuroTheme(
    themeMode: com.kuro.app.core.utils.ThemeMode = com.kuro.app.core.utils.ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        com.kuro.app.core.utils.ThemeMode.LIGHT -> false
        com.kuro.app.core.utils.ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                // Use Dynamic Dark but override with the Kuro tonal palette.
                dynamicDarkColorScheme(context).copy(
                    primary = BrandCream,
                    onPrimary = Color.White,
                    secondary = Color(0xFF49D6C2),
                    onSecondary = FluxDarkGrey,
                    tertiary = FluxAccentCyan,
                    background = FluxDarkSurface,
                    surface = FluxDarkGrey,
                    surfaceContainer = Color(0xFF151D2A),
                    surfaceContainerHigh = Color(0xFF1B2534),
                    onBackground = TextWhite,
                    onSurface = TextWhite,
                    onSurfaceVariant = TextGrey,
                    outline = Color(0xFF263244),
                    outlineVariant = Color(0xFF263244),
                    error = FluxDanger
                )
            } else {
                // Use Dynamic Light but override Primary & Secondary
                dynamicLightColorScheme(context).copy(
                    primary = BrandCream, // Soft Cream Primary
                    onPrimary = FluxDarkGrey,
                    secondary = FluxDarkGrey,
                    onSecondary = Color.White,
                    background = Color(0xFFFAFAFA),
                    onBackground = FluxDarkGrey,
                    surface = Color.White, // Clean white surface
                    onSurface = FluxDarkGrey
                )
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb() // Match background
            window.navigationBarColor = colorScheme.background.toArgb()
            
            // Handle Icon Colors
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme // Light icons in Dark mode, Dark icons in Light mode
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
