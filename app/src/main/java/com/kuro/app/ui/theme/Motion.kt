package com.kuro.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object MotionTokens {
    const val FAST_MS = 150
    const val MEDIUM_MS = 300
    const val SLOW_MS = 450
    const val REDUCED_MS = 100
    const val INTRO_TOTAL_MS = 1500
    const val STAGGER_STEP_MS = 12
    const val STAGGER_MAX_ITEMS = 12

    const val PRESS_SCALE = 0.98f
    const val DIALOG_ENTER_SCALE = 0.92f
    const val NAV_ENTER_SCALE = 0.98f
    const val NAV_EXIT_SCALE = 0.98f
    val NAV_ENTER_SLIDE: Dp = 24.dp

    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    fun <T> interactiveSpring(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.75f, stiffness = 350f)

    fun <T> placementSpring(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    fun staggerDelayMs(index: Int): Int {
        if (index < 0) return 0
        return (minOf(index, STAGGER_MAX_ITEMS) * STAGGER_STEP_MS)
    }

    fun navEnter(reduceMotion: Boolean, slidePx: Int = 48): EnterTransition {
        if (reduceMotion) return fadeIn(tween(REDUCED_MS))
        return fadeIn(tween(MEDIUM_MS, easing = Standard)) +
            slideInVertically(tween(MEDIUM_MS, easing = Standard)) { slidePx } +
            scaleIn(tween(MEDIUM_MS, easing = Standard), initialScale = NAV_ENTER_SCALE)
    }

    fun navExit(reduceMotion: Boolean): ExitTransition {
        if (reduceMotion) return fadeOut(tween(REDUCED_MS))
        return fadeOut(tween(FAST_MS, easing = Standard)) +
            scaleOut(tween(FAST_MS, easing = Standard), targetScale = NAV_EXIT_SCALE)
    }

    fun dialogScale(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reduceMotion) tween(REDUCED_MS)
        else tween(MEDIUM_MS, easing = Emphasized)

    fun dialogAlpha(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reduceMotion) tween(REDUCED_MS)
        else tween(FAST_MS, easing = Standard)
}
