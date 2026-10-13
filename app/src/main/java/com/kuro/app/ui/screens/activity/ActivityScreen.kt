package com.kuro.app.ui.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.core.utils.formatRelative
import com.kuro.app.domain.models.ActivityType
import com.kuro.app.ui.components.EmptyStateView
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.LoadingSkeleton

@Composable
fun ActivityScreen(
    modifier: Modifier = Modifier,
    vm: ActivityViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            KuroCard(
                title = stringResource(R.string.activity_banner),
                subtitle = stringResource(R.string.activity_subtitle)
            ) {
            }
        }
        if (state.isLoading) {
            item { LoadingSkeleton(lines = 4) }
        } else if (state.events.isEmpty()) {
            item {
                EmptyStateView(
                    title = stringResource(R.string.activity_empty_title),
                    body = stringResource(R.string.activity_empty_body),
                    icon = Icons.Filled.Timeline
                )
            }
        } else {
            items(state.events, key = { it.id }) { ev ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(dotColor(ev.type))
                            )
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(28.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = ev.cloneName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = activityLabel(ev.type) + " • " + ev.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatRelative(ev.timestampMillis),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(90.dp)) }
        }
    }
}

@Composable
private fun activityLabel(type: ActivityType): String = when (type) {
    ActivityType.CREATED -> stringResource(R.string.activity_created)
    ActivityType.LAUNCHED -> stringResource(R.string.activity_launched)
    ActivityType.DELETED -> stringResource(R.string.activity_deleted)
}

@Composable
private fun dotColor(type: ActivityType): androidx.compose.ui.graphics.Color {
    return when (type) {
        ActivityType.CREATED -> MaterialTheme.colorScheme.primary
        ActivityType.LAUNCHED -> MaterialTheme.colorScheme.secondary
        ActivityType.DELETED -> MaterialTheme.colorScheme.error
    }
}
