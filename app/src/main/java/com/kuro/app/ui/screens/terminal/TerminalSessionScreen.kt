package com.kuro.app.ui.screens.terminal

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kuro.app.R
import com.kuro.app.engine.proot.InstallFailure
import com.kuro.app.engine.proot.LiveSession
import com.kuro.app.ui.components.KuroCard
import com.kuro.app.ui.components.KuroPrimaryButton

/**
 * Step 5: live terminal screen. Opens (or attaches to) a real pty session:
 * - auto-starts one session when the container has none live;
 * - back navigation detaches the View without killing the session;
 * - reopening ATTACHES the same live fd;
 * - multiple sessions per container via selector chips + New session.
 */
@Composable
fun TerminalSessionScreen(
    containerId: String,
    modifier: Modifier = Modifier,
    vm: TerminalViewModel = viewModel()
) {
    val context = LocalContext.current
    val containers by vm.containers.collectAsState()
    val liveSessions by vm.liveSessions.collectAsState()
    val exits by vm.sessionExits.collectAsState()
    val starting by vm.starting.collectAsState()
    val startError by vm.startError.collectAsState()
    val container = containers.firstOrNull { it.id == containerId }

    val sessions = remember(liveSessions, containerId) {
        liveSessions.values.filter { it.containerId == containerId }
    }
    var selectedId by remember(containerId) { mutableStateOf<String?>(null) }
    var lastKnownId by remember(containerId) { mutableStateOf<String?>(null) }
    var binaryError by remember(containerId) { mutableStateOf<InstallFailure?>(null) }
    var binaryChecked by remember(containerId) { mutableStateOf(false) }
    var binaryAttempt by remember(containerId) { mutableStateOf(0) }

    val selected: LiveSession? = sessions.firstOrNull { it.sessionId == selectedId } ?: sessions.firstOrNull()
    LaunchedEffect(selected?.sessionId) {
        selected?.let {
            selectedId = it.sessionId
            lastKnownId = it.sessionId
        }
    }

    // Binary pre-check, then auto-start one session when none is live.
    LaunchedEffect(containerId, binaryAttempt) {
        binaryChecked = false
        binaryError = null
        val binary = vm.ensureProotBinary(context)
        binaryChecked = true
        if (binary.isFailure) {
            binaryError = InstallFailure.Exec(
                "proot binary unavailable: ${binary.exceptionOrNull()?.message}"
            )
        }
    }
    LaunchedEffect(binaryChecked, liveSessions, container) {
        if (binaryChecked && binaryError == null && container != null &&
            sessions.isEmpty() && containerId !in starting
        ) {
            vm.startSession(context, containerId)
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = container?.name ?: stringResource(R.string.session_title_fallback),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (container != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TypeBadge(type = container.type)
                }
            }
            IconButton(
                onClick = { selected?.let { vm.stop(containerId, context) } },
                enabled = selected != null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.session_stop)
                )
            }
            IconButton(onClick = { keyboard?.show() }) {
                Icon(
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = stringResource(R.string.session_keyboard)
                )
            }
        }
        if (sessions.size > 1 || (sessions.isNotEmpty())) {
            Spacer(modifier = Modifier.height(8.dp))
            SessionSelector(
                sessions = sessions,
                selectedId = selected?.sessionId,
                onSelect = { selectedId = it },
                onNew = { vm.startSession(context, containerId) }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        val startErr = startError?.takeIf { it.first == containerId }?.second
        when {
            binaryError != null -> {
                InstallFailureCard(failure = binaryError!!, onRetry = {
                    binaryAttempt++
                    vm.clearStartError()
                })
            }
            startErr != null -> {
                InstallFailureCard(failure = startErr, onRetry = {
                    vm.clearStartError()
                    vm.startSession(context, containerId)
                })
            }
            selected != null -> {
                KuroTerminalView(
                    session = selected.terminalSession,
                    filesDir = context.filesDir,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                )
            }
            containerId in starting -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.session_starting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                // Ended (or never started): honest note + restart.
                val exit = lastKnownId?.let { exits[it] }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (exit != null)
                                "${stringResource(R.string.session_ended_title)} ($exit)"
                            else
                                stringResource(R.string.session_no_session_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.session_no_session_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        KuroPrimaryButton(
                            label = stringResource(R.string.session_start_new),
                            onClick = { vm.startSession(context, containerId) }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (selected != null) stringResource(R.string.terminal_status_running)
                else stringResource(R.string.terminal_status_stopped),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (container != null) {
                Text(
                    text = container.distroName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(90.dp))
    }
}

@Composable
private fun SessionSelector(
    sessions: List<LiveSession>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sessions.forEachIndexed { i, s ->
            FilterChip(
                selected = s.sessionId == selectedId,
                onClick = { onSelect(s.sessionId) },
                label = { Text("#${i + 1}") }
            )
        }
        FilterChip(
            selected = false,
            onClick = onNew,
            label = { Text("+ ${stringResource(R.string.terminal_open_new)}") }
        )
    }
}

@Composable
private fun InstallFailureCard(failure: InstallFailure, onRetry: () -> Unit) {
    KuroCard(title = failure.title) {
        Text(
            text = failure.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        KuroPrimaryButton(
            label = stringResource(R.string.install_retry),
            onClick = onRetry
        )
    }
}
