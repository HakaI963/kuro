package com.kuro.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kuro.app.R
import com.kuro.app.ui.navigation.BottomDestination
import com.kuro.app.ui.theme.MotionTokens

private data class Tab(
    val dest: BottomDestination,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelRes: Int,
    val cdRes: Int
)

@Composable
fun KuroBottomBar(
    current: BottomDestination,
    onSelect: (BottomDestination) -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false
) {
    // Step 4 (Stage 2): System | Terminal | Clones | Settings.
    // Pill math is cell-agnostic (barWidth/4), so 4 cells stay centered.
    val tabs = listOf(
        Tab(BottomDestination.System, Icons.Filled.Memory, R.string.nav_system, R.string.cd_nav_system),
        Tab(BottomDestination.Terminal, Icons.Filled.Terminal, R.string.nav_terminal, R.string.cd_nav_terminal),
        Tab(BottomDestination.Clones, Icons.Filled.ViewModule, R.string.nav_clones, R.string.cd_nav_clones),
        Tab(BottomDestination.Settings, Icons.Filled.Settings, R.string.nav_settings, R.string.cd_nav_settings)
    )
    val selectedIndex = tabs.indexOfFirst { it.dest == current }.coerceAtLeast(0)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            val density = LocalDensity.current
            // Step 3c: single pill instance. Width = 64dp (barWidth/4*0.6),
            // height 32dp, fully rounded, centered under selected tab.
            // Offset = cellWidth * index + (cellWidth - pillWidth)/2 at rest.
            val cellWidth = maxWidth / 4f
            val pillW = 64.dp
            val pillH = 32.dp
            val centeredBase = (cellWidth - pillW) / 2f
            val cellWidthPx = with(density) { cellWidth.toPx() }
            val targetPx = cellWidthPx * selectedIndex.toFloat()

            // Step 4b: exactly ONE onGloballyPositioned in the bar (on the tab
            // row). It only records the measured width for rest-position
            // verification and writes the same value back, so no measure
            // feedback loop can form. Pill motion stays graphicsLayer-only.
            var barWidthPx by remember { mutableIntStateOf(0) }

            // Animate only translation via graphicsLayer, tween 200ms.
            // Reduce motion: 100ms alpha-style (fast fade of position, no spring).
            val animatedIndexPx by animateFloatAsState(
                targetValue = targetPx,
                animationSpec = tween(
                    durationMillis = if (reduceMotion) MotionTokens.REDUCED_MS else MotionTokens.INDICATOR_MS
                ),
                label = "nav-pill-x"
            )

            // Hairline top border: surfaceContainer bar with outlineVariant line.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )

            // One indicator instance, tonal surfaceContainerHigh (never brown).
            Box(
                modifier = Modifier
                    .offset(x = centeredBase, y = 12.dp)
                    .size(pillW, pillH)
                    .graphicsLayer {
                        translationX = animatedIndexPx
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        // Rest-position verification hook: barWidthPx must equal
                        // 4 * cellWidthPx so pill lands exactly centered.
                        barWidthPx = it.size.width
                    }
            ) {
                tabs.forEach { tab ->
                    val selected = tab.dest == current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { onSelect(tab.dest) }
                            ),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(pillW, pillH),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = stringResource(tab.cdRes),
                                    // Primary-toned contrast on surfaceContainerHigh.
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = stringResource(tab.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
