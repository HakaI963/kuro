package com.kuro.app.ui.screens.details

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.core.utils.formatBytes
import com.kuro.app.core.utils.formatDate
import com.kuro.app.core.utils.formatRelative
import com.kuro.app.domain.models.CloneStatus
import com.kuro.app.ui.components.EmptyStateView
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroDialog
import com.kuro.app.ui.components.KuroOutlineButton
import com.kuro.app.ui.components.StatusBadge

class DetailsVmFactory(private val id: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CloneDetailsViewModel(id) as T
    }
}

@Composable
fun CloneDetailsScreen(
    cloneId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val factory = remember(cloneId) { DetailsVmFactory(cloneId) }
    val vm: CloneDetailsViewModel = viewModel(factory = factory, key = "details-$cloneId")
    val clone by vm.clone.collectAsState()
    val events by vm.relatedEvents.collectAsState()
    val showDelete by vm.showDelete.collectAsState()
    val showOpened by vm.showOpened.collectAsState()
    val showSettings by vm.showSettings.collectAsState()

    val c = clone
    if (c == null) {
        EmptyStateView(
            title = stringResource(R.string.state_failed_title),
            body = stringResource(R.string.state_failed_body),
            actionLabel = stringResource(R.string.state_retry),
            onAction = onBack,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(Color(c.labelColorArgb).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = c.iconLetter, style = MaterialTheme.typography.headlineMedium, color = Color(c.labelColorArgb))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = c.name, style = MaterialTheme.typography.headlineSmall)
                    Text(text = c.sourceAppName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    StatusBadge(status = c.status)
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    label = stringResource(R.string.details_open),
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = vm::mockOpen,
                    modifier = Modifier.weight(1f),
                    primary = true
                )
                ActionButton(
                    label = if (c.status == CloneStatus.FROZEN) stringResource(R.string.details_unfreeze) else stringResource(R.string.details_freeze),
                    icon = { Icon(Icons.Filled.Pause, contentDescription = null) },
                    onClick = vm::toggleFreeze,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    label = stringResource(R.string.details_clone_settings),
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = vm::openSettings,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = stringResource(R.string.details_delete),
                    icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = vm::askDelete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            KuroCard(title = stringResource(R.string.details_overview)) {
                DetailRow(label = stringResource(R.string.details_created), value = formatDate(c.createdAtMillis))
                DetailRow(label = stringResource(R.string.details_profile_type), value = c.isolationMode.name.lowercase().replaceFirstChar { it.uppercase() })
                DetailRow(label = stringResource(R.string.details_storage), value = formatBytes(c.storageBytes))
                DetailRow(
                    label = stringResource(R.string.details_last_launched),
                    value = if (c.lastLaunchedMillis != null) formatRelative(c.lastLaunchedMillis) else "—"
                )
            }
        }
        item {
            KuroCard(title = stringResource(R.string.details_recent_activity)) {
                if (events.isEmpty()) {
                    Text(text = stringResource(R.string.activity_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    events.forEach { ev ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = ev.detail, style = MaterialTheme.typography.bodyMedium)
                                Text(text = formatRelative(ev.timestampMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showDelete) {
        KuroDialog(
            title = stringResource(R.string.details_confirm_delete_title),
            body = stringResource(R.string.details_confirm_delete_body),
            confirmLabel = stringResource(R.string.details_confirm_delete_confirm),
            dismissLabel = stringResource(R.string.details_confirm_delete_dismiss),
            onConfirm = { vm.confirmDelete(onDeleted) },
            onDismiss = vm::dismissDelete
        )
    }
    if (showOpened) {
        KuroDialog(
            title = stringResource(R.string.details_mock_opened),
            body = stringResource(R.string.details_mock_opened_body),
            confirmLabel = stringResource(R.string.details_ok),
            onConfirm = vm::dismissOpened,
            onDismiss = vm::dismissOpened
        )
    }
    if (showSettings) {
        com.kuro.app.ui.components.KuroBottomSheet(onDismiss = vm::dismissSettings) {
            Text(text = stringResource(R.string.details_quick_actions), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            com.kuro.app.ui.components.KuroSwitchRow(
                title = stringResource(R.string.add_clone_toggle_notifications),
                description = stringResource(R.string.add_clone_toggle_notifications_desc),
                checked = c.notificationsEnabled,
                onCheckedChange = vm::setNotifications
            )
            com.kuro.app.ui.components.KuroSwitchRow(
                title = stringResource(R.string.add_clone_toggle_separate_data),
                description = stringResource(R.string.add_clone_toggle_separate_data_desc),
                checked = c.separateData,
                onCheckedChange = vm::setSeparateData
            )
            com.kuro.app.ui.components.KuroSwitchRow(
                title = stringResource(R.string.add_clone_toggle_hidden),
                description = stringResource(R.string.add_clone_toggle_hidden_desc),
                checked = c.hiddenFromLauncher,
                onCheckedChange = vm::setHidden
            )
            com.kuro.app.ui.components.KuroSwitchRow(
                title = stringResource(R.string.add_clone_toggle_biometric),
                description = stringResource(R.string.add_clone_toggle_biometric_desc),
                checked = c.biometricLock,
                onCheckedChange = vm::setBiometric
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    if (primary) {
        androidx.compose.material3.Button(onClick = onClick, modifier = modifier.height(48.dp), shape = MaterialTheme.shapes.medium) {
            icon()
            Spacer(modifier = Modifier.width(6.dp))
            Text(label)
        }
    } else {
        KuroOutlineButton(label = label, onClick = onClick, modifier = modifier.fillMaxWidth())
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
