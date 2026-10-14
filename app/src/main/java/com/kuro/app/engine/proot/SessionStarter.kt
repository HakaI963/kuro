package com.kuro.app.engine.proot

import android.content.Context
import android.os.Build
import com.kuro.app.data.local.KuroDatabase
import com.kuro.app.sessions.SessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Step 5: starts live pty sessions for installed proot containers.
 * Preconditions are checked honestly (arch, binary, rootfs) and every
 * failure returns a typed InstallFailure — the terminal screen renders it
 * instead of a terminal. Never invokes su (proot path is rootless).
 */
object SessionStarter {

    suspend fun start(
        appContext: Context,
        db: KuroDatabase,
        containerId: String
    ): StartOutcome<LiveSession, InstallFailure> {
        val record = withContext(Dispatchers.IO) {
            runCatching { db.containerDao().get(containerId) }.getOrNull()
        } ?: return StartOutcome.failure(
            InstallFailure.Exec("container $containerId is not installed")
        )
        if (!isArm64()) {
            return StartOutcome.failure(
                InstallFailure.Exec(
                    "proot runs on arm64 only (this device: " +
                        (Build.SUPPORTED_ABIS.firstOrNull() ?: "?") + ")"
                )
            )
        }
        val binary = ProotBinary.ensureInstalled(appContext).getOrElse { e ->
            return StartOutcome.failure(
                InstallFailure.Exec("proot binary unavailable: ${e.message}")
            )
        }
        ContainerEngineLog.append(
            appContext.filesDir, "session",
            "using proot executable ${binary.absolutePath}"
        )
        val rootfs = com.kuro.app.core.system.StoragePaths
            .prootRootfsDir(appContext.filesDir, containerId)
        if (!rootfs.isDirectory || File(rootfs, "bin").exists().not()) {
            return StartOutcome.failure(
                InstallFailure.Exec("rootfs missing for $containerId — recreate the container")
            )
        }
        val plan = ProotLauncher.plan(
            prootBin = binary,
            rootfs = rootfs,
            containerId = containerId,
            shellPref = record.shell,
            bridgeEnabled = record.bridgeEnabled
        )
        // TerminalSession owns an android Handler: construct on main.
        val live = withContext(Dispatchers.Main) {
            ProotSessions.create(appContext, containerId, plan, db = db)
        }
        SessionService.ensureSessions(appContext)
        ContainerEngineLog.append(
            appContext.filesDir, "session",
            "started $containerId via ${plan.shellLabel}"
        )
        return StartOutcome.success(live)
    }

    private fun isArm64(): Boolean =
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    /** Typed outcome (avoids throwing across layers). */
    sealed interface StartOutcome<out T, out F> {
        data class Success<T>(val value: T) : StartOutcome<T, Nothing>
        data class Failure<F>(val failure: F) : StartOutcome<Nothing, F>

        fun getOrElse(fallback: (F) -> Nothing): T = when (this) {
            is Success -> value
            is Failure -> fallback(failure)
        }

        companion object {
            fun <T> success(value: T): StartOutcome<T, Nothing> = Success(value)
            fun <F> failure(failure: F): StartOutcome<Nothing, F> = Failure(failure)
        }
    }
}
