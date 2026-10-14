package com.kuro.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState

/** Kuro bottom tabs: Home · Terminal · Clones · Settings. */
enum class BottomTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    TERMINAL("Terminal", Icons.Default.Terminal),
    CLONES("Clones", Icons.Default.Apps),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Flat Kuro bottom bar (72dp, 0xFF101620 at 0.95 alpha) with a 1dp top
 * border (0xFF1E293B). No indicator. Selected icon+label use
 * 0xFF6366F1; unselected use 0xFF94A3B8. Press scales the icon to 1.1x.
 * Tab-switch fade is handled by the host AnimatedContent.
 */
@Composable
fun CustomBottomNav(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF101620).copy(alpha = 0.95f))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF1E293B))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            BottomTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val contentColor =
                    if (selected) Color(0xFF6366F1) else Color(0xFF94A3B8)
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 1.1f else 1f,
                    animationSpec = tween(100),
                    label = "kuro-tab-press"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onTabSelected(tab) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = contentColor,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale
                                )
                        )
                        Text(
                            text = tab.title,
                            fontSize = 12.sp,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassBottomNavigation(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_PARAMETER")
    val _haze = hazeState
    CustomBottomNav(
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        modifier = modifier
    )
}
