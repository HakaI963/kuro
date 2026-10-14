package com.kuro.app.engine.proot

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Step 5c: the proot binary is SHARED by all containers — one validated copy
 * at filesDir/bin/proot. Flow:
 * - Session start (ensureInstalled): installed copy passes the full gate
 *   (ELF + `proot -V` + smoke) → return it. Else run ProotAcquisition
 *   (reliability-ordered candidates, first pass wins). All fail → throw with
 *   every logged reason (honest error card).
 * - Cold start (revalidateBackground): if a copy is installed but no longer
 *   passes ELF + `-V`, auto re-acquire in background. Fresh installs (no copy)
 *   are left for first use — no surprise downloads.
 */
object ProotBinary {
    const val ASSET_PATH = "proot/proot-aarch64"

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun binaryFile(filesDir: File): File = File(File(filesDir, "bin"), "proot")

    suspend fun ensureInstalled(context: Context): Result<File> {
        return runCatching {
            withContext(Dispatchers.IO) { ensureOnIo(context.applicationContext) }
        }
    }

    /** Fire-and-forget cold-start revalidation (never blocks start). */
    fun revalidateBackground(context: Context) {
        val app = context.applicationContext
        bgScope.launch {
            runCatching {
                val dest = binaryFile(app.filesDir)
                // Only re-acquire something that was previously installed.
                if (!dest.exists()) return@launch
                if (quickGate(dest)) {
                    ContainerEngineLog.append(app.filesDir, "proot", "cold-start revalidation OK")
                    return@launch
                }
                ContainerEngineLog.append(
                    app.filesDir, "proot",
                    "cold-start copy invalid, re-acquiring"
                )
                when (val r = ProotAcquisition.acquire(app)) {
                    is AcquireResult.Success -> Unit
                    is AcquireResult.Failure -> ContainerEngineLog.append(
                        app.filesDir, "proot",
                        "re-acquire failed: ${r.reasons.joinToString(" | ")}"
                    )
                }
            }
        }
    }

    private suspend fun ensureOnIo(app: Context): File {
        val dest = binaryFile(app.filesDir)
        if (dest.exists() && fullGate(app, dest)) return dest
        // Installed copy missing or invalid: run the acquisition pipeline.
        return when (val r = ProotAcquisition.acquire(app)) {
            is AcquireResult.Success -> binaryFile(app.filesDir)
            is AcquireResult.Failure -> throw ProotUnavailableException(r.reasons)
        }
    }

    /** Full gate on the installed copy (ELF + version + smoke). */
    private fun fullGate(app: Context, dest: File): Boolean {
        val elf = ElfProbe.inspect(dest) ?: return false.also {
            ContainerEngineLog.append(app.filesDir, "proot", "installed copy: not ELF")
        }
        val verdict = ElfProbe.verdict(elf)
        if (verdict != null) {
            ContainerEngineLog.append(app.filesDir, "proot", "installed copy rejected: $verdict")
            return false
        }
        val version = runCatching { queryVersion(dest) }.getOrNull()
        if (version == null || !version.contains("proot", ignoreCase = true)) {
            ContainerEngineLog.append(app.filesDir, "proot", "installed copy: -V failed")
            return false
        }
        val smoke = runCatching { querySmoke(dest) }.getOrNull() ?: false
        if (!smoke) {
            ContainerEngineLog.append(app.filesDir, "proot", "installed copy: smoke failed")
            return false
        }
        ProotAcquisition.writeValidation(
            app.filesDir,
            ProotAcquisition.readProvenance(app.filesDir)?.source ?: "?",
            elf.proofLines(),
            version.lineSequence().firstOrNull() ?: "",
            "smoke=proot-ok exit=0",
            ok = true
        )
        return true
    }

    /** Cheap cold-start gate (ELF + version only). */
    private fun quickGate(dest: File): Boolean {
        val elf = ElfProbe.inspect(dest) ?: return false
        if (ElfProbe.verdict(elf) != null) return false
        val version = runCatching { queryVersion(dest) }.getOrNull() ?: return false
        return version.contains("proot", ignoreCase = true)
    }

    private fun queryVersion(binary: File): String? {
        val (out, _, code) = ExecCapture.run(listOf(binary.absolutePath, "-V"), 10_000L)
        if (code != 0) return null
        return out.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
    }

    private fun querySmoke(binary: File): Boolean {
        val (out, _, code) = ExecCapture.run(
            listOf(binary.absolutePath, "-w", "/", "/system/bin/sh", "-c", "echo proot-ok"),
            15_000L
        )
        return code == 0 && "proot-ok" in out
    }

    /** Thrown when every acquisition candidate fails; message lists reasons. */
    class ProotUnavailableException(val reasons: List<String>) :
        IllegalStateException(reasons.joinToString("\n"))
}
