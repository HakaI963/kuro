package com.kuro.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.domain.models.CloneStatus
import com.kuro.engine.EngineStatus
import com.kuro.app.ui.components.CloneCard
import com.kuro.app.ui.components.EmptyStateView
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroPrimaryButton
import com.kuro.app.ui.components.KuroSearchBar
import com.kuro.app.ui.components.LoadingSkeleton
import com.kuro.app.ui.components.SectionHeader
import com.kuro.app.ui.components.firstLoadFade
import com.kuro.app.ui.components.pressScaleValue
import com.kuro.app.ui.components.pressTonalColor
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpenClone: (String) -> Unit,
    onNewClone: () -> Unit,
    onImportApk: () -> Unit,
    onViewLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel(),
    listState: LazyListState = LazyListState(),
    animateListIn: Boolean = false,
    onListShown: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val reduceMotion by vm.reduceMotion.collectAsState(initial = false)
    val engineStatus by vm.engineStatus.collectAsState(initial = EngineStatus.Uninitialized)
    val engineInitializing by vm.engineInitializing.collectAsState(initial = false)

    LaunchedEffect(animateListIn) {
        if (!animateListIn) onListShown()
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .firstLoadFade(animate = animateListIn, reduceMotion = reduceMotion, onShown = onListShown)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            // Step 3c: screen top = 16dp below app bar; all tab headers align.
            Spacer(modifier = Modifier.height(16.dp))
            GreetingHeader(engineStatus = engineStatus, engineInitializing = engineInitializing)
            Spacer(modifier = Modifier.height(12.dp))
            KuroSearchBar(value = state.query, onValueChange = vm::onQueryChange)
        }
        item {
            SectionHeader(title = stringResource(R.string.home_quick_actions))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(
                    title = stringResource(R.string.home_action_new_clone),
                    desc = stringResource(R.string.home_action_new_clone_desc),
                    icon = { Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = onNewClone,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.weight(1f)
                )
                QuickAction(
                    title = stringResource(R.string.home_action_import_apk),
                    desc = stringResource(R.string.home_action_import_apk_desc),
                    icon = { Icon(Icons.Filled.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {},
                    reduceMotion = reduceMotion,
                    modifier = Modifier.weight(1f),
                    enabled = false
                )
                QuickAction(
                    title = stringResource(R.string.home_action_view_library),
                    desc = stringResource(R.string.home_action_view_library_desc),
                    icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    onClick = onViewLibrary,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (state.isLoading) {
            item { LoadingSkeleton(reduceMotion = reduceMotion) }
        } else {
            // Recent clones section: title -> content 12dp, list rows 12dp.
            item {
                SectionHeader(
                    title = stringResource(R.string.home_recent_clones),
                    actionLabel = stringResource(R.string.home_view_all),
                    onAction = onViewLibrary
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (state.filtered.isEmpty() && state.clones.isNotEmpty()) {
                    EmptyStateView(
                        title = stringResource(R.string.home_no_results_title),
                        body = stringResource(R.string.home_no_results_body)
                    )
                } else if (state.filtered.isEmpty()) {
                    KuroCard(
                        title = stringResource(R.string.home_empty_title),
                        subtitle = stringResource(R.string.home_empty_body)
                    ) {
                        KuroPrimaryButton(
                            label = stringResource(R.string.home_empty_action),
                            onClick = onNewClone,
                            reduceMotion = reduceMotion
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.filtered.take(4).forEach { clone ->
                            CloneCard(
                                clone = clone,
                                onClick = { onOpenClone(clone.id) },
                                reduceMotion = reduceMotion
                            )
                        }
                    }
                }
            }

            item {
                val active = state.clones.filter { it.status == CloneStatus.ACTIVE }
                KuroCard(
                    title = stringResource(R.string.home_active_now),
                    subtitle = if (active.isEmpty()) stringResource(R.string.home_empty_title) else "${active.size} ${stringResource(R.string.home_storage_profiles)}"
                ) {
                    if (active.isNotEmpty()) {
                        active.take(2).forEach { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = c.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                KuroCard(
                    title = stringResource(R.string.home_storage_title),
                    subtitle = "${stringResource(R.string.home_storage_used)} • ${state.clones.size} ${stringResource(R.string.home_storage_profiles)}"
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${state.clones.size} ${stringResource(R.string.home_storage_profiles)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.home_storage_manage),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = onViewLibrary)
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(90.dp)) }
        }
    }
}

@Composable
private fun GreetingHeader(
    engineStatus: EngineStatus,
    engineInitializing: Boolean
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val res = when (hour) {
        in 5..11 -> R.string.home_greeting_morning
        in 12..17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        EngineChip(status = engineStatus, initializing = engineInitializing)
    }
}

@Composable
private fun EngineChip(status: EngineStatus, initializing: Boolean) {
    val labelRes = when {
        initializing -> R.string.engine_chip_loading
        status is EngineStatus.Ready -> R.string.settings_engine_status_ready
        status is EngineStatus.Error -> R.string.settings_engine_status_error
        status is EngineStatus.Incompatible -> R.string.settings_engine_status_incompatible
        else -> R.string.settings_engine_status_uninitialized
    }
    val color = when {
        initializing -> MaterialTheme.colorScheme.onSurfaceVariant
        status is EngineStatus.Ready -> MaterialTheme.colorScheme.primary
        status is EngineStatus.Error -> MaterialTheme.colorScheme.error
        status is EngineStatus.Incompatible -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "engine-chip-fade"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .alpha(alpha)
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun QuickAction(
    title: String,
    desc: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    enabled: Boolean = true
) {
    // Step 3c: surfaceContainer + 1dp outlineVariant border + icon in a
    // 40dp tonal circle; press scale 0.98. No flat low-contrast slabs.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScaleValue(interaction, reduceMotion)
    val tonal by pressTonalColor(
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (enabled) 1f else 0.5f),
        pressed, reduceMotion
    )
    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.medium
            )
            .then(if (enabled) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = tonal)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = desc,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
