package com.kuro.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

object KuroMotion {
    const val FAST_MS = 150
    const val MEDIUM_MS = 250
    const val SLOW_MS = 400
    const val SHIMMER_MS = 1200
    const val SPLASH_MS = 900

    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    fun durationMs(reduceMotion: Boolean, normalMs: Int): Int =
        if (reduceMotion) 0 else normalMs
}
