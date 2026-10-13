package com.kuro.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
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
    reduceMotion: Boolean = false,
    pillOffset: Dp = bottomBarPillOffset(current)
) {
    val tabs = listOf(
        Tab(BottomDestination.Home, Icons.Filled.Home, R.string.nav_home, R.string.cd_nav_home),
        Tab(BottomDestination.Library, Icons.Filled.ViewModule, R.string.nav_library, R.string.cd_nav_library),
        Tab(BottomDestination.Activity, Icons.Filled.Timeline, R.string.nav_activity, R.string.cd_nav_activity),
        Tab(BottomDestination.Settings, Icons.Filled.Settings, R.string.nav_settings, R.string.cd_nav_settings)
    )
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            val pillW = 64.dp
            // Single pill instance; offset is animated by the caller's spring.
            Box(
                modifier = Modifier
                    .offset(x = pillOffset, y = 12.dp)
                    .size(pillW, 32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            )
            Row(modifier = Modifier.fillMaxSize()) {
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
                        AnimatedContent(
                            targetState = selected,
                            transitionSpec = {
                                (fadeIn(tween(if (reduceMotion) MotionTokens.REDUCED_MS else MotionTokens.FAST_MS)) +
                                    scaleIn(
                                        tween(if (reduceMotion) MotionTokens.REDUCED_MS else MotionTokens.FAST_MS),
                                        initialScale = 0.85f
                                    ))
                                    .togetherWith(
                                        fadeOut(tween(if (reduceMotion) MotionTokens.REDUCED_MS else MotionTokens.FAST_MS)) +
                                            scaleOut(
                                                tween(if (reduceMotion) MotionTokens.REDUCED_MS else MotionTokens.FAST_MS),
                                                targetScale = 0.85f
                                            )
                                    )
                            },
                            label = "tab-content"
                        ) { isSelected ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier.size(pillW, 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.cdRes),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = if (isSelected) 1.08f else 1f
                                            scaleY = if (isSelected) 1.08f else 1f
                                        }
                                    )
                                }
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun bottomBarPillOffset(current: BottomDestination): Dp =
    when (current) {
        BottomDestination.Home -> 20.dp
        BottomDestination.Library -> 113.dp
        BottomDestination.Activity -> 206.dp
        BottomDestination.Settings -> 299.dp
    }
