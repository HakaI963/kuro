package com.kuro.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.kuro.app.ui.theme.MotionTokens
import kotlinx.coroutines.delay

@Composable
fun pressScaleValue(
    interaction: InteractionSource,
    reduceMotion: Boolean
): State<Float> {
    val pressed by interaction.collectIsPressedAsState()
    val target = if (pressed && !reduceMotion) MotionTokens.PRESS_SCALE else 1f
    return animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) tween(MotionTokens.REDUCED_MS)
        else MotionTokens.interactiveSpring(),
        label = "press-scale"
    )
}

@Composable
fun pressTonalColor(
    base: Color,
    pressed: Boolean,
    reduceMotion: Boolean
): State<Color> {
    val target = if (pressed && !reduceMotion) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else base
    return androidx.compose.animation.animateColorAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) tween(MotionTokens.REDUCED_MS)
        else tween(MotionTokens.FAST_MS, easing = MotionTokens.Standard),
        label = "press-tonal"
    )
}

class StaggerController internal constructor(
    private val seen: androidx.compose.runtime.State<Boolean>,
    private val reduceMotion: Boolean
) {
    fun delayFor(index: Int): Int =
        if (seen.value || reduceMotion) 0 else MotionTokens.staggerDelayMs(index)
}

@Composable
fun rememberStagger(key: String, reduceMotion: Boolean): StaggerController {
    val seen = remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { seen.value = true }
    return remember(key, reduceMotion) { StaggerController(seen, reduceMotion) }
}

fun Modifier.staggerFade(delayMs: Int, reduceMotion: Boolean): Modifier = composed {
    var shown by remember(delayMs) { mutableStateOf(delayMs == 0) }
    LaunchedEffect(delayMs) {
        if (delayMs > 0) {
            delay(delayMs.toLong())
            shown = true
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            if (reduceMotion) MotionTokens.REDUCED_MS else MotionTokens.MEDIUM_MS,
            easing = MotionTokens.Standard
        ),
        label = "stagger-alpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (shown) 0f else 14f,
        animationSpec = tween(
            if (reduceMotion) MotionTokens.REDUCED_MS else MotionTokens.MEDIUM_MS,
            easing = MotionTokens.Standard
        ),
        label = "stagger-offset"
    )
    this.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}
