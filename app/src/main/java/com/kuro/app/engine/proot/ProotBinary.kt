package com.kuro.app.engine.proot

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Step 5: ships the static aarch64 proot binary (see
 * third_party/proot/PROVENANCE.md). At first use it is extracted to
 * filesDir/bin/proot, chmod 700, and verified by running `proot -V`
 * through a guarded process call. Any failure is an honest error state.
 */
object ProotBinary {
    const val ASSET_PATH = "proot/proot-aarch64"

    fun binaryFile(filesDir: File): File = File(File(filesDir, "bin"), "proot")

    suspend fun ensureInstalled(context: Context): Result<File> {
        return runCatching {
            withContext(Dispatchers.IO) { ensureOnIo(context) }
        }
    }

    private fun ensureOnIo(context: Context): File {
        val dest = binaryFile(context.filesDir)
        val filesDir = context.filesDir
        if (!dest.exists()) {
            dest.parentFile?.mkdirs()
            context.assets.open(ASSET_PATH).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            ContainerEngineLog.append(filesDir, "proot", "extracted asset to ${dest.absolutePath}")
        }
        // chmod 700: precise Os.chmod first, shell fallback second.
        val chmodOk = runCatching {
            Os.chmod(dest.absolutePath, 448) // 0700
            true
        }.getOrDefault(false) || runCatching {
            val p = ProcessBuilder("chmod", "700", dest.absolutePath).start()
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)
        if (!chmodOk) {
            throw IllegalStateException("chmod 700 failed for ${dest.absolutePath}")
        }
        val version = runCatching { queryVersion(dest) }.getOrNull()
            ?: throw IllegalStateException("proot -V produced no version output")
        if (!version.contains("proot", ignoreCase = true)) {
            throw IllegalStateException("unexpected proot -V output: ${version.take(120)}")
        }
        ContainerEngineLog.append(filesDir, "proot", "verified: ${version.lineSequence().firstOrNull()}")
        return dest
    }

    private fun queryVersion(binary: File): String? {
        val proc = try {
            ProcessBuilder(binary.absolutePath, "-V")
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            return null
        }
        try {
            val finished = try {
                proc.waitFor(10, TimeUnit.SECONDS)
            } catch (_: Exception) {
                false
            }
            if (!finished) return null
            // Process already exited with tiny output: drain is immediate.
            val out = runCatching { proc.inputStream.readBytes() }.getOrDefault(ByteArray(0))
            if (proc.exitValue() != 0) return null
            return out.toString(Charsets.UTF_8).trim().takeIf { it.isNotBlank() }
        } finally {
            runCatching { proc.destroy() }
        }
    }
}
