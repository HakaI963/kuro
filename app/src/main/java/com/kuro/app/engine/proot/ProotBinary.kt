package com.kuro.app.engine.proot

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Step 5e: the proot binary is SHARED by all containers — one validated copy
 * at an EXECUTABLE location. Order of preference:
 *   1) nativeLibraryDir/libkuro-proot.so (APK-bundled .so validates in
 *      place; downloaded winners are copied there, chmod 700, re-validated).
 *   2) /data/local/tmp/kuro-proot (su copy + chmod via KernelSU, validated
 *      from there; user-initiated flows only, never background).
 * filesDir/bin/proot is LEGACY (Step 5c/5d): app-private storage cannot exec
 * on targetSdk 29+ (error=13), so it is never returned — the gate rejects it
 * and acquisition reinstalls to a real location.
 *
 * Flow:
 * - Session start (ensureInstalled, user-initiated): first location passing
 *   the full gate (ELF + `proot -V` + smoke, tightened: PT_INTERP absent) is
 *   returned. Else run ProotAcquisition (apk-lib first, then downloads).
 *   All fail → throw with every logged reason (honest error card).
 * - Cold start (revalidateBackground): same, but NEVER su (allowSuCopy=false).
 *   Fresh installs (nothing anywhere) are left for first use.
 */
object ProotBinary {
    const val ASSET_PATH = "proot/proot-aarch64"
    const val LIB_NAME = ProotAcquisition.APK_LIB_NAME

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Preferred executable: APK native library dir. */
    fun libFile(app: Context): File =
        File(app.applicationInfo.nativeLibraryDir, LIB_NAME)

    /** su-only fallback executable (user-initiated flows only). */
    fun tmpFile(): File = ProotAcquisition.tmpFallbackFile()

    /** Legacy Step 5c/5d location: never executable, never returned. */
    fun legacyFile(filesDir: File): File = File(File(filesDir, "bin"), "proot")

    suspend fun ensureInstalled(
        context: Context,
        allowSuFallback: Boolean = true
    ): Result<File> {
        return runCatching {
            withContext(Dispatchers.IO) { ensureOnIo(context.applicationContext, allowSuFallback) }
        }
    }

    /** Fire-and-forget cold-start revalidation (never blocks start, never su). */
    fun revalidateBackground(context: Context) {
        val app = context.applicationContext
        bgScope.launch {
            runCatching {
                val current = resolveValid(app)
                if (current != null) {
                    ContainerEngineLog.append(
                        app.filesDir, "proot",
                        "cold-start revalidation OK (${current.absolutePath})"
                    )
                    return@launch
                }
                val hadSomething = libFile(app).exists() || tmpFile().exists() ||
                    legacyFile(app.filesDir).exists()
                // Fresh installs (no copy anywhere): leave for first use —
                // no surprise downloads, no su prompts from background.
                if (!hadSomething) return@launch
                ContainerEngineLog.append(
                    app.filesDir, "proot",
                    "cold-start copy invalid, re-acquiring (no su)"
                )
                when (val r = ProotAcquisition.acquire(app, allowSuCopy = false)) {
                    is AcquireResult.Success -> Unit
                    is AcquireResult.Failure -> ContainerEngineLog.append(
                        app.filesDir, "proot",
                        "re-acquire failed: ${r.reasons.joinToString(" | ")}"
                    )
                }
            }
        }
    }

    private suspend fun ensureOnIo(app: Context, allowSuFallback: Boolean): File {
        resolveValid(app)?.let { return it }
        // Installed copy missing or invalid: run the acquisition pipeline.
        return when (val r = ProotAcquisition.acquire(app, allowSuCopy = allowSuFallback)) {
            is AcquireResult.Success -> resolveValid(app)
                ?: throw ProotUnavailableException(
                    listOf(
                        "acquire reported success but no executable validates at " +
                            "${libFile(app).absolutePath} or ${tmpFile().absolutePath}"
                    )
                )
            is AcquireResult.Failure -> throw ProotUnavailableException(r.reasons)
        }
    }

    /** First executable location passing the full gate, else null. */
    fun resolveValid(app: Context): File? {
        val lib = libFile(app)
        if (lib.isFile && fullGate(app, lib, "apk-lib")) return lib
        val tmp = tmpFile()
        if (tmp.isFile && fullGate(app, tmp, "tmp-su-fallback")) return tmp
        return null
    }

    /** Full gate on one executable path (ELF + version + smoke). */
    private fun fullGate(app: Context, dest: File, sourceHint: String): Boolean {
        val elf = ElfProbe.inspect(dest) ?: return false.also {
            ContainerEngineLog.append(
                app.filesDir, "proot", "installed copy (${dest.absolutePath}): not ELF"
            )
        }
        val verdict = ElfProbe.verdict(elf)
        if (verdict != null) {
            ContainerEngineLog.append(
                app.filesDir, "proot",
                "installed copy (${dest.absolutePath}) rejected: $verdict"
            )
            return false
        }
        val version = runCatching { queryVersion(dest) }.getOrNull()
        if (version == null || !version.contains("proot", ignoreCase = true)) {
            ContainerEngineLog.append(
                app.filesDir, "proot", "installed copy (${dest.absolutePath}): -V failed"
            )
            return false
        }
        val smoke = runCatching { querySmoke(dest) }.getOrNull() ?: false
        if (!smoke) {
            ContainerEngineLog.append(
                app.filesDir, "proot", "installed copy (${dest.absolutePath}): smoke failed"
            )
            return false
        }
        // Backfill provenance for in-place valid copies (e.g. fresh APK
        // install where acquisition never ran): Diagnostics must show the
        // validated source + version + final path.
        backfillProvenance(app, dest, sourceHint, elf, version)
        ProotAcquisition.writeValidation(
            app.filesDir,
            ProotAcquisition.readProvenance(app.filesDir)?.source ?: sourceHint,
            elf.proofLines(),
            version.lineSequence().firstOrNull() ?: "",
            "smoke=proot-ok exit=0",
            ok = true
        )
        return true
    }

    private fun backfillProvenance(
        app: Context,
        dest: File,
        sourceHint: String,
        elf: ElfReport,
        version: String
    ) {
        val existing = ProotAcquisition.readProvenance(app.filesDir)
        if (existing?.path == dest.absolutePath) return
        val sha = runCatching { Sha256.ofBytes(dest.readBytes()) }.getOrDefault("?")
        val provenance = ProotProvenance(
            source = sourceHint,
            sha256 = sha,
            versionLine = version.lineSequence().firstOrNull()?.take(120) ?: version.take(120),
            installedAt = System.currentTimeMillis(),
            elfProof = elf.proofLines(),
            path = dest.absolutePath
        )
        runCatching {
            File(app.filesDir, ProotAcquisition.PROVENANCE_FILE).apply {
                parentFile?.mkdirs()
                writeText(provenance.toLines().joinToString("\n", postfix = "\n"))
            }
        }
        ContainerEngineLog.append(
            app.filesDir, "proot",
            "provenance recorded ($sourceHint): ${dest.absolutePath}"
        )
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
