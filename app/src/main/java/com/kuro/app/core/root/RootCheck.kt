package com.kuro.app.core.root

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
/**
 * Step 4b root rules (hardened):
 * - On every cold start: SILENT background availability check with a 3s
 *   timeout. Result is cached in memory and shown in the Settings card.
 *   No UI, no dialogs, no crash when root is absent.
 * - `su` is invoked ONLY from [requestRoot] (user pressed "Request root
 *   access") or another user-initiated root-requiring action, so the
 *   KernelSU grant prompt appears only when the user asks for it.
 * - The exec block runs on Dispatchers.IO inside runCatching, consumes
 *   stdout/stderr fully, enforces the timeout, and destroys the process in
 *   finally. Failure always degrades to NotGranted/NotDetected — never throws.
 */
sealed interface RootStatus {
    data object Checking : RootStatus
    data object Granted : RootStatus
    data object NotGranted : RootStatus
    data object NotDetected : RootStatus
}

object RootChecker {
    private const val TIMEOUT_SEC = 3L

    private val _status = MutableStateFlow<RootStatus>(RootStatus.Checking)
    val status: StateFlow<RootStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Silent check. Safe to call on every app open; never touches the UI. */
    fun refreshSilent() {
        scope.launch {
            _status.value = runCatching { probe() }.getOrDefault(RootStatus.NotDetected)
        }
    }

    /**
     * User-initiated: runs `su` so KernelSU shows its grant prompt.
     * Call only from an explicit user action (e.g. Settings button).
     */
    fun requestRoot(onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            val status = runCatching { probe() }.getOrDefault(RootStatus.NotDetected)
            _status.value = status
            onResult(status == RootStatus.Granted)
        }
    }

    private suspend fun probe(): RootStatus {
        return withTimeoutOrNull(TIMEOUT_SEC * 1000L) {
            withContext(Dispatchers.IO) { execSu() }
        } ?: RootStatus.NotGranted
    }

    private fun execSu(): RootStatus {
        val proc = try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "true"))
        } catch (_: Exception) {
            return RootStatus.NotDetected
        }
        try {
            // Wait first with timeout so a grant-prompt block can't hang us;
            // then drain both streams (small output) and read the exit code.
            val finished = try {
                proc.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS)
            } catch (_: Exception) {
                false
            }
            if (!finished) return RootStatus.NotGranted
            runCatching { proc.inputStream.readBytes() }
            runCatching { proc.errorStream.readBytes() }
            val code = runCatching { proc.exitValue() }.getOrDefault(1)
            return if (code == 0) RootStatus.Granted else RootStatus.NotGranted
        } finally {
            runCatching { proc.destroy() }
        }
    }
}
