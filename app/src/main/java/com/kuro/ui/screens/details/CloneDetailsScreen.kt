package com.kuro.app.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.kuro.app.core.utils.formatDate
import com.kuro.app.core.utils.formatRelative
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
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false
) {
    val factory = remember(cloneId) { DetailsVmFactory(cloneId) }
    val vm: CloneDetailsViewModel = viewModel(factory = factory, key = "details-$cloneId")
    val clone by vm.clone.collectAsState()
    val events by vm.relatedEvents.collectAsState()
    val showDelete by vm.showDelete.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message!!)
            vm.clearMessage()
        }
    }

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

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { _ ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp),
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
                        Text(
                            text = c.name,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier
                        )
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
                        onClick = vm::openClone,
                        modifier = Modifier.weight(1f),
                        primary = true
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
                    DetailRow(label = stringResource(R.string.details_package), value = c.sourceAppId)
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
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }

    if (showDelete) {
        KuroDialog(
            title = stringResource(R.string.details_confirm_delete_title),
            body = stringResource(R.string.details_confirm_delete_body),
            confirmLabel = stringResource(R.string.details_confirm_delete_confirm),
            dismissLabel = stringResource(R.string.details_confirm_delete_dismiss),
            onConfirm = { vm.confirmDelete(onDeleted) },
            onDismiss = vm::dismissDelete,
            reduceMotion = reduceMotion
        )
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
        // Step 3c: primary uses tonal primary system (56dp, 16dp corners).
        // Keep launch flow untouched; visual only.
        com.kuro.app.ui.components.KuroPrimaryButton(
            label = label,
            onClick = onClick,
            modifier = modifier
        )
    } else {
        // Step 3c: destructive uses error-container tonal.
        com.kuro.app.ui.components.KuroDestructiveButton(
            label = label,
            onClick = onClick,
            modifier = modifier
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
