package com.kuro.app.ui.screens.terminal

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kuro.app.R
import com.kuro.app.core.system.StoragePaths
import com.kuro.app.data.containers.ContainerType
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroDialog
import com.kuro.app.ui.components.KuroPrimaryButton
import com.kuro.app.ui.components.KuroSecondaryButton
import com.kuro.app.ui.components.KuroSwitchRow

private enum class DistroStep { TYPE, DISTRO, OPTIONS, CREATE }

private enum class InterfaceMode { TERMINAL_ONLY, TERMINAL_GUI }

@Composable
fun AddDistroScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false
) {
    var step by remember { mutableStateOf(DistroStep.TYPE) }
    var type by remember { mutableStateOf(ContainerType.PROOT) }
    var distroId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var bridgeOn by remember { mutableStateOf(true) }
    var interfaceMode by remember { mutableStateOf(InterfaceMode.TERMINAL_ONLY) }
    var keepAlive by remember { mutableStateOf(true) }
    var wakeLock by remember { mutableStateOf(false) }
    var shell by remember { mutableStateOf("bash") }
    var showStep5 by remember { mutableStateOf(false) }

    val distro = distroId?.let(DistroCatalog::byId)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DistroStepIndicator(step = step)
        Spacer(modifier = Modifier.height(8.dp))
        when (step) {
            DistroStep.TYPE -> TypeStep(
                selected = type,
                onSelect = { type = it },
                onContinue = { step = DistroStep.DISTRO }
            )
            DistroStep.DISTRO -> DistroStepUi(
                selectedId = distroId,
                onSelect = { distroId = it },
                onBack = { step = DistroStep.TYPE },
                onContinue = { step = DistroStep.OPTIONS }
            )
            DistroStep.OPTIONS -> OptionsStepUi(
                name = name,
                onName = { name = it },
                bridgeOn = bridgeOn,
                onBridge = { bridgeOn = it },
                interfaceMode = interfaceMode,
                onInterface = { interfaceMode = it },
                keepAlive = keepAlive,
                onKeepAlive = { keepAlive = it },
                wakeLock = wakeLock,
                onWakeLock = { wakeLock = it },
                shell = shell,
                onShell = { shell = it },
                type = type,
                onBack = { step = DistroStep.DISTRO },
                onContinue = { step = DistroStep.CREATE }
            )
            DistroStep.CREATE -> CreateStepUi(
                type = type,
                distroName = distro?.name ?: "−",
                distroVersion = distro?.version ?: "−",
                name = name.ifBlank { distro?.name?.lowercase() ?: "container" },
                bridgeOn = bridgeOn,
                interfaceMode = interfaceMode,
                keepAlive = keepAlive,
                wakeLock = wakeLock,
                shell = shell,
                onBack = { step = DistroStep.OPTIONS },
                onCreate = { showStep5 = true }
            )
        }
    }

    if (showStep5) {
        // The ONLY allowed non-functional state: honest, no progress, no success.
        KuroDialog(
            title = stringResource(R.string.distro_step5_title),
            body = stringResource(R.string.distro_step5_body),
            confirmLabel = stringResource(R.string.distro_got_it),
            onConfirm = { showStep5 = false },
            onDismiss = { showStep5 = false },
            reduceMotion = reduceMotion
        )
    }
}

@Composable
private fun DistroStepIndicator(step: DistroStep) {
    val labels = listOf(
        stringResource(R.string.distro_step_type),
        stringResource(R.string.distro_step_distro),
        stringResource(R.string.distro_step_options),
        stringResource(R.string.distro_step_create)
    )
    val current = step.ordinal
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
private fun TypeStep(
    selected: ContainerType,
    onSelect: (ContainerType) -> Unit,
    onContinue: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = stringResource(R.string.distro_type_title), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.distro_type_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SelectableTypeCard(
                title = stringResource(R.string.distro_proot_title),
                desc = stringResource(R.string.distro_proot_desc),
                extra = stringResource(R.string.distro_storage_line_proot),
                selected = selected == ContainerType.PROOT,
                onClick = { onSelect(ContainerType.PROOT) },
                badge = ContainerType.PROOT
            )
        }
        item {
            // Chroot stays selectable: root is requested via KernelSU at creation.
            SelectableTypeCard(
                title = stringResource(R.string.distro_chroot_title),
                desc = stringResource(R.string.distro_chroot_desc),
                extra = stringResource(R.string.distro_chroot_path_note) +
                    ": " + StoragePaths.chrootPreview() +
                    "\n" + stringResource(R.string.distro_chroot_root_note),
                selected = selected == ContainerType.CHROOT,
                onClick = { onSelect(ContainerType.CHROOT) },
                badge = ContainerType.CHROOT
            )
        }
        item {
            KuroPrimaryButton(label = stringResource(R.string.distro_continue), onClick = onContinue)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SelectableTypeCard(
    title: String,
    desc: String,
    extra: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: ContainerType? = null
) {
    Card(
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
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (extra.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (badge != null) TypeBadge(type = badge)
        }
    }
}

@Composable
private fun DistroStepUi(
    selectedId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = stringResource(R.string.distro_pick_title), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.distro_pick_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DistroCatalog.entries.forEach { entry ->
            item {
                SelectableDistroCard(
                    entry = entry,
                    selected = selectedId == entry.id,
                    onClick = { onSelect(entry.id) }
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    KuroSecondaryButton(label = stringResource(R.string.add_clone_back), onClick = onBack)
                }
                Box(modifier = Modifier.weight(1f)) {
                    KuroPrimaryButton(
                        label = stringResource(R.string.distro_continue),
                        onClick = onContinue,
                        enabled = selectedId != null
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SelectableDistroCard(entry: DistroEntry, selected: Boolean, onClick: () -> Unit) {
    Card(
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
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.name + " " + entry.version, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.approxDownloadSize + " • " + entry.arch,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.distro_source) + ": " + entry.sourceUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OptionsStepUi(
    name: String,
    onName: (String) -> Unit,
    bridgeOn: Boolean,
    onBridge: (Boolean) -> Unit,
    interfaceMode: InterfaceMode,
    onInterface: (InterfaceMode) -> Unit,
    keepAlive: Boolean,
    onKeepAlive: (Boolean) -> Unit,
    wakeLock: Boolean,
    onWakeLock: (Boolean) -> Unit,
    shell: String,
    onShell: (String) -> Unit,
    type: ContainerType,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = stringResource(R.string.distro_options_title), style = MaterialTheme.typography.titleLarge)
        }
        item {
            Text(text = stringResource(R.string.distro_name_label), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.distro_name_hint)) },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
        }
        item {
            KuroCard(title = stringResource(R.string.distro_bridge_title)) {
                KuroSwitchRow(
                    title = stringResource(R.string.distro_bridge_title),
                    description = stringResource(R.string.distro_bridge_desc),
                    checked = bridgeOn,
                    onCheckedChange = onBridge
                )
            }
        }
        item {
            Text(text = stringResource(R.string.distro_interface_label), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    SelectableTypeCard(
                        title = stringResource(R.string.distro_interface_terminal),
                        desc = "",
                        extra = "",
                        selected = interfaceMode == InterfaceMode.TERMINAL_ONLY,
                        onClick = { onInterface(InterfaceMode.TERMINAL_ONLY) }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    SelectableTypeCard(
                        title = stringResource(R.string.distro_interface_gui),
                        desc = "",
                        extra = "",
                        selected = interfaceMode == InterfaceMode.TERMINAL_GUI,
                        onClick = { onInterface(InterfaceMode.TERMINAL_GUI) }
                    )
                }
            }
        }
        item {
            KuroCard {
                KuroSwitchRow(
                    title = stringResource(R.string.distro_keep_alive),
                    description = stringResource(R.string.distro_keep_alive_desc),
                    checked = keepAlive,
                    onCheckedChange = onKeepAlive
                )
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSwitchRow(
                    title = stringResource(R.string.distro_wakelock),
                    description = stringResource(R.string.distro_wakelock_desc),
                    checked = wakeLock,
                    onCheckedChange = onWakeLock
                )
            }
        }
        item {
            Text(text = stringResource(R.string.distro_shell_label), style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("bash", "zsh").forEach { option ->
                    Box(modifier = Modifier.weight(1f)) {
                        SelectableTypeCard(
                            title = option,
                            desc = "",
                            extra = "",
                            selected = shell == option,
                            onClick = { onShell(option) }
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = if (type == ContainerType.PROOT)
                    stringResource(R.string.distro_storage_line_proot)
                else
                    stringResource(R.string.distro_storage_line_chroot),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.distro_isolation_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    KuroSecondaryButton(label = stringResource(R.string.add_clone_back), onClick = onBack)
                }
                Box(modifier = Modifier.weight(1f)) {
                    KuroPrimaryButton(label = stringResource(R.string.distro_continue), onClick = onContinue)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CreateStepUi(
    type: ContainerType,
    distroName: String,
    distroVersion: String,
    name: String,
    bridgeOn: Boolean,
    interfaceMode: InterfaceMode,
    keepAlive: Boolean,
    wakeLock: Boolean,
    shell: String,
    onBack: () -> Unit,
    onCreate: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = stringResource(R.string.distro_summary_title), style = MaterialTheme.typography.titleLarge)
        }
        item {
            KuroCard(title = name) {
                SummaryRow(distroName + " " + distroVersion)
                SummaryRow(if (type == ContainerType.PROOT) "proot" else "chroot")
                SummaryRow(
                    if (interfaceMode == InterfaceMode.TERMINAL_ONLY)
                        stringResource(R.string.distro_interface_terminal)
                    else stringResource(R.string.distro_interface_gui)
                )
                SummaryRow("shell: $shell")
                SummaryRow(
                    (if (bridgeOn) "✓ " else "— ") + stringResource(R.string.distro_bridge_title)
                )
                SummaryRow(
                    (if (keepAlive) "✓ " else "— ") + stringResource(R.string.distro_keep_alive)
                )
                SummaryRow(
                    (if (wakeLock) "✓ " else "— ") + stringResource(R.string.distro_wakelock)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    KuroSecondaryButton(label = stringResource(R.string.add_clone_back), onClick = onBack)
                }
                Box(modifier = Modifier.weight(1f)) {
                    KuroPrimaryButton(label = stringResource(R.string.distro_create), onClick = onCreate)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SummaryRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
}
