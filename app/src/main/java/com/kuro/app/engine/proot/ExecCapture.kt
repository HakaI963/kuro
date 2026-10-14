package com.kuro.app.engine.proot

import java.util.concurrent.TimeUnit

/**
 * Step 5c: guarded process execution shared by the binary gates.
 * Returns stdout, first 200 stderr bytes, and the exit code.
 * execve-level failures surface in the stderr slot (errno text included).
 */
object ExecCapture {
    fun run(cmd: List<String>, timeoutMs: Long): Triple<String, String, Int> {
        val proc = try {
            ProcessBuilder(cmd).redirectErrorStream(false).start()
        } catch (e: Exception) {
            return Triple("", "execve: ${e.message}".take(200), -1)
        }
        try {
            val finished = try {
                proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                false
            }
            if (!finished) return Triple("", "timeout after ${timeoutMs}ms", -2)
            val out = runCatching { proc.inputStream.readBytes() }.getOrDefault(ByteArray(0))
            val err = runCatching { proc.errorStream.readBytes() }.getOrDefault(ByteArray(0))
            val code = runCatching { proc.exitValue() }.getOrDefault(-3)
            return Triple(
                out.toString(Charsets.UTF_8),
                err.toString(Charsets.UTF_8).take(200),
                code
            )
        } finally {
            runCatching { proc.destroy() }
        }
    }
}
