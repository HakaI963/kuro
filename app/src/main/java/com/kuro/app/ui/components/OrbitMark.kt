package com.kuro.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbitMark(
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.outline
    val density = LocalDensity.current
    val strokePx = with(density) { 2.dp.toPx() }
    val dotPx = with(density) { 3.dp.toPx() }
    Canvas(modifier = modifier.size(22.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - dotPx
        drawCircle(color = track.copy(alpha = 0.4f), radius = r, center = center, style = Stroke(width = strokePx))
        drawArc(
            color = primary,
            startAngle = -90f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        val rad = Math.toRadians(30.0).toFloat()
        drawCircle(
            color = secondary,
            radius = dotPx,
            center = Offset(center.x + r * cos(rad), center.y + r * sin(rad))
        )
    }
}
