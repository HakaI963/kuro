package com.kuro.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.kuro.app.ui.theme.KuroMotion

@Composable
fun LoadingSkeleton(
    modifier: Modifier = Modifier,
    lines: Int = 3,
    reduceMotion: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(lines) { index ->
            ShimmerBlock(
                modifier = Modifier.fillMaxWidth(),
                fraction = if (index == lines - 1) 0.6f else 1f,
                reduceMotion = reduceMotion
            )
            if (index < lines - 1) Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ShimmerBlock(
    modifier: Modifier = Modifier,
    fraction: Float = 1f,
    reduceMotion: Boolean = false
) {
    val base = MaterialTheme.colorScheme.surfaceContainer
    if (reduceMotion) {
        Box(
            modifier = modifier
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(base.copy(alpha = 0.7f))
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(KuroMotion.SHIMMER_MS),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-offset"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            base,
            base.copy(alpha = 0.5f),
            base
        ),
        start = Offset(offset - 400f, 0f),
        end = Offset(offset, 200f)
    )
    Box(
        modifier = modifier
            .fillMaxWidth(fraction)
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
    )
}
