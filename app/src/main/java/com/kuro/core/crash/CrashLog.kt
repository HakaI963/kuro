package com.kuro.app.core.crash

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Step 4b: global crash capture so every future device crash is user-reportable.
 * [install] hooks Thread.UncaughtExceptionHandler: it appends timestamp, thread
 * name and the full stack trace to filesDir/crash.log (last 5 crashes kept),
 * then rethrows to the previous default handler so the system still closes
 * the app normally. The handler itself never throws.
 */
object CrashLog {
    const val FILE_NAME = "crash.log"
    const val MAX_ENTRIES = 5
    private const val SEPARATOR = "===== CRASH "
    private const val MAX_FILE_BYTES = 256 * 1024L

    data class Entry(
        val timestampMillis: Long,
        val threadName: String,
        val trace: String
    ) {
        val timestampLabel: String
            get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMillis))
        /** First 3 lines of the trace for the About preview row. */
        val preview: String
            get() = trace.lines().take(3).joinToString("\n")
    }

    fun install(filesDir: File) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                append(filesDir, thread.name, throwable.stackTraceToString())
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readLast(filesDir: File): Entry? {
        return runCatching { parse(File(filesDir, FILE_NAME)).lastOrNull() }.getOrNull()
    }

    private fun append(filesDir: File, threadName: String, trace: String) {
        val file = File(filesDir, FILE_NAME)
        val entries = if (file.exists()) parse(file) else emptyList()
        val kept = (entries + Entry(System.currentTimeMillis(), threadName, trace))
            .takeLast(MAX_ENTRIES)
        val out = buildString {
            kept.forEach { e ->
                append(SEPARATOR).append(e.timestampMillis).append(" | ")
                    .append(e.threadName.replace('\n', ' ')).append('\n')
                append(e.trace.trimEnd()).append('\n')
            }
        }.take(MAX_FILE_BYTES.toInt())
        file.parentFile?.mkdirs()
        file.writeText(out)
    }

    private fun parse(file: File): List<Entry> {
        if (!file.exists()) return emptyList()
        val result = mutableListOf<Entry>()
        var ts = 0L
        var thread = ""
        val trace = StringBuilder()
        fun flush() {
            if (trace.isNotBlank()) result.add(Entry(ts, thread, trace.toString().trimEnd()))
        }
        file.forEachLine { line ->
            if (line.startsWith(SEPARATOR)) {
                flush()
                trace.clear()
                val head = line.removePrefix(SEPARATOR)
                ts = head.substringBefore(" | ").toLongOrNull() ?: 0L
                thread = head.substringAfter(" | ", "")
            } else {
                trace.appendLine(line)
            }
        }
        flush()
        return result
    }
}
