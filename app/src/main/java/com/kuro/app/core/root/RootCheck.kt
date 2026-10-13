package com.kuro.app.core.root

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Step 4 root rules:
 * - On every cold start: SILENT background availability check with a short
 *   timeout. Result is cached in memory and shown in the Settings card.
 *   No UI, no dialogs, no crash when root is absent.
 * - `su` is invoked ONLY from [requestRoot] (user pressed "Request root
 *   access") or another user-initiated root-requiring action, so the
 *   KernelSU grant prompt appears only when the user asks for it.
 */
sealed interface RootStatus {
    data object Checking : RootStatus
    data object Granted : RootStatus
    data object NotGranted : RootStatus
    data object NotDetected : RootStatus
}

object RootChecker {
    private const val TIMEOUT_MS = 2500L

    private val _status = MutableStateFlow<RootStatus>(RootStatus.Checking)
    val status: StateFlow<RootStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Silent check. Safe to call on every app open; never touches the UI. */
    fun refreshSilent() {
        scope.launch {
            _status.value = probe()
        }
    }

    /**
     * User-initiated: runs `su` so KernelSU shows its grant prompt.
     * Call only from an explicit user action (e.g. Settings button).
     */
    fun requestRoot(onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            val granted = runSuId()
            _status.value = if (granted) RootStatus.Granted else {
                // su exists but denied → not granted; no su at all → not detected.
                if (suBinaryPresent()) RootStatus.NotGranted else RootStatus.NotDetected
            }
            onResult(granted)
        }
    }

    private suspend fun probe(): RootStatus {
        if (!suBinaryPresent()) return RootStatus.NotDetected
        val granted = withTimeoutOrNull(TIMEOUT_MS) { runSuId() } ?: false
        return if (granted) RootStatus.Granted else RootStatus.NotGranted
    }

    private fun suBinaryPresent(): Boolean {
        return runCatching {
            val p = ProcessBuilder("which", "su")
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.contains("su")
        }.getOrDefault(false)
    }

    private fun runSuId(): Boolean {
        return runCatching {
            val p = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.contains("uid=0")
        }.getOrDefault(false)
    }
}
