package com.kuro.app.ui.screens.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuro.app.R
import com.kuro.app.ui.theme.MotionTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun seg(p: Float, from: Float, to: Float): Float =
    ((p - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOutCubic(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return 1f - (1f - u) * (1f - u) * (1f - u)
}

@Composable
fun OrbitalIntro(
    reduceMotion: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    LaunchedEffect(reduceMotion) {
        // Step 4b: completion/finally handoff. If the animation is cancelled
        // (navigation, config change) or throws, the overlay state still flips
        // to hidden so the intro can never remain composed over any tab.
        // The ring animation itself is unchanged; reduce motion still skips it.
        try {
            if (reduceMotion) {
                fade.animateTo(1f, tween(MotionTokens.REDUCED_MS))
            } else {
                progress.animateTo(1f, tween(MotionTokens.INTRO_TOTAL_MS - 100, easing = LinearEasing))
            }
        } finally {
            finish()
        }
    }

    val skipInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = skipInteraction, indication = null) { finish() },
        contentAlignment = Alignment.Center
    ) {
        if (reduceMotion) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.alpha(fade.value)
            )
        } else {
            OrbitalScene(progress = progress.value)
        }
    }
}

@Composable
private fun OrbitalScene(progress: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.outline

    val ringSeg = easeOutCubic(seg(progress, 0f, 0.36f))
    val coreSeg = easeOutCubic(seg(progress, 0.05f, 0.3f))
    val dotSegs = listOf(
        easeOutCubic(seg(progress, 0.14f, 0.55f)),
        easeOutCubic(seg(progress, 0.24f, 0.62f)),
        easeOutCubic(seg(progress, 0.34f, 0.68f))
    )
    val pulseSeg = seg(progress, 0.5f, 0.78f)
    val pulse = sin(pulseSeg * PI.toFloat())
    val contract = 1f - 0.06f * seg(progress, 0.55f, 0.8f)
    val wordSeg = easeOutCubic(seg(progress, 0.62f, 0.93f))

    val density = LocalDensity.current
    val strokePx = with(density) { 2.dp.toPx() }
    val dotAngles = listOf(210f, 330f, 90f)
    val dotColors = listOf(primary, secondary, tertiary)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseR = size.minDimension / 2f * 0.72f
            val ringR = baseR * contract

            drawCircle(color = track.copy(alpha = 0.28f), radius = ringR, center = center, style = Stroke(width = strokePx))
            if (ringSeg > 0f) {
                drawArc(
                    color = primary,
                    startAngle = -90f + (1f - ringSeg) * 50f,
                    sweepAngle = 320f * ringSeg,
                    useCenter = false,
                    topLeft = Offset(center.x - ringR, center.y - ringR),
                    size = androidx.compose.ui.geometry.Size(ringR * 2f, ringR * 2f),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
            dotAngles.forEachIndexed { i, deg ->
                val s = dotSegs[i]
                if (s > 0f) {
                    val rad = Math.toRadians(deg.toDouble()).toFloat()
                    val r = ringR * s
                    drawCircle(
                        color = dotColors[i].copy(alpha = s),
                        radius = with(density) { 4.dp.toPx() },
                        center = Offset(center.x + r * cos(rad), center.y + r * sin(rad))
                    )
                }
            }
            if (coreSeg > 0f) {
                val glowR = with(density) { 13.dp.toPx() } * (0.6f + 0.4f * coreSeg) * (1f + 0.28f * pulse)
                drawCircle(color = primary.copy(alpha = 0.16f * coreSeg), radius = glowR * 1.6f, center = center)
                drawCircle(color = primary.copy(alpha = 0.35f * coreSeg), radius = glowR, center = center)
                drawCircle(color = primary.copy(alpha = coreSeg), radius = glowR * 0.45f, center = center)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium.copy(
                letterSpacing = ((1f - wordSeg) * 10f).sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.alpha(wordSeg)
        )
    }
}
