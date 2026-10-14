package com.kuro.app.engine.proot

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Step 5c: kuro-chroot helper, shipped now (assets/kuro-chroot, static musl).
 * Sessions wire in Step 6; here we build + validate with device gates run
 * through su (user-initiated ONLY — see Settings "Validate chroot helper").
 *
 *   g1) su -c "kuro-chroot version" exits 0 with version output.
 *   g2) mount + umount cycle on an empty test dir under /data/local/kuro.
 *
 * Gate results persist to filesDir/bin/chroot-gates and to kuro-engine.log.
 * Failures are honest error states in the Settings root card, never a crash.
 */
object ChrootGates {
    const val ASSET_PATH = "kuro-chroot"
    const val GATES_FILE = "bin/chroot-gates"
    const val TEST_DIR = "/data/local/kuro/.gate-test"

    data class Results(
        val timestamp: Long,
        val helperVersion: String?,
        val g1ok: Boolean,
        val g1detail: String,
        val g2ok: Boolean,
        val g2detail: String
    ) {
        fun toLines(): List<String> = listOf(
            "ts=$timestamp",
            "version=${helperVersion ?: "-"}",
            "g1=$g1ok",
            "g1detail=$g1detail",
            "g2=$g2ok",
            "g2detail=$g2detail"
        )

        companion object {
            fun parse(lines: List<String>): Results? {
                return runCatching {
                    val map = lines.mapNotNull {
                        val i = it.indexOf('=')
                        if (i < 0) null else it.take(i) to it.drop(i + 1)
                    }.toMap()
                    Results(
                        timestamp = map["ts"]?.toLongOrNull() ?: return null,
                        helperVersion = map["version"]?.takeIf { it != "-" },
                        g1ok = map["g1"] == "true",
                        g1detail = map["g1detail"] ?: "",
                        g2ok = map["g2"] == "true",
                        g2detail = map["g2detail"] ?: ""
                    )
                }.getOrNull()
            }
        }
    }

    fun helperFile(filesDir: File): File = File(File(filesDir, "bin"), "kuro-chroot")

    /** Extract asset + chmod (no su). Returns version line or null. */
    suspend fun ensureHelper(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dest = helperFile(context.filesDir)
            if (!dest.exists()) {
                dest.parentFile?.mkdirs()
                context.assets.open(ASSET_PATH).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            chmod700(dest)
            val (out, err, code) = ExecCapture.run(listOf(dest.absolutePath, "version"), 10_000L)
            val line = out.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            if (code == 0 && line?.contains("kuro-chroot") == true) line else null
        }.getOrNull()
    }

    /**
     * Run g1+g2 through su. Call ONLY from explicit user action while root is
     * granted — this pops the KernelSU prompt on first use.
     */
    suspend fun runGates(context: Context): Results = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val filesDir = app.filesDir
        val dest = helperFile(filesDir)
        val version = ensureHelper(app)
        var g1ok = false
        var g1detail = "helper missing"
        var g2ok = false
        var g2detail = "skipped"
        if (version != null && dest.exists()) {
            val (out1, err1, code1) = suExec(dest.absolutePath, listOf("version"))
            g1ok = code1 == 0 && "kuro-chroot" in out1
            g1detail = if (g1ok) {
                out1.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "ok"
            } else {
                "exit=$code1 stderr=${err1.take(200)}"
            }
            if (g1ok) {
                val dns = androidDns(app)
                val mount = suExec(
                    dest.absolutePath,
                    listOf("mount", TEST_DIR),
                    env = dnsEnv(dns)
                )
                val unmount = suExec(dest.absolutePath, listOf("umount", TEST_DIR))
                // Always attempt cleanup, even if mount failed halfway.
                suExec(dest.absolutePath, listOf("umount", TEST_DIR))
                g2ok = mount.third == 0 && unmount.third == 0
                g2detail = if (g2ok) {
                    "mount+umount $TEST_DIR ok"
                } else {
                    "mount exit=${mount.third} umount exit=${unmount.third} " +
                        "stderr=${(mount.second + " " + unmount.second).trim().take(200)}"
                }
            } else {
                g2detail = "skipped (g1 failed)"
            }
        }
        val results = Results(
            timestamp = System.currentTimeMillis(),
            helperVersion = version?.take(120),
            g1ok = g1ok,
            g1detail = g1detail.take(200),
            g2ok = g2ok,
            g2detail = g2detail.take(200)
        )
        persist(filesDir, results)
        ContainerEngineLog.append(
            filesDir, "chroot-gates",
            "g1=$g1ok ($g1detail) g2=$g2ok ($g2detail)"
        )
        results
    }

    fun readLast(filesDir: File): Results? {
        val f = File(filesDir, GATES_FILE)
        if (!f.exists()) return null
        return runCatching { Results.parse(f.readLines()) }.getOrNull()
    }

    private fun persist(filesDir: File, results: Results) {
        runCatching {
            val f = File(filesDir, GATES_FILE)
            f.parentFile?.mkdirs()
            f.writeText(results.toLines().joinToString("\n", postfix = "\n"))
        }
    }

    private fun chmod700(file: File) {
        val ok = runCatching {
            android.system.Os.chmod(file.absolutePath, 448)
            true
        }.getOrDefault(false) || runCatching {
            val p = ProcessBuilder("chmod", "700", file.absolutePath).start()
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)
        if (!ok) throw IllegalStateException("chmod 700 failed")
    }

    /** su -c 'exec <helper> <args...>'. Env assignments precede exec so the root shell applies them. */
    private fun suExec(
        helper: String,
        args: List<String>,
        env: Map<String, String> = emptyMap(),
        timeoutMs: Long = 20_000L
    ): Triple<String, String, Int> {
        val assignments = env.entries.joinToString(" ") { (k, v) -> "$k=\"$v\"" }
        val inner = buildString {
            if (assignments.isNotBlank()) append(assignments).append(' ')
            append("exec ").append(helper)
            args.forEach { append(' ').append(it) }
        }
        return ExecCapture.run(listOf("su", "-c", inner), timeoutMs)
    }

    private fun androidDns(app: Context): List<String> {
        return runCatching {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val props = cm?.getLinkProperties(cm.activeNetwork)
            props?.dnsServers?.mapNotNull { it.hostAddress?.takeIf { h -> h.isNotBlank() } }
        }.getOrNull().orEmpty().take(2)
    }

    private fun dnsEnv(dns: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        dns.getOrNull(0)?.let { map["KURO_DNS1"] = it }
        dns.getOrNull(1)?.let { map["KURO_DNS2"] = it }
        return map
    }
}
