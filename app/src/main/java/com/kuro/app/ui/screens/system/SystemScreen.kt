package com.kuro.app.ui.screens.system

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.core.system.SystemMetrics
import com.kuro.app.core.utils.formatBytes
import com.kuro.app.core.utils.formatRelative
import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.CloneStatus
import com.kuro.app.ui.components.EmptyStateView
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.SectionHeader
import com.kuro.app.ui.components.firstLoadFade
import com.kuro.engine.EngineStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SystemScreen(
    modifier: Modifier = Modifier,
    vm: SystemViewModel = viewModel(),
    listState: LazyListState = LazyListState(),
    animateListIn: Boolean = false,
    onListShown: () -> Unit = {}
) {
    val context = LocalContext.current
    val metrics by vm.metrics.collectAsState()
    val clones by vm.clones.collectAsState()
    val events by vm.events.collectAsState()
    val engineStatus by vm.engineStatus.collectAsState()
    val containers by vm.containers.collectAsState()

    LaunchedEffect(animateListIn) {
        if (!animateListIn) onListShown()
    }

    // Live refresh, ~2.5s cycle, WHILE VISIBLE ONLY: this effect lives inside
    // the tab content, so switching tabs drops it from composition and the
    // loop — including an in-flight 1s CPU sample — cancels. Nothing polls
    // when backgrounded. All IO stays off the main thread (see ViewModel).
    LaunchedEffect(Unit) {
        val dir = context.filesDir
        while (isActive) {
            vm.refreshCycle(dir)
            delay(1500L)
        }
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
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(title = stringResource(R.string.system_section_metrics))
            Spacer(modifier = Modifier.height(12.dp))
            MetricsGrid(metrics = metrics)
        }
        item {
            SectionHeader(title = stringResource(R.string.system_section_containers))
            Spacer(modifier = Modifier.height(12.dp))
            ContainersSummaryCard(
                installed = containers.size,
                running = containers.count { it.status == com.kuro.app.data.containers.ContainerStatus.RUNNING }
            )
        }
        item {
            SectionHeader(title = stringResource(R.string.system_section_clones))
            Spacer(modifier = Modifier.height(12.dp))
            ClonesSummaryCard(
                total = clones.size,
                active = clones.count { it.status == CloneStatus.ACTIVE },
                engineStatus = engineStatus
            )
        }
        item {
            SectionHeader(title = stringResource(R.string.system_section_events))
            Spacer(modifier = Modifier.height(12.dp))
            RecentEventsCard(events = events.take(5))
        }

        // ── Future System cards (Step 5+) ──────────────────────────────────
        // Add new summary cards here as their own `item { }` blocks (one per
        // card: title -> content 12dp, sections stay 24dp apart). Nothing is
        // rendered for this section on purpose — no visual clutter.
        // ──────────────────────────────────────────────────────────────────

        item { Spacer(modifier = Modifier.height(90.dp)) }
    }
}

@Composable
private fun MetricsGrid(metrics: SystemMetrics?) {
    // Per-tile isolation: each tile renders "-" from its own null source.
    val na = stringResource(R.string.system_value_unavailable)
    val ram = metrics?.let { m ->
        if (m.ramUsedBytes != null && m.ramTotalBytes != null)
            "${formatBytes(m.ramUsedBytes)} / ${formatBytes(m.ramTotalBytes)}" else null
    } ?: na
    val storage = metrics?.let { m ->
        if (m.storageFreeBytes != null && m.storageTotalBytes != null)
            "${formatBytes(m.storageFreeBytes)} / ${formatBytes(m.storageTotalBytes)}" else null
    } ?: na
    val tiles = listOf(
        stringResource(R.string.system_metric_ram) to ram,
        stringResource(R.string.system_metric_cpu) to
            (metrics?.cpuLoad01?.let { "${(it * 100).toInt()}%" } ?: na),
        stringResource(R.string.system_metric_temp) to
            (metrics?.cpuTempC?.let { String.format("%.1f°C", it) } ?: na),
        stringResource(R.string.system_metric_gpu) to
            (metrics?.gpuRenderer?.take(28) ?: na),
        stringResource(R.string.system_metric_storage) to storage,
        stringResource(R.string.system_metric_kuro_storage) to
            (metrics?.kuroDataBytes?.let { formatBytes(it) } ?: na),
        stringResource(R.string.system_metric_kernel) to
            (metrics?.kernelLabel?.let { k ->
                metrics.arch?.let { "$k • $it" } ?: k
            } ?: na),
        stringResource(R.string.system_metric_uptime) to
            (metrics?.uptimeMillis?.let { formatUptime(it) } ?: na)
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    MetricTile(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatUptime(millis: Long): String {
    val totalMin = millis / 60000L
    val days = totalMin / 1440L
    val hours = (totalMin % 1440L) / 60L
    val mins = totalMin % 60L
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0 || days > 0) append("${hours}h ")
        append("${mins}m")
    }.trim()
}

@Composable
private fun ContainersSummaryCard(installed: Int, running: Int) {
    KuroCard(
        title = "$installed ${stringResource(R.string.system_installed_count)}" +
            " • $running ${stringResource(R.string.system_running_count)}"
    ) {
        if (installed == 0) {
            Text(
                text = stringResource(R.string.system_containers_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ClonesSummaryCard(total: Int, active: Int, engineStatus: EngineStatus) {
    val engineLabel = when (engineStatus) {
        is EngineStatus.Ready -> stringResource(R.string.system_engine_ready)
        is EngineStatus.Error -> stringResource(R.string.settings_engine_status_error)
        is EngineStatus.Incompatible -> stringResource(R.string.settings_engine_status_incompatible)
        is EngineStatus.Uninitialized -> stringResource(R.string.settings_engine_status_uninitialized)
    }
    KuroCard(title = "$total • $active ${stringResource(R.string.system_running_count)}") {
        Text(
            text = engineLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun RecentEventsCard(events: List<ActivityEvent>) {
    KuroCard(title = null) {
        if (events.isEmpty()) {
            EmptyStateView(
                title = stringResource(R.string.system_events_empty),
                body = stringResource(R.string.system_events_empty_body)
            )
        } else {
            events.forEach { ev ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = ev.cloneName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = ev.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatRelative(ev.timestampMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
