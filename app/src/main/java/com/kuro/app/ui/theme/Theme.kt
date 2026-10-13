package com.kuro.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.kuro.app.domain.models.ThemeMode

private val KuroDarkScheme = darkColorScheme(
    primary = KuroPalette.Primary,
    onPrimary = KuroPalette.OnPrimary,
    secondary = KuroPalette.Secondary,
    tertiary = KuroPalette.Tertiary,
    error = KuroPalette.Error,
    background = KuroPalette.Background,
    surface = KuroPalette.Surface,
    surfaceContainer = KuroPalette.SurfaceContainer,
    surfaceContainerHigh = KuroPalette.SurfaceElevated,
    onBackground = KuroPalette.TextPrimary,
    onSurface = KuroPalette.TextPrimary,
    onSurfaceVariant = KuroPalette.TextSecondary,
    outline = KuroPalette.Outline,
    outlineVariant = KuroPalette.Outline
)

private val KuroLightScheme = lightColorScheme(
    primary = KuroPalette.Primary,
    onPrimary = KuroPalette.OnPrimary,
    secondary = KuroPalette.Secondary,
    tertiary = KuroPalette.Tertiary,
    error = KuroPalette.Error,
    background = KuroPalette.TextPrimary,
    surface = KuroPalette.OnPrimary,
    onBackground = KuroPalette.Background,
    onSurface = KuroPalette.Background,
    onSurfaceVariant = KuroPalette.SurfaceElevated
)

@Composable
fun KuroTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> KuroDarkScheme
        else -> KuroLightScheme
    }
    CompositionLocalProvider(LocalKuroSpacing provides KuroSpacing()) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KuroTypography,
            shapes = KuroShapes,
            content = content
        )
    }
}
