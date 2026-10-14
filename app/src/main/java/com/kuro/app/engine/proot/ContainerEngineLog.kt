package com.kuro.app.engine.proot

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Step 5: engine event log at filesDir/kuro-engine.log, rotated at 1 MB
 * (overflow truncates to the newest ~512 KB). Informational only — the UI
 * always shows its own typed state alongside any log line.
 */
object ContainerEngineLog {
    const val FILE_NAME = "kuro-engine.log"
    private const val MAX_BYTES = 1024L * 1024L
    private const val KEEP_BYTES = 512L * 1024L

    @Synchronized
    fun append(filesDir: File, tag: String, message: String) {
        runCatching {
            val file = File(filesDir, FILE_NAME)
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > MAX_BYTES) {
                val all = file.readBytes()
                file.writeBytes(all.takeLast(KEEP_BYTES.toInt()).toByteArray())
            }
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file.appendText("[$ts] $tag: $message\n")
        }
    }
}
