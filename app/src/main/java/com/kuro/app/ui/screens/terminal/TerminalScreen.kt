package com.kuro.app.ui.screens.terminal

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.core.utils.formatBytes
import com.kuro.app.data.containers.ContainerStatus
import com.kuro.app.data.containers.ContainerType
import com.kuro.app.data.containers.LinuxContainer
import com.kuro.app.ui.components.EmptyStateView
import com.kuro.app.ui.components.KuroDialog
import com.kuro.app.ui.components.KuroOutlineButton
import com.kuro.app.ui.components.KuroPrimaryButton
import com.kuro.app.ui.components.SectionHeader
import com.kuro.app.ui.components.firstLoadFade

@Composable
fun TerminalScreen(
    onAddDistro: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: TerminalViewModel = viewModel(),
    reduceMotion: Boolean = false,
    listState: LazyListState = LazyListState(),
    animateListIn: Boolean = false,
    onListShown: () -> Unit = {}
) {
    val containers by vm.containers.collectAsState()
    val startError by vm.startError.collectAsState()
    val deleted by vm.deleted.collectAsState()
    val context = LocalContext.current
    var guiNoteFor by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<LinuxContainer?>(null) }

    LaunchedEffect(animateListIn) {
        if (!animateListIn) onListShown()
    }
    LaunchedEffect(Unit) {
        vm.ensureLoaded(context)
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
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(title = stringResource(R.string.terminal_section_sessions))
            Spacer(modifier = Modifier.height(12.dp))
            if (containers.isEmpty()) {
                EmptyStateView(
                    title = stringResource(R.string.terminal_empty_title),
                    body = stringResource(R.string.terminal_empty_body),
                    actionLabel = stringResource(R.string.action_add_distro),
                    onAction = onAddDistro,
                    icon = Icons.Filled.Terminal
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    containers.forEach { c ->
                        ContainerCard(
                            container = c,
                            onOpen = { onOpenSession(c.id) },
                            onStop = { vm.stop(c.id, context) },
                            onGui = { guiNoteFor = c.name },
                            onDelete = { confirmDelete = c },
                            reduceMotion = reduceMotion
                        )
                    }
                }
            }
        }
        if (containers.isNotEmpty()) {
            item {
                KuroPrimaryButton(
                    label = stringResource(R.string.action_add_distro),
                    onClick = onAddDistro,
                    reduceMotion = reduceMotion
                )
            }
        }
        item { Spacer(modifier = Modifier.height(90.dp)) }
    }

    if (guiNoteFor != null) {
        KuroDialog(
            title = stringResource(R.string.terminal_action_gui),
            body = "Kuro View (GUI output for “$guiNoteFor”) arrives in Step 8. Nothing was started.",
            confirmLabel = stringResource(R.string.distro_got_it),
            onConfirm = { guiNoteFor = null },
            onDismiss = { guiNoteFor = null },
            reduceMotion = reduceMotion
        )
    }

    val pendingDelete = confirmDelete
    if (pendingDelete != null) {
        KuroDialog(
            title = stringResource(R.string.terminal_confirm_delete_title),
            body = stringResource(R.string.terminal_confirm_delete_body),
            confirmLabel = stringResource(R.string.terminal_delete_confirm),
            dismissLabel = stringResource(R.string.terminal_delete_keep),
            onConfirm = {
                vm.delete(context, pendingDelete.id)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
            reduceMotion = reduceMotion
        )
    }

    val freed = deleted
    if (freed != null) {
        KuroDialog(
            title = stringResource(R.string.terminal_freed_title),
            body = "${freed.first}: ${formatBytes(freed.second)}",
            confirmLabel = stringResource(R.string.distro_got_it),
            onConfirm = { vm.clearDeleted() },
            onDismiss = { vm.clearDeleted() },
            reduceMotion = reduceMotion
        )
    }

    val err = startError
    if (err != null) {
        KuroDialog(
            title = err.second.title,
            body = err.second.detail,
            confirmLabel = stringResource(R.string.distro_got_it),
            onConfirm = { vm.clearStartError() },
            onDismiss = { vm.clearStartError() },
            reduceMotion = reduceMotion
        )
    }
}

@Composable
private fun ContainerCard(
    container: LinuxContainer,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    onGui: () -> Unit,
    onDelete: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Distro logo placeholder: initial in a tonal circle.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = container.distroName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = container.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = container.distroName +
                            " • " + formatBytes(container.sizeBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TypeBadge(type = container.type)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (container.status == ContainerStatus.RUNNING)
                    stringResource(R.string.terminal_status_running)
                else
                    stringResource(R.string.terminal_status_stopped),
                style = MaterialTheme.typography.labelMedium,
                color = if (container.status == ContainerStatus.RUNNING)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    KuroPrimaryButton(
                        label = stringResource(R.string.terminal_action_open),
                        onClick = onOpen,
                        reduceMotion = reduceMotion
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    KuroOutlineButton(
                        label = stringResource(R.string.terminal_action_stop),
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = container.status == ContainerStatus.RUNNING,
                        reduceMotion = reduceMotion
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    if (container.withGui) {
                        // GUI-mode containers: disabled Desktop action, labeled.
                        KuroOutlineButton(
                            label = stringResource(R.string.terminal_action_desktop),
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            reduceMotion = reduceMotion
                        )
                    } else {
                        KuroOutlineButton(
                            label = stringResource(R.string.terminal_action_gui),
                            onClick = onGui,
                            modifier = Modifier.fillMaxWidth(),
                            reduceMotion = reduceMotion
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    KuroOutlineButton(
                        label = stringResource(R.string.terminal_action_delete),
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        reduceMotion = reduceMotion
                    )
                }
            }
            if (container.withGui) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.terminal_desktop_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TypeBadge(type: ContainerType, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (type == ContainerType.PROOT)
                stringResource(R.string.terminal_type_proot)
            else
                stringResource(R.string.terminal_type_chroot),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
