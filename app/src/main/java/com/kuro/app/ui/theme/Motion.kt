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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object MotionTokens {
    const val FAST_MS = 150
    const val MEDIUM_MS = 300
    const val SLOW_MS = 450
    const val REDUCED_MS = 100
    const val INTRO_TOTAL_MS = 1500

    const val TAB_MS = 220
    const val TAB_SLIDE_DP = 20
    const val PUSH_ENTER_MS = 300
    const val PUSH_EXIT_MS = 250
    const val POP_ENTER_MS = 250
    const val POP_EXIT_MS = 275
    const val DIALOG_MS = 180
    const val LIST_FADE_MS = 120

    const val PRESS_SCALE = 0.98f
    const val DIALOG_ENTER_SCALE = 0.92f
    val PUSH_ENTER_SLIDE: Dp = 24.dp

    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    fun <T> interactiveSpring(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.75f, stiffness = 350f)

    fun <T> pressSpring(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 700f)

    fun pushEnter(reduceMotion: Boolean, slidePx: Int): EnterTransition {
        if (reduceMotion) return fadeIn(tween(REDUCED_MS))
        return fadeIn(tween(PUSH_ENTER_MS, easing = Standard)) +
            slideInVertically(tween(PUSH_ENTER_MS, easing = Standard)) { slidePx }
    }

    fun pushExit(reduceMotion: Boolean): ExitTransition {
        if (reduceMotion) return fadeOut(tween(REDUCED_MS))
        return fadeOut(tween(PUSH_EXIT_MS, easing = Standard))
    }

    fun popEnter(reduceMotion: Boolean): EnterTransition {
        if (reduceMotion) return fadeIn(tween(REDUCED_MS))
        return fadeIn(tween(POP_ENTER_MS, easing = Standard))
    }

    fun popExit(reduceMotion: Boolean, slidePx: Int): ExitTransition {
        if (reduceMotion) return fadeOut(tween(REDUCED_MS))
        return fadeOut(tween(POP_EXIT_MS, easing = Standard)) +
            slideOutVertically(tween(POP_EXIT_MS, easing = Standard)) { slidePx }
    }

    fun dialogScale(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reduceMotion) tween(REDUCED_MS)
        else tween(DIALOG_MS, easing = Emphasized)

    fun dialogAlpha(reduceMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reduceMotion) tween(REDUCED_MS)
        else tween(FAST_MS, easing = Standard)
}
