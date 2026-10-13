package com.kuro.app.core.system

import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.kuro.app.core.utils.formatBytes
import java.io.File

/**
 * Step 4 (Stage 2 shell): REAL system metric readers for the System tab.
 * No mocks, no fake values. Anything unavailable renders as "−".
 */
data class SystemMetrics(
    val ramUsedBytes: Long,
    val ramTotalBytes: Long,
    val cpuLoad01: Float?,
    val cpuTempC: Float?,
    val gpuRenderer: String?,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val kuroDataBytes: Long,
    val kernelVersion: String,
    val arch: String,
    val uptimeMillis: Long
) {
    val ramUsedLabel: String get() = formatBytes(ramUsedBytes)
    val ramTotalLabel: String get() = formatBytes(ramTotalBytes)
}

object SystemMetricsReader {
    fun read(appFilesDir: File): SystemMetrics {
        val (used, total) = readMemInfo()
        return SystemMetrics(
            ramUsedBytes = used,
            ramTotalBytes = total,
            cpuLoad01 = readCpuLoad(),
            cpuTempC = readCpuTemp(),
            gpuRenderer = readGpuRenderer(),
            storageFreeBytes = storageFreeBytes(),
            storageTotalBytes = storageTotalBytes(),
            kuroDataBytes = dirSize(appFilesDir),
            kernelVersion = System.getProperty("os.version") ?: "−",
            arch = Build.SUPPORTED_ABIS.firstOrNull() ?: (System.getProperty("os.arch") ?: "−"),
            uptimeMillis = SystemClock.elapsedRealtime()
        )
    }

    private fun readMemInfo(): Pair<Long, Long> {
        var totalKb = 0L
        var availKb = 0L
        runCatching {
            File("/proc/meminfo").forEachLine { line ->
                when {
                    line.startsWith("MemTotal:") -> totalKb = line.filter { it.isDigit() }.toLong()
                    line.startsWith("MemAvailable:") -> availKb = line.filter { it.isDigit() }.toLong()
                }
            }
        }
        val total = totalKb * 1024L
        val used = (totalKb - availKb).coerceAtLeast(0L) * 1024L
        return used to total
    }

    private fun readCpuStat(): Pair<Long, Long>? {
        return runCatching {
            val parts = File("/proc/stat").readLines().firstOrNull()
                ?.split("\\s+".toRegex())?.drop(1)?.mapNotNull { it.toLongOrNull() }
                ?: return null
            if (parts.size < 4) return null
            val idle = parts[3] + parts.getOrNull(4).orZero()
            val total = parts.sum()
            total to idle
        }.getOrNull()
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private fun readCpuLoad(): Float? {
        // Sample /proc/stat twice ~400ms apart; load = 1 - idleDelta/totalDelta.
        val first = readCpuStat() ?: return null
        runCatching { Thread.sleep(400) }
        val second = readCpuStat() ?: return null
        val totalDelta = (second.first - first.first).toFloat()
        val idleDelta = (second.second - first.second).toFloat()
        if (totalDelta <= 0f) return null
        return (1f - idleDelta / totalDelta).coerceIn(0f, 1f)
    }

    private fun readCpuTemp(): Float? {
        // Highest thermal-zone temperature that looks like a CPU sensor.
        var best: Float? = null
        runCatching {
            File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
                ?.forEach { zone ->
                    val type = runCatching { File(zone, "type").readText().trim() }.getOrNull() ?: ""
                    val raw = runCatching { File(zone, "temp").readText().trim().toLongOrNull() }.getOrNull()
                        ?: return@forEach
                    if (raw <= 0L) return@forEach
                    val celsius = raw / 1000f
                    if (celsius !in 0f..150f) return@forEach
                    val looksCpu = type.contains("cpu", ignoreCase = true) ||
                        type.contains("soc", ignoreCase = true) ||
                        type.contains("thermal", ignoreCase = true)
                    if (looksCpu && (best == null || celsius > best!!)) best = celsius
                }
        }
        return best
    }

    private fun readGpuRenderer(): String? {
        // Honest GLES query: returns null without a GL context → caller shows "−".
        return runCatching {
            val name = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER)
            val err = android.opengl.GLES20.glGetError()
            if (name.isNullOrBlank() || err != android.opengl.GLES20.GL_NO_ERROR) null else name
        }.getOrNull()
    }

    private fun storageFreeBytes(): Long {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        }.getOrDefault(0L)
    }

    private fun storageTotalBytes(): Long {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.blockCountLong * stat.blockSizeLong
        }.getOrDefault(0L)
    }

    fun dirSize(dir: File): Long {
        var size = 0L
        runCatching {
            dir.walkTopDown().forEach { f ->
                if (f.isFile) size += f.length()
            }
        }
        return size
    }
}
