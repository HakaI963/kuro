package com.kuro.app.core.system

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.kuro.app.core.utils.formatBytes
import java.io.File

/**
 * Step 4 storage layout (see docs/STAGE2.md):
 * - proot containers live in Kuro private data: filesDir/containers/<id>
 * - chroot containers live at /data/local/kuro/chroot/<id>
 * - every container gets its own isolated filesystem (one dir per container)
 */
object StoragePaths {
    const val CHROOT_BASE = "/data/local/kuro"
    const val CHROOT_DIR = "/data/local/kuro/chroot"

    fun chrootPreview(id: String = "<id>"): String = "$CHROOT_DIR/$id"

    fun prootBase(appFilesDir: File): File = File(appFilesDir, "containers")

    /** Step 5 layout: filesDir/distros/proot/<id>/rootfs (own fs per container). */
    fun prootDistrosDir(appFilesDir: File): File = File(File(appFilesDir, "distros"), "proot")

    fun prootContainerDir(appFilesDir: File, id: String): File =
        File(prootDistrosDir(appFilesDir), id)

    fun prootRootfsDir(appFilesDir: File, id: String): File =
        File(prootContainerDir(appFilesDir, id), "rootfs")

    /** Tarball cache (reusable for re-creates). */
    fun downloadCacheDir(cacheDir: File): File = File(cacheDir, "downloads")

    fun tarballFile(cacheDir: File, distroId: String, url: String): File {
        val base = url.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "$distroId.tar.xz"
        return File(downloadCacheDir(cacheDir), "$distroId-$base")
    }

    fun deviceFreeLabel(): String {
        val free = runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        }.getOrDefault(0L)
        return formatBytes(free)
    }

    fun containerSizeLabel(dir: File?): String =
        if (dir == null || !dir.exists()) "−"
        else formatBytes(SystemMetricsReader.dirSizeNow(dir) ?: 0L)
}

/**
 * Kuro Bridge (Step 7): lets distro containers control Kuro clones via the
 * `kuro` command over localhost. Values below are STATIC PLACEHOLDERS until
 * Step 7 implements the service — the Settings card labels them as such.
 */
object BridgeInfo {
    const val PLACEHOLDER_PORT = 17355
    const val PLACEHOLDER_TOKEN = "kuro-bridge-token"
    const val LOOPBACK = "127.0.0.1"

    fun appContext(context: Context): Context = context.applicationContext
}
