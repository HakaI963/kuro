package com.ivarna.fluxlinux.core.system

import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.delay
import java.io.File

/**
 * Step 4b: REAL system metric readers, hardened.
 * - Every reader is wrapped in runCatching and runs on Dispatchers.IO
 *   (see SystemViewModel.refreshCycle). Per-tile isolation: one failing
 *   source renders "-" for its tile and never throws.
 * - No Thread.sleep anywhere: CPU sampling uses cancellable delay() so the
 *   sampling coroutine dies with the System tab.
 * - GLES is never queried without a context: kgsl sysfs first, else a fully
 *   guarded offscreen EGL14 pbuffer (everything destroyed), else "-".
 */
data class SystemMetrics(
    val ramUsedBytes: Long?,
    val ramTotalBytes: Long?,
    val cpuLoad01: Float?,
    val cpuTempC: Float?,
    val gpuRenderer: String?,
    val storageFreeBytes: Long?,
    val storageTotalBytes: Long?,
    val appDataBytes: Long?,
    val kernelLabel: String?,
    val arch: String?,
    val uptimeMillis: Long?
)

object SystemMetricsReader {
    private const val APP_CACHE_MS = 60_000L

    @Volatile
    private var cachedAppBytes: Long? = null

    @Volatile
    private var cachedKuroAt: Long = 0L

    /**
     * One full refresh. Suspends ~1s for CPU sampling; cancellable.
     * Never throws — every tile degrades to null ("-") independently.
     */
    suspend fun readCycle(appFilesDir: File): SystemMetrics {
        val statFirst = readCpuStat()
        delay(1000L)
        val statSecond = readCpuStat()
        return SystemMetrics(
            ramUsedBytes = readRam().first,
            ramTotalBytes = readRam().second,
            cpuLoad01 = cpuLoad(statFirst, statSecond),
            cpuTempC = readCpuTemp(),
            gpuRenderer = readGpuRenderer(),
            storageFreeBytes = storageFreeBytes(),
            storageTotalBytes = storageTotalBytes(),
            appDataBytes = cachedAppBytes(appFilesDir),
            kernelLabel = readKernelLabel(),
            arch = readArch(),
            uptimeMillis = readUptime()
        )
    }

    private fun readRam(): Pair<Long?, Long?> {
        var totalKb: Long? = null
        var availKb: Long? = null
        runCatching {
            File("/proc/meminfo").forEachLine { line ->
                when {
                    line.startsWith("MemTotal:") ->
                        totalKb = line.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toLong()
                    line.startsWith("MemAvailable:") ->
                        availKb = line.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toLong()
                }
            }
        }
        val total = totalKb?.times(1024L)
        val used = if (totalKb != null && availKb != null) {
            (totalKb!! - availKb!!).coerceAtLeast(0L) * 1024L
        } else null
        return used to total
    }

    private fun readCpuStat(): Pair<Long, Long>? {
        return runCatching {
            val parts = File("/proc/stat").bufferedReader().use { it.readLine() }
                ?.split("\\s+".toRegex())?.drop(1)?.mapNotNull { it.toLongOrNull() }
                ?: return null
            if (parts.size < 4) return null
            val idle = parts[3] + parts.getOrElse(4) { 0L }
            val total = parts.sum()
            total to idle
        }.getOrNull()
    }

    private fun cpuLoad(first: Pair<Long, Long>?, second: Pair<Long, Long>?): Float? {
        if (first == null || second == null) return null
        val totalDelta = (second.first - first.first).toFloat()
        // Guard: zero/negative delta (counter reset, single-core stall) = no data.
        if (totalDelta <= 0f) return null
        val idleDelta = (second.second - first.second).toFloat()
        return (1f - idleDelta / totalDelta).coerceIn(0f, 1f)
    }

    private fun readCpuTemp(): Float? {
        data class Hit(val priority: Int, val temp: Float)
        var best: Hit? = null
        runCatching {
            val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
                ?: return null
            zones.forEach { zone ->
                val type = runCatching { File(zone, "type").readText().trim() }.getOrNull() ?: ""
                val raw = runCatching { File(zone, "temp").readText().trim().toLongOrNull() }.getOrNull()
                    ?: return@forEach
                if (raw <= 0L) return@forEach
                val celsius = raw / 1000f
                if (celsius !in 0f..150f) return@forEach
                val lower = type.lowercase()
                val priority = when {
                    "cpu" in lower -> 0
                    "x0" in lower -> 1
                    "monitor" in lower -> 2
                    else -> 3
                }
                val cur = best
                if (cur == null || priority < cur.priority ||
                    (priority == cur.priority && celsius > cur.temp)
                ) {
                    best = Hit(priority, celsius)
                }
            }
        }
        return best?.temp
    }

    private fun readGpuRenderer(): String? {
        // 1) kgsl sysfs — no GL context needed.
        runCatching {
            File("/sys/class/kgsl/kgsl-3d0/gpu_model").readText().trim()
                .takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        // 2) Guarded offscreen EGL pbuffer on this (IO) thread, else "-".
        return eglPbufferRenderer()
    }

    private fun eglPbufferRenderer(): String? {
        return runCatching {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == null || display == EGL14.EGL_NO_DISPLAY) return null
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
            try {
                val configAttribs = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
                val numConfigs = IntArray(1)
                if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) ||
                    numConfigs[0] == 0 || configs[0] == null
                ) return null
                val surfaceAttribs = intArrayOf(
                    EGL14.EGL_WIDTH, 64, EGL14.EGL_HEIGHT, 64, EGL14.EGL_NONE
                )
                val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)
                if (surface == null || surface == EGL14.EGL_NO_SURFACE) return null
                val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                val context = EGL14.eglCreateContext(
                    display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0
                )
                if (context == null || context == EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroySurface(display, surface)
                    return null
                }
                try {
                    if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null
                    val name = GLES20.glGetString(GLES20.GL_RENDERER)
                    if (name.isNullOrBlank()) null else name
                } finally {
                    EGL14.eglMakeCurrent(
                        display,
                        EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                    EGL14.eglDestroyContext(display, context)
                    EGL14.eglDestroySurface(display, surface)
                }
            } finally {
                EGL14.eglTerminate(display)
            }
        }.getOrNull()
    }

    private fun storageFreeBytes(): Long? {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        }.getOrNull()
    }

    private fun storageTotalBytes(): Long? {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.blockCountLong * stat.blockSizeLong
        }.getOrNull()
    }

    /** Recursive filesDir size, computed on IO, cached at most every 60s. */
    fun cachedAppBytes(appFilesDir: File): Long? {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedAppBytes
        if (cached != null && now - cachedKuroAt < APP_CACHE_MS) return cached
        val size = runCatching {
            var total = 0L
            appFilesDir.walkTopDown().forEach { f ->
                if (f.isFile) total += f.length()
            }
            total
        }.getOrNull() ?: return cached
        cachedAppBytes = size
        cachedKuroAt = now
        return size
    }

    /** Uncached one-off dir size for Settings rows. Call on IO. */
    fun dirSizeNow(dir: File): Long? {
        return runCatching {
            var total = 0L
            dir.walkTopDown().forEach { f ->
                if (f.isFile) total += f.length()
            }
            total
        }.getOrNull()
    }

    private fun readKernelLabel(): String? {
        val release = runCatching {
            File("/proc/version").bufferedReader().use { it.readLine() }
        }.getOrNull()?.trim().takeIf { !it.isNullOrBlank() }
        val android = Build.VERSION.RELEASE ?: ""
        return when {
            release == null -> null
            else -> {
                // First token of /proc/version is "Linux"; the release token
                // (3rd) is the human-meaningful one — fall back to 1st.
                val tokens = release.split("\\s+".toRegex())
                val kernel = tokens.getOrElse(2) { tokens.first() }
                if (android.isNotBlank()) "$kernel • Android $android" else kernel
            }
        }
    }

    private fun readArch(): String? {
        return runCatching {
            Build.SUPPORTED_ABIS.firstOrNull()
                ?: System.getProperty("os.arch")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun readUptime(): Long? {
        runCatching {
            File("/proc/uptime").bufferedReader().use { it.readLine() }
                ?.split("\\s+".toRegex())?.firstOrNull()?.toFloatOrNull()
                ?.let { return (it * 1000L).toLong() }
        }
        return runCatching { SystemClock.elapsedRealtime() }.getOrNull()
    }
}
