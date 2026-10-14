package com.kuro.app.engine.proot

import java.io.File
import java.util.TimeZone

/**
 * Step 5: builds the exact proot launch command per session.
 *
 *   proot -r <rootfs> -b /dev -b /proc -b /sys -b /sdcard -b /storage
 *         -b /data/local/tmp:/tmp -w /root -0 <shell> -l
 *
 * <shell> is the configured bash/zsh when present in the rootfs, else /bin/sh.
 * Bind rationale (see docs/STEP5.md): /dev + /proc + /sys for a functional
 * userspace, /sdcard + /storage for device files, host /data/local/tmp as
 * guest /tmp (Android has no shared /tmp). -w /root working dir, -0 fakeroot.
 */
object ProotLauncher {
    private val SHELL_CANDIDATES = listOf(
        "bin/bash", "usr/bin/bash",
        "bin/zsh", "usr/bin/zsh",
        "bin/sh"
    )

    data class LaunchPlan(
        val shellPath: String,
        val cwd: String,
        val args: List<String>,
        val env: List<String>,
        val shellLabel: String
    )

    fun plan(
        prootBin: File,
        rootfs: File,
        containerId: String,
        shellPref: String,
        bridgeEnabled: Boolean
    ): LaunchPlan {
        val shell = pickShell(rootfs, shellPref)
        val tz = runCatching { TimeZone.getDefault().id }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "UTC"
        val args = listOf(
            prootBin.absolutePath,
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "/sdcard",
            "-b", "/storage",
            "-b", "/data/local/tmp:/tmp",
            "-w", "/root",
            "-0",
            shell, "-l"
        )
        val env = listOf(
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "TZ=$tz",
            "KURO_CONTAINER=$containerId",
            "KURO_BRIDGE=${if (bridgeEnabled) 1 else 0}"
        )
        return LaunchPlan(
            shellPath = prootBin.absolutePath,
            cwd = "/",
            args = args,
            env = env,
            shellLabel = shell
        )
    }

    private fun pickShell(rootfs: File, pref: String): String {
        val prefLower = pref.lowercase()
        val ordered = SHELL_CANDIDATES.sortedBy { candidate ->
            when {
                candidate.contains(prefLower) -> 0
                candidate.endsWith("/sh") -> 2
                else -> 1
            }
        }
        // Return the guest-absolute path of the first candidate present.
        ordered.forEach { rel ->
            if (File(rootfs, rel).exists()) return "/$rel"
        }
        return "/bin/sh"
    }
}
