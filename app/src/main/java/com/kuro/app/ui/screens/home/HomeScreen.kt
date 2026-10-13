package com.kuro.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.domain.models.CloneStatus
import com.kuro.app.ui.components.CloneCard
import com.kuro.app.ui.components.EmptyStateView
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroSearchBar
import com.kuro.app.ui.components.LoadingSkeleton
import com.kuro.app.ui.components.OrbitMark
import com.kuro.app.ui.components.SectionHeader
import com.kuro.app.ui.components.pressScaleValue
import com.kuro.app.ui.components.pressTonalColor
import com.kuro.app.ui.components.rememberStagger
import com.kuro.app.ui.components.staggerFade
import java.util.Calendar

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onOpenClone: (String) -> Unit,
    onNewClone: () -> Unit,
    onImportApk: () -> Unit,
    onViewLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel(),
    sharedScope: SharedTransitionScope? = null,
    animScope: AnimatedVisibilityScope? = null
) {
    val state by vm.uiState.collectAsState()
    val reduceMotion by vm.reduceMotion.collectAsState(initial = false)
    val stagger = rememberStagger("home", reduceMotion)

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            GreetingHeader()
            Spacer(modifier = Modifier.height(12.dp))
            KuroSearchBar(value = state.query, onValueChange = vm::onQueryChange)
        }
        item {
            SectionHeader(title = stringResource(R.string.home_quick_actions))
            Spacer(modifier = Modifier.height(8.dp))
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
            item {
                SectionHeader(
                    title = stringResource(R.string.home_recent_clones),
                    actionLabel = stringResource(R.string.home_view_all),
                    onAction = onViewLibrary
                )
            }
            if (state.filtered.isEmpty() && state.clones.isNotEmpty()) {
                item {
                    EmptyStateView(
                        title = stringResource(R.string.home_no_results_title),
                        body = stringResource(R.string.home_no_results_body)
                    )
                }
            } else if (state.filtered.isEmpty()) {
                item {
                    KuroCard(
                        title = stringResource(R.string.home_empty_title),
                        subtitle = stringResource(R.string.home_empty_body)
                    ) {
                        androidx.compose.material3.Button(
                            onClick = onNewClone,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.home_empty_action))
                        }
                    }
                }
            } else {
                itemsIndexed(state.filtered.take(4), key = { _, c -> c.id }) { index, clone ->
                    CloneCard(
                        clone = clone,
                        onClick = { onOpenClone(clone.id) },
                        reduceMotion = reduceMotion,
                        sharedScope = sharedScope,
                        animScope = animScope,
                        sharedKey = clone.id,
                        modifier = Modifier
                            .staggerFade(stagger.delayFor(index), reduceMotion)
                            .animateItem()
                    )
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
                                    .padding(vertical = 6.dp),
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
private fun GreetingHeader() {
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
        OrbitMark()
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
            .then(if (enabled) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = tonal)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            icon()
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = desc,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
