package com.kuro.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kuro.app.core.root.RootChecker
import com.kuro.app.core.root.RootStatus
import com.kuro.app.core.system.BridgeInfo
import com.kuro.app.core.system.StoragePaths
import com.kuro.app.core.system.SystemMetricsReader
import com.kuro.app.core.utils.formatBytes
import com.kuro.app.data.containers.ContainerStore
import com.kuro.app.data.containers.ContainerStatus
import com.kuro.app.domain.models.ThemeMode
import com.kuro.engine.EngineStatus
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroSecondaryButton
import com.kuro.app.ui.components.KuroSettingRow
import com.kuro.app.ui.components.KuroSwitchRow
import com.kuro.app.ui.components.firstLoadFade

@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(),
    listState: LazyListState = LazyListState(),
    animateListIn: Boolean = false,
    onListShown: () -> Unit = {}
) {
    val s by vm.settings.collectAsState()
    val engineStatus by vm.engineStatus.collectAsState()
    val rootStatus by RootChecker.status.collectAsState()
    val containers by ContainerStore.containers.collectAsState()
    val context = LocalContext.current

    // Storage sizes are read once off-main; no polling here.
    var prootSize by remember { mutableStateOf<String?>(null) }
    var kuroDataSize by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val filesDir = context.filesDir
        val proot = StoragePaths.prootBase(filesDir)
        val prootBytes = withContext(Dispatchers.IO) { SystemMetricsReader.dirSizeNow(proot) }
        val kuroBytes = withContext(Dispatchers.IO) { SystemMetricsReader.dirSizeNow(filesDir) }
        prootSize = prootBytes?.let { formatBytes(it) }
        kuroDataSize = kuroBytes?.let { formatBytes(it) }
    }

    LaunchedEffect(animateListIn) {
        if (!animateListIn) onListShown()
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .firstLoadFade(animate = animateListIn, reduceMotion = false, onShown = onListShown)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            Text(text = stringResource(R.string.settings_section_engine), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            KuroCard(
                title = stringResource(R.string.settings_engine_title),
                subtitle = null
            ) {
                val (label, detail) = when (val st = engineStatus) {
                    is EngineStatus.Ready -> stringResource(R.string.settings_engine_status_ready) to null
                    is EngineStatus.Uninitialized -> stringResource(R.string.settings_engine_status_uninitialized) to null
                    is EngineStatus.Incompatible ->
                        stringResource(R.string.settings_engine_status_incompatible) to
                            "Engine not compatible with Android ${st.androidVersion} on this device"
                    is EngineStatus.Error -> stringResource(R.string.settings_engine_status_error) to st.message
                }
                Text(text = label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (detail != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(12.dp))
                KuroSecondaryButton(
                    label = stringResource(R.string.settings_engine_retry),
                    onClick = vm::retryEngineInit
                )
                if (com.kuro.app.BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(8.dp))
                    KuroSettingRow(
                        title = stringResource(R.string.settings_engine_diagnostics),
                        description = stringResource(R.string.settings_engine_diagnostics_desc),
                        onClick = onOpenDiagnostics
                    )
                }
            }
        }

        item {
            Text(text = stringResource(R.string.settings_section_root), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            KuroCard(title = stringResource(R.string.settings_root_title)) {
                val (rootLabel, rootColor) = when (rootStatus) {
                    is RootStatus.Granted -> stringResource(R.string.settings_root_granted) to MaterialTheme.colorScheme.primary
                    is RootStatus.NotGranted -> stringResource(R.string.settings_root_not_granted) to MaterialTheme.colorScheme.error
                    is RootStatus.NotDetected -> stringResource(R.string.settings_root_not_detected) to MaterialTheme.colorScheme.onSurfaceVariant
                    is RootStatus.Checking -> stringResource(R.string.settings_root_checking) to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(text = rootLabel, style = MaterialTheme.typography.titleMedium, color = rootColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_root_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                // User-initiated only: this is what pops the KernelSU grant UI.
                KuroSecondaryButton(
                    label = stringResource(R.string.settings_root_request),
                    onClick = { RootChecker.requestRoot() }
                )
            }
        }

        item {
            ChrootHelperCard(
                rootGranted = rootStatus is RootStatus.Granted,
                reduceMotion = false
            )
        }

        item {
            val running = containers.count { it.status == ContainerStatus.RUNNING }
            Text(text = stringResource(R.string.settings_section_sessions), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            KuroCard(title = stringResource(R.string.settings_sessions_title)) {
                Text(
                    text = stringResource(R.string.settings_sessions_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (running == 0) stringResource(R.string.settings_sessions_none)
                    else "$running ${stringResource(R.string.system_running_count)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                KuroSecondaryButton(
                    label = stringResource(R.string.settings_sessions_stop_all),
                    onClick = { vm.stopAllSessions(context) },
                    enabled = running > 0
                )
            }
        }

        item {
            Text(text = stringResource(R.string.settings_section_storage), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            KuroCard {
                KuroSettingRow(
                    title = stringResource(R.string.settings_storage_proot),
                    description = StoragePaths.prootBase(context.filesDir).absolutePath +
                        " • " + (prootSize ?: "…")
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSettingRow(
                    title = stringResource(R.string.settings_storage_chroot),
                    description = StoragePaths.CHROOT_DIR + " • " +
                        stringResource(R.string.settings_storage_free) + ": " +
                        StoragePaths.deviceFreeLabel()
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                if (containers.isEmpty()) {
                    KuroSettingRow(
                        title = stringResource(R.string.settings_storage_none),
                        description = "Kuro data: " + (kuroDataSize ?: "…")
                    )
                } else {
                    containers.forEach { c ->
                        KuroSettingRow(
                            title = c.name,
                            description = StoragePaths.containerSizeLabel(null)
                        )
                    }
                }
            }
        }

        item {
            Text(text = stringResource(R.string.settings_section_bridge), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            KuroCard(title = stringResource(R.string.settings_bridge_title)) {
                Text(
                    text = stringResource(R.string.settings_bridge_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_bridge_placeholder_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                KuroSettingRow(
                    title = stringResource(R.string.settings_bridge_port),
                    description = "${BridgeInfo.LOOPBACK}:${BridgeInfo.PLACEHOLDER_PORT}"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSettingRow(
                    title = stringResource(R.string.settings_bridge_token),
                    description = BridgeInfo.PLACEHOLDER_TOKEN
                )
            }
        }

        item {
            Text(text = stringResource(R.string.settings_section_appearance), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            KuroCard {
                KuroSettingRow(
                    title = stringResource(R.string.settings_theme),
                    description = stringResource(R.string.settings_theme_desc),
                    value = when (s.themeMode) {
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                    },
                    onClick = {
                        val next = when (s.themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.SYSTEM
                        }
                        vm.setTheme(next)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSwitchRow(
                    title = stringResource(R.string.settings_dynamic),
                    description = stringResource(R.string.settings_dynamic_desc),
                    checked = s.dynamicColor,
                    onCheckedChange = vm::setDynamic
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSwitchRow(
                    title = stringResource(R.string.settings_reduce_motion),
                    description = stringResource(R.string.settings_reduce_motion_desc),
                    checked = s.reduceMotion,
                    onCheckedChange = vm::setReduceMotion
                )
            }
        }

        item {
            Text(text = stringResource(R.string.settings_section_behavior), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            KuroCard {
                KuroSwitchRow(
                    title = stringResource(R.string.settings_default_launch),
                    description = stringResource(R.string.settings_default_launch_desc),
                    checked = s.defaultToLibrary,
                    onCheckedChange = vm::setDefaultToLibrary
                )
            }
        }

        item {
            Text(text = stringResource(R.string.settings_section_about), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            KuroCard {
                KuroSettingRow(
                    title = stringResource(R.string.settings_version),
                    description = "1.0.0-stage1 (3)"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSettingRow(
                    title = stringResource(R.string.settings_build),
                    description = stringResource(R.string.settings_build_value)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                LastCrashRow()
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                KuroSettingRow(
                    title = stringResource(R.string.settings_legal),
                    description = stringResource(R.string.settings_legal_body)
                )
            }
        }
        item { Spacer(modifier = Modifier.height(90.dp)) }
    }
}

/**
 * Step 4b: "Last crash" row. Shows timestamp + first 3 trace lines from
 * filesDir/crash.log; tap copies the FULL trace to the clipboard. Clean
 * "no crashes" state when the log is absent.
 */
@Composable
private fun LastCrashRow() {
    val context = LocalContext.current
    var entry by remember { mutableStateOf<com.kuro.app.core.crash.CrashLog.Entry?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entry = withContext(Dispatchers.IO) {
            runCatching {
                com.kuro.app.core.crash.CrashLog.readLast(context.filesDir)
            }.getOrNull()
        }
    }
    if (copied) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000L)
            copied = false
        }
    }
    val snapshot = entry
    if (snapshot == null) {
        KuroSettingRow(
            title = stringResource(R.string.settings_crash_title),
            description = stringResource(R.string.settings_crash_none)
        )
    } else {
        KuroSettingRow(
            title = stringResource(R.string.settings_crash_title),
            description = snapshot.timestampLabel + "\n" + snapshot.preview,
            value = if (copied) stringResource(R.string.settings_crash_copied)
            else stringResource(R.string.settings_crash_copy),
            onClick = {
                runCatching {
                    val manager = context.getSystemService(android.content.ClipboardManager::class.java)
                    manager?.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "kuro-crash",
                            snapshot.trace
                        )
                    )
                    copied = true
                }
            }
        )
    }
}

/**
 * Step 5c: chroot helper row. Shows last gate results (persisted) and a
 * user-initiated "Validate" action (enabled only when root is granted —
 * gates run through su, so they must never fire on their own).
 */
@Composable
private fun ChrootHelperCard(rootGranted: Boolean, reduceMotion: Boolean) {
    val context = LocalContext.current
    var gates by remember {
        mutableStateOf<com.kuro.app.engine.proot.ChrootGates.Results?>(null)
    }
    var running by remember { mutableStateOf(false) }
    fun reload() {
        gates = runCatching {
            com.kuro.app.engine.proot.ChrootGates.readLast(context.filesDir)
        }.getOrNull()
    }
    LaunchedEffect(Unit) { reload() }
    Text(text = stringResource(R.string.settings_chroot_title), style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(12.dp))
    com.kuro.app.ui.components.KuroCard(title = stringResource(R.string.settings_chroot_title)) {
        Text(
            text = stringResource(R.string.settings_chroot_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        val snapshot = gates
        val (label, color) = when {
            snapshot == null -> stringResource(R.string.settings_chroot_not_validated) to
                MaterialTheme.colorScheme.onSurfaceVariant
            snapshot.g1ok && snapshot.g2ok -> (
                stringResource(R.string.settings_chroot_validated) + " • " +
                    (snapshot.helperVersion ?: "")
                ) to MaterialTheme.colorScheme.primary
            else -> stringResource(R.string.settings_chroot_gates_failed) to
                MaterialTheme.colorScheme.error
        }
        Text(text = label, style = MaterialTheme.typography.titleMedium, color = color)
        if (snapshot != null && !(snapshot.g1ok && snapshot.g2ok)) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "g1: ${snapshot.g1detail}\ng2: ${snapshot.g2detail}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        com.kuro.app.ui.components.KuroSecondaryButton(
            label = stringResource(R.string.settings_chroot_validate),
            onClick = {
                if (running) return@KuroSecondaryButton
                running = true
                CoroutineScope(Dispatchers.Main.immediate).launch {
                    gates = withContext(Dispatchers.IO) {
                        runCatching {
                            com.kuro.app.engine.proot.ChrootGates.runGates(context)
                        }.getOrNull()
                    }
                    running = false
                }
            },
            enabled = rootGranted && !running,
            reduceMotion = reduceMotion
        )
    }
}
