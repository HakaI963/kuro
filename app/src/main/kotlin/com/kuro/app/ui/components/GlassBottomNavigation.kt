package com.kuro.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private val BottomNavItems = BottomTab.entries.toList()

/**
 * Step 7: opaque Kuro bottom bar (64dp, surface at 0.95 alpha) with a single
 * sliding pill indicator (64x32dp, fully rounded, primaryContainer) that
 * glides between tabs with a 200ms tween. Selected icon+label use
 * onPrimaryContainer; unselected use onSurfaceVariant. The haze param is kept
 * for call-site compatibility; the bar itself is opaque so tabs never blend.
 */
@Composable
fun GlassBottomNavigation(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_PARAMETER")
    val _haze = hazeState
    val colors = MaterialTheme.colorScheme
    val selectedIndex = BottomNavItems.indexOf(selectedTab).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(colors.surface.copy(alpha = 0.95f))
    ) {
        val tabWidth = maxWidth / BottomNavItems.size
        val pillOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex + (tabWidth - 64.dp) / 2,
            animationSpec = tween(durationMillis = 200),
            label = "kuro-tab-pill"
        )
        // Sliding pill, vertically centered.
        Box(
            modifier = Modifier
                .offset(x = pillOffset)
                .align(Alignment.CenterStart)
                .width(64.dp)
                .height(32.dp)
                .background(colors.primaryContainer, RoundedCornerShape(50))
        )
        Row(modifier = Modifier.matchParentSize()) {
            BottomNavItems.forEach { tab ->
                val selected = tab == selectedTab
                val contentColor =
                    if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
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
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}
