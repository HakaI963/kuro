package com.kuro.app.ui.screens.addclone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.domain.models.IsolationMode
import com.kuro.app.ui.components.AppSourceCard
import com.kuro.app.ui.components.EmptyStateView
import com.kuro.app.ui.components.KuroPrimaryButton
import com.kuro.app.ui.components.KuroSecondaryButton
import com.kuro.app.ui.components.KuroSwitchRow
import com.kuro.app.ui.components.ProgressPanel
import com.kuro.app.ui.components.ProgressStep

private val labelColors = listOf(0xFF7C6CFF, 0xFF49D6C2, 0xFF5DB2FF, 0xFF3DDC97, 0xFFFFC861, 0xFFFF6B6B)

@Composable
fun AddCloneScreen(
    onDone: () -> Unit,
    onOpenClone: (String) -> Unit,
    modifier: Modifier = Modifier,
    vm: AddCloneViewModel = viewModel()
) {
    val state by vm.ui.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        StepIndicator(step = state.step)
        Spacer(modifier = Modifier.height(8.dp))
        when (state.step) {
            AddCloneStep.SOURCE -> SourceStep(
                state = state,
                onTab = vm::setTab,
                onSelect = vm::selectApp,
                onContinue = {
                    val ok = vm.goToOptions()
                    if (!ok) { /* handled via disabled button */ }
                }
            )
            AddCloneStep.OPTIONS -> OptionsStep(
                state = state,
                onName = vm::setName,
                onColor = vm::setColor,
                onMode = vm::setMode,
                onNotif = vm::setNotifications,
                onSeparate = vm::setSeparateData,
                onHidden = vm::setHidden,
                onBiometric = vm::setBiometric,
                onBack = vm::backToSource,
                onCreate = { vm.startCreation(false) }
            )
            AddCloneStep.PROGRESS -> ProgressStepUi(
                state = state,
                onBack = vm::backToOptions,
                onRetry = vm::retry,
                onDone = onDone,
                onOpen = onOpenClone,
                onSimulateError = { vm.startCreation(true) }
            )
        }
    }
}

@Composable
private fun StepIndicator(step: AddCloneStep) {
    val labels = listOf(
        stringResource(R.string.add_clone_step_source),
        stringResource(R.string.add_clone_step_options),
        stringResource(R.string.add_clone_step_review)
    )
    val current = when (step) {
        AddCloneStep.SOURCE -> 0
        AddCloneStep.OPTIONS -> 1
        AddCloneStep.PROGRESS -> 2
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        labels.forEachIndexed { i, label ->
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${i + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (i <= current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == current) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SourceStep(
    state: AddCloneUiState,
    onTab: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(text = stringResource(R.string.add_clone_source_title), style = MaterialTheme.typography.titleLarge)
        Text(text = stringResource(R.string.add_clone_source_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        TabRow(selectedTabIndex = state.sourceTab) {
            Tab(selected = state.sourceTab == 0, onClick = { onTab(0) }, text = { Text(stringResource(R.string.add_clone_tab_installed)) })
            Tab(selected = state.sourceTab == 1, onClick = { onTab(1) }, text = { Text(stringResource(R.string.add_clone_tab_apk)) })
        }
        Spacer(modifier = Modifier.height(12.dp))
        val list = if (state.sourceTab == 0) state.installedApps else state.apkApps
        if (state.sourceTab == 1) {
            Text(
                text = stringResource(R.string.add_clone_apk_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(list, key = { it.id }) { app ->
                AppSourceCard(app = app, selected = state.selectedAppId == app.id, onClick = { onSelect(app.id) })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        KuroPrimaryButton(
            label = stringResource(R.string.add_clone_continue),
            onClick = onContinue,
            enabled = state.selectedAppId != null
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun OptionsStep(
    state: AddCloneUiState,
    onName: (String) -> Unit,
    onColor: (Long) -> Unit,
    onMode: (IsolationMode) -> Unit,
    onNotif: (Boolean) -> Unit,
    onSeparate: (Boolean) -> Unit,
    onHidden: (Boolean) -> Unit,
    onBiometric: (Boolean) -> Unit,
    onBack: () -> Unit,
    onCreate: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = stringResource(R.string.add_clone_options_title), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.add_clone_options_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text(text = stringResource(R.string.add_clone_name_label), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = state.cloneName,
                onValueChange = onName,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.add_clone_name_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            Text(text = stringResource(R.string.add_clone_tag_label), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                labelColors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                width = if (state.labelColor == c) 2.dp else 0.dp,
                                color = if (state.labelColor == c) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onColor(c) }
                    )
                }
            }
        }
        item {
            Text(text = stringResource(R.string.add_clone_isolation_label), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            ModeCard(
                selected = state.isolationMode == IsolationMode.STANDARD,
                title = stringResource(R.string.add_clone_mode_standard),
                desc = stringResource(R.string.add_clone_mode_standard_desc),
                onClick = { onMode(IsolationMode.STANDARD) }
            )
        }
        item {
            ModeCard(
                selected = state.isolationMode == IsolationMode.PRIVATE,
                title = stringResource(R.string.add_clone_mode_private),
                desc = stringResource(R.string.add_clone_mode_private_desc),
                onClick = { onMode(IsolationMode.PRIVATE) }
            )
        }
        item {
            ModeCard(
                selected = state.isolationMode == IsolationMode.WORK,
                title = stringResource(R.string.add_clone_mode_work),
                desc = stringResource(R.string.add_clone_mode_work_desc),
                onClick = { onMode(IsolationMode.WORK) }
            )
        }
        item {
            ModeCard(
                selected = state.isolationMode == IsolationMode.EXPERIMENTAL,
                title = stringResource(R.string.add_clone_mode_experimental),
                desc = stringResource(R.string.add_clone_mode_experimental_desc),
                onClick = { onMode(IsolationMode.EXPERIMENTAL) }
            )
        }
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    KuroSwitchRow(title = stringResource(R.string.add_clone_toggle_notifications), description = stringResource(R.string.add_clone_toggle_notifications_desc), checked = state.notifications, onCheckedChange = onNotif)
                    KuroSwitchRow(title = stringResource(R.string.add_clone_toggle_separate_data), description = stringResource(R.string.add_clone_toggle_separate_data_desc), checked = state.separateData, onCheckedChange = onSeparate)
                    KuroSwitchRow(title = stringResource(R.string.add_clone_toggle_hidden), description = stringResource(R.string.add_clone_toggle_hidden_desc), checked = state.hidden, onCheckedChange = onHidden)
                    KuroSwitchRow(title = stringResource(R.string.add_clone_toggle_biometric), description = stringResource(R.string.add_clone_toggle_biometric_desc), checked = state.biometric, onCheckedChange = onBiometric)
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    KuroSecondaryButton(label = stringResource(R.string.add_clone_back), onClick = onBack)
                }
                Box(modifier = Modifier.weight(1f)) {
                    KuroPrimaryButton(label = stringResource(R.string.add_clone_create), onClick = onCreate)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ModeCard(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ProgressStepUi(
    state: AddCloneUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onOpen: (String) -> Unit,
    onSimulateError: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        when (state.status) {
            AddCloneStatus.RUNNING, AddCloneStatus.IDLE -> {
                ProgressPanel(
                    title = stringResource(R.string.add_clone_creating_title),
                    subtitle = stringResource(R.string.add_clone_creating_subtitle),
                    progress = state.progress,
                    steps = listOf(
                        ProgressStep(stringResource(R.string.add_clone_step_prepare), done = state.progressStep > 0, active = state.progressStep == 0),
                        ProgressStep(stringResource(R.string.add_clone_step_isolate), done = state.progressStep > 1, active = state.progressStep == 1),
                        ProgressStep(stringResource(R.string.add_clone_step_finish), done = false, active = state.progressStep == 2)
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                KuroSecondaryButton(label = stringResource(R.string.add_clone_cancel), onClick = onBack)
            }
            AddCloneStatus.SUCCESS -> {
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.add_clone_success_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(R.string.add_clone_success_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(20.dp))
                KuroPrimaryButton(label = stringResource(R.string.add_clone_open), onClick = { state.createdCloneId?.let(onOpen) }, enabled = state.createdCloneId != null)
                Spacer(modifier = Modifier.height(10.dp))
                KuroSecondaryButton(label = stringResource(R.string.add_clone_done), onClick = onDone)
            }
            AddCloneStatus.ERROR -> {
                EmptyStateView(
                    title = stringResource(R.string.add_clone_error_title),
                    body = stringResource(R.string.add_clone_error_body),
                    actionLabel = stringResource(R.string.add_clone_retry),
                    onAction = onRetry,
                    icon = Icons.Filled.ErrorOutline
                )
                Spacer(modifier = Modifier.height(8.dp))
                KuroSecondaryButton(label = stringResource(R.string.add_clone_back), onClick = onBack)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
