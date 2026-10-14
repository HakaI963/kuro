package com.ivarna.fluxlinux.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ivarna.fluxlinux.core.data.Distro
import com.ivarna.fluxlinux.core.data.DistroRepository
import com.ivarna.fluxlinux.core.system.SystemMetrics
import com.ivarna.fluxlinux.core.system.SystemMetricsReader
import com.ivarna.fluxlinux.core.terminal.SessionRegistry
import com.ivarna.fluxlinux.core.terminal.TerminalLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Step 6: Kuro System tab — real device metrics (2.5s refresh while visible),
 * containers summary, clones summary, recent install events. Every value is
 * measured; unavailable tiles render "−". Opaque root, no fake data.
 */
@Composable
fun KuroSystemTab(
    onOpenTerminalTab: () -> Unit,
    onOpenClonesTab: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    onNavigateToInstall: (Distro) -> Unit,
    onOpenTerminal: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var metrics by remember { mutableStateOf<SystemMetrics?>(null) }

    // 2.5s refresh while this tab is composed; cancelled on leave.
    LaunchedEffect(Unit) {
        while (isActive) {
            val next = withContext(Dispatchers.IO) {
                runCatching { SystemMetricsReader.readCycle(context.filesDir) }.getOrNull()
            }
            if (next != null) metrics = next
            delay(2500L)
        }
    }

    val revision by SessionRegistry.revision.collectAsState()
    val runningSessions = remember(revision) { SessionRegistry.sessions().size }
    val installedIds = remember {
        DistroRepository.supportedDistros
            .filter {
                runCatching { TerminalLauncher.isDistroInstalledOnFs(context, it.id) }
                    .getOrDefault(false)
            }
            .map { it.id }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item {
            Text(
                text = "System",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Device and spaces at a glance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            SectionCard(title = "System metrics") {
                val m = metrics
                MetricRow("RAM", m?.let { ramLabel(it.ramUsedBytes, it.ramTotalBytes) } ?: "−")
                MetricRow("CPU load", m?.cpuLoad01?.let { "${(it * 100).toInt()}%" } ?: "−")
                MetricRow("CPU temp", m?.cpuTempC?.let { "${it.toInt()}°C" } ?: "−")
                MetricRow("GPU", m?.gpuRenderer ?: "−")
                MetricRow(
                    "Storage",
                    m?.let { storageLabel(it.storageFreeBytes, it.storageTotalBytes) } ?: "−"
                )
                MetricRow("Kuro data", m?.appDataBytes?.let { formatBytes(it) } ?: "−")
                MetricRow("Kernel", m?.kernelLabel ?: "−")
            }
        }
        item {
            SectionCard(title = "Containers") {
                Text(
                    text = "${installedIds.size} installed • $runningSessions running",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    TextButton(onClick = onOpenTerminalTab) { Text("Manage distros") }
                }
            }
        }
        item {
            SectionCard(title = "Clones") {
                val clonesService = remember {
                    runCatching { com.kuro.app.di.ServiceLocator.cloneRepository }.getOrNull()
                }
                if (clonesService == null) {
                    Text(
                        text = "Clone service unavailable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val clones by clonesService.clones.collectAsState()
                    Text(
                        text = "${clones.size} installed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    TextButton(onClick = onOpenClonesTab) { Text("Open Clones") }
                }
            }
        }
        item {
            SectionCard(title = "Recent events") {
                val activityService = remember {
                    runCatching { com.kuro.app.di.ServiceLocator.activityRepository }.getOrNull()
                }
                val events = activityService?.events?.collectAsState()?.value.orEmpty().take(5)
                if (events.isEmpty()) {
                    Text(
                        text = "No events yet — clone activity will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    events.forEach { event ->
                        Text(
                            text = "${event.type}: ${event.cloneName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.0f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}

private fun ramLabel(used: Long?, total: Long?): String {
    if (used == null || total == null || total <= 0) return "−"
    return "${formatBytes(used)} / ${formatBytes(total)}"
}

private fun storageLabel(free: Long?, total: Long?): String {
    if (free == null || total == null || total <= 0) return "−"
    return "${formatBytes(total - free)} used • ${formatBytes(free)} free"
}
