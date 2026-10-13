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
        else MotionTokens.pressSpring(),
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
        else MotionTokens.pressSpring(),
        label = "press-tonal"
    )
}

fun Modifier.firstLoadFade(
    animate: Boolean,
    reduceMotion: Boolean,
    onShown: () -> Unit
): Modifier = composed {
    var shown by remember { mutableStateOf(!animate) }
    LaunchedEffect(animate) {
        if (animate && !shown) {
            shown = true
            onShown()
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            if (reduceMotion) MotionTokens.REDUCED_MS else MotionTokens.LIST_FADE_MS,
            easing = MotionTokens.Standard
        ),
        label = "list-fade"
    )
    this.graphicsLayer {
        this.alpha = alpha
    }
}
