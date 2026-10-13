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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.core.utils.formatBytes
import com.kuro.engine.EngineStatus
import com.kuro.app.ui.components.AppSourceCard
import com.kuro.app.ui.components.EmptyStateView
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroPrimaryButton
import com.kuro.app.ui.components.KuroSearchBar
import com.kuro.app.ui.components.KuroSecondaryButton
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
    val reduceMotion by vm.reduceMotion.collectAsState(initial = false)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        StepIndicator(step = state.step)
        Spacer(modifier = Modifier.height(8.dp))
        when (state.step) {
            AddCloneStep.SOURCE -> SourceStep(
                state = state,
                reduceMotion = reduceMotion,
                onQuery = vm::setQuery,
                onSelect = vm::selectApp,
                onSourceType = vm::setSourceType,
                onApkPicked = vm::setApkFile,
                onRefresh = vm::refreshApps,
                onContinue = { vm.goToOptions() }
            )
            AddCloneStep.OPTIONS -> OptionsStep(
                state = state,
                onName = vm::setName,
                onColor = vm::setColor,
                onBack = vm::backToSource,
                onCreate = vm::startCreation
            )
            AddCloneStep.PROGRESS -> ProgressStepUi(
                state = state,
                onBack = vm::backToOptions,
                onRetry = vm::retry,
                onDone = onDone,
                onOpen = onOpenClone
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
    reduceMotion: Boolean,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
    onSourceType: (CloneSourceType) -> Unit,
    onApkPicked: (uriString: String, displayName: String, sizeBytes: Long?) -> Unit,
    onRefresh: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    // Real system file picker (SAF). The picked file's real name + size come
    // from the ContentResolver — nothing is copied or installed here.
    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            var displayName = uri.lastPathSegment ?: "app.apk"
            var sizeBytes: Long? = null
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                        if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx).takeIf { it >= 0 }
                    }
                }
            }
            onApkPicked(uri.toString(), displayName, sizeBytes)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(text = stringResource(R.string.add_clone_source_title), style = MaterialTheme.typography.titleLarge)
        Text(text = stringResource(R.string.add_clone_source_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))

        // Source type selector. Installed-apps path below is Stage 1 behavior.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SourceTypeCard(
                    label = stringResource(R.string.clone_source_installed),
                    selected = state.sourceType == CloneSourceType.INSTALLED,
                    onClick = { onSourceType(CloneSourceType.INSTALLED) }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                SourceTypeCard(
                    label = stringResource(R.string.clone_source_apk),
                    selected = state.sourceType == CloneSourceType.APK,
                    onClick = { onSourceType(CloneSourceType.APK) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (state.sourceType == CloneSourceType.APK) {
            ApkSourceStep(
                displayName = state.apkDisplayName,
                sizeBytes = state.apkSizeBytes,
                onPick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) },
                onContinue = onContinue
            )
            Spacer(modifier = Modifier.height(12.dp))
            return
        }

        if (state.engineStatus !is EngineStatus.Ready) {
            EngineNotReadyCard(status = state.engineStatus)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                KuroSearchBar(value = state.query, onValueChange = onQuery)
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = stringResource(R.string.add_clone_refresh))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (state.appsLoading) {
            Text(
                text = stringResource(R.string.state_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        val list = state.filteredApps
        if (!state.appsLoading && list.isEmpty()) {
            EmptyStateView(
                title = stringResource(R.string.add_clone_no_apps_title),
                body = stringResource(R.string.add_clone_no_apps_body),
                actionLabel = stringResource(R.string.add_clone_refresh),
                onAction = onRefresh
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(list, key = { it.packageName }) { app ->
                    AppSourceCard(
                        app = app,
                        selected = state.selectedAppPackage == app.packageName,
                        onClick = { onSelect(app.packageName) },
                        reduceMotion = reduceMotion
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        KuroPrimaryButton(
            label = stringResource(R.string.add_clone_continue),
            onClick = onContinue,
            enabled = state.selectedAppPackage != null
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SourceTypeCard(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApkSourceStep(
    displayName: String?,
    sizeBytes: Long?,
    onPick: () -> Unit,
    onContinue: () -> Unit
) {
    KuroSecondaryButton(
        label = stringResource(R.string.clone_apk_pick),
        onClick = onPick
    )
    if (displayName != null) {
        Spacer(modifier = Modifier.height(12.dp))
        KuroCard(title = displayName, subtitle = sizeBytes?.let { formatBytes(it) }) {
            Text(
                text = stringResource(R.string.clone_apk_step7_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        KuroPrimaryButton(
            label = stringResource(R.string.add_clone_continue),
            onClick = onContinue,
            enabled = true
        )
    }
}

@Composable
private fun EngineNotReadyCard(status: EngineStatus) {
    val body = when (status) {
        is EngineStatus.Incompatible ->
            "Engine not compatible with Android ${status.androidVersion} on this device"
        is EngineStatus.Error -> status.message
        else -> stringResource(R.string.add_clone_engine_not_ready_body)
    }
    KuroCard(
        title = stringResource(R.string.add_clone_engine_not_ready_title),
        subtitle = body
    ) { }
}

@Composable
private fun OptionsStep(
    state: AddCloneUiState,
    onName: (String) -> Unit,
    onColor: (Long) -> Unit,
    onBack: () -> Unit,
    onCreate: () -> Unit
) {
    // APK files have no install engine until Step 7: honest state, no Create.
    if (state.sourceType == CloneSourceType.APK) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                KuroCard(
                    title = state.apkDisplayName ?: stringResource(R.string.clone_source_apk),
                    subtitle = state.apkSizeBytes?.let { formatBytes(it) }
                ) {
                    Text(
                        text = stringResource(R.string.clone_apk_step7_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.clone_apk_step7_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            item {
                KuroSecondaryButton(label = stringResource(R.string.add_clone_back), onClick = onBack)
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
        return
    }
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
            if (state.engineStatus !is EngineStatus.Ready) {
                EngineNotReadyCard(status = state.engineStatus)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    KuroSecondaryButton(label = stringResource(R.string.add_clone_back), onClick = onBack)
                }
                Box(modifier = Modifier.weight(1f)) {
                    KuroPrimaryButton(
                        label = stringResource(R.string.add_clone_create),
                        onClick = onCreate,
                        enabled = state.canCreate
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ProgressStepUi(
    state: AddCloneUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onOpen: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        when (state.status) {
            AddCloneStatus.RUNNING, AddCloneStatus.IDLE -> {
                ProgressPanel(
                    title = stringResource(R.string.add_clone_creating_title),
                    subtitle = if (state.progressMessage.isNotBlank()) state.progressMessage
                    else stringResource(R.string.add_clone_creating_subtitle),
                    progress = state.progress,
                    steps = listOf(
                        ProgressStep(stringResource(R.string.add_clone_progress_copy), done = state.progress > 0.5f, active = state.progress <= 0.5f),
                        ProgressStep(stringResource(R.string.add_clone_progress_verify), done = state.progress >= 1f, active = state.progress in 0.5f..0.99f),
                        ProgressStep(stringResource(R.string.add_clone_progress_done), done = false, active = state.progress >= 1f)
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
                    body = state.errorMessage ?: stringResource(R.string.add_clone_error_body),
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
