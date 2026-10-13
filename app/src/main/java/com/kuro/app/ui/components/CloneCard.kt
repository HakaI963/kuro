package com.kuro.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuro.app.R
import com.kuro.app.core.utils.formatRelative
import com.kuro.app.domain.models.CloneProfile

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CloneCard(
    clone: CloneProfile,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    sharedScope: SharedTransitionScope? = null,
    animScope: AnimatedVisibilityScope? = null,
    sharedKey: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScaleValue(interaction, reduceMotion)
    val tonal by pressTonalColor(MaterialTheme.colorScheme.surfaceContainer, pressed, reduceMotion)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = tonal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(clone.labelColorArgb).copy(alpha = 0.2f))
                    .then(
                        sharedElementModifier(
                            sharedScope, animScope,
                            sharedKey?.let { "$it-icon" }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = clone.iconLetter,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(clone.labelColorArgb)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clone.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = sharedElementModifier(
                        sharedScope, animScope,
                        sharedKey?.let { "$it-title" }
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = clone.sourceAppName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = clone.status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (clone.lastLaunchedMillis != null)
                            formatRelative(clone.lastLaunchedMillis)
                        else
                            stringResource(R.string.details_created) + " " + com.kuro.app.core.utils.formatDate(clone.createdAtMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun sharedElementModifier(
    sharedScope: SharedTransitionScope?,
    animScope: AnimatedVisibilityScope?,
    key: String?
): Modifier {
    if (sharedScope == null || animScope == null || key == null) return Modifier
    return with(sharedScope) {
        Modifier.sharedElement(
            sharedScope.rememberSharedContentState(key = key),
            animatedVisibilityScope = animScope
        )
    }
}
