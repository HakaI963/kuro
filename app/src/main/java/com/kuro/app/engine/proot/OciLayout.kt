package com.kuro.app.engine.proot

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Step 5d: minimal OCI image-layout support for official-vendor fallback
 * tarballs that ship as OCI images (Fedora Container Base `*.oci.tar.xz`).
 *
 * Only runs when the extracted staging dir holds an OCI layout
 * (`oci-layout` + `index.json`); primary proot-distro tarballs never do, so
 * this path cannot regress them. Layers apply in listed order (later wins);
 * overlay whiteout markers (`.wh.*`) are skipped. Any problem throws an
 * IOException with an honest message — the caller surfaces it as a typed
 * extract failure, never a fake rootfs.
 *
 * Pure JVM (no Android APIs) — covered by unit tests with synthetic tarballs.
 */
object OciLayout {

    fun isOciStaging(staging: File): Boolean =
        File(staging, "oci-layout").isFile && File(staging, "index.json").isFile

    /** First sha256 digest after the "manifests" key in index.json. */
    fun manifestDigest(indexJson: String): String? {
        val section = indexJson.substringAfter("\"manifests\"", missingDelimiterValue = "")
        if (section.isEmpty()) return null
        return digestRegex().find(section)?.groupValues?.get(1)
    }

    /** Layer digests in order (config digest excluded via the "layers" scope). */
    fun layerDigests(manifestJson: String): List<String> {
        val section = manifestJson.substringAfter("\"layers\"", missingDelimiterValue = "")
        if (section.isEmpty()) return emptyList()
        return digestRegex().findAll(section).map { it.groupValues[1] }.toList()
    }

    private fun digestRegex() = Regex(""""digest"\s*:\s*"sha256:([0-9a-fA-F]{64})"""")

    /**
     * Replace the OCI layout inside [staging] with the materialized rootfs
     * from its layers. Returns the extracted regular-file count.
     */
    fun materialize(staging: File): Int {
        val indexText = File(staging, "index.json").readText()
        val manifestDigest = manifestDigest(indexText)
            ?: throw java.io.IOException("oci index.json has no manifest digest")
        val manifestText = runCatching {
            File(staging, "blobs/sha256/$manifestDigest").readText()
        }.getOrElse {
            throw java.io.IOException("oci manifest blob missing: ${manifestDigest.take(12)}")
        }
        val layers = layerDigests(manifestText)
        if (layers.isEmpty()) throw java.io.IOException("oci manifest lists no layers")
        val roots = File(staging.parentFile, staging.name + ".oci-roots")
        runCatching { roots.deleteRecursively() }
        roots.mkdirs()
        try {
            var files = 0
            layers.forEach { digest ->
                val blob = File(staging, "blobs/sha256/$digest")
                if (!blob.isFile) throw java.io.IOException("oci layer blob missing: ${digest.take(12)}")
                files += extractLayerBlob(blob, roots)
            }
            // Swap: wipe the OCI layout, move the rootfs into place.
            staging.listFiles()?.forEach {
                if (it.isDirectory) it.deleteRecursively() else it.delete()
            }
            var moved = 0
            roots.listFiles()?.forEach { child ->
                val dest = File(staging, child.name)
                if (child.renameTo(dest)) {
                    moved++
                } else {
                    if (child.isDirectory) child.copyRecursively(dest) else child.copyTo(dest)
                    moved++
                }
            }
            if (moved == 0) throw java.io.IOException("oci layers extracted nothing usable")
            return files
        } finally {
            runCatching { roots.deleteRecursively() }
        }
    }

    /**
     * Extract one layer blob (gzip, xz, or plain tar — sniffed by magic)
     * into [dest] with traversal guard + whiteout skip. Returns file count.
     */
    fun extractLayerBlob(blob: File, dest: File): Int {
        var files = 0
        BufferedInputStream(FileInputStream(blob)).use { fileIn ->
            openDecompressed(fileIn).use { decIn ->
                TarArchiveInputStream(decIn).use { tarIn ->
                    var entry = tarIn.nextEntry
                    while (entry != null) {
                        val name = (entry as TarArchiveEntry).name
                        if (isWhiteout(name)) {
                            entry = tarIn.nextEntry
                            continue
                        }
                        val target = TarPathGuard.resolve(dest, name)
                            ?: throw java.io.IOException("oci layer blocked path: ${name.take(120)}")
                        when {
                            entry.isDirectory -> target.mkdirs()
                            entry.isSymbolicLink -> {
                                target.parentFile?.mkdirs()
                                runCatching { java.nio.file.Files.deleteIfExists(target.toPath()) }
                                java.nio.file.Files.createSymbolicLink(
                                    target.toPath(),
                                    java.nio.file.Paths.get(entry.linkName)
                                )
                            }
                            entry.isFile -> {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { out ->
                                    val buf = ByteArray(128 * 1024)
                                    var remaining = entry.size
                                    while (remaining > 0) {
                                        val want = minOf(buf.size.toLong(), remaining).toInt()
                                        val n = tarIn.read(buf, 0, want)
                                        if (n < 0) break
                                        out.write(buf, 0, n)
                                        remaining -= n
                                    }
                                }
                                files++
                            }
                            // sockets/devices/fifos + hardlinks: skipped (bound at runtime)
                        }
                        entry = tarIn.nextEntry
                    }
                }
            }
        }
        return files
    }

    /** Overlay whiteout marker (file or opaque-dir marker): never materialize. */
    fun isWhiteout(entryName: String): Boolean =
        entryName.split('/').any { it.startsWith(".wh.") }

    /**
     * Wrap [input] in the decompressor matching its magic bytes (gzip, xz),
     * else treat it as a plain tar. The stream is peeked (mark/reset), never
     * consumed, before wrapping.
     */
    fun openDecompressed(input: BufferedInputStream): InputStream {
        input.mark(8)
        val head = ByteArray(8)
        var n = 0
        while (n < head.size) {
            val r = input.read(head, n, head.size - n)
            if (r < 0) break
            n += r
        }
        input.reset()
        val isGzip = n >= 2 && head[0] == 0x1F.toByte() && head[1] == 0x8B.toByte()
        val isXz = n >= 6 && head[0] == 0xFD.toByte() && head[1] == 0x37.toByte() &&
            head[2] == 0x7A.toByte() && head[3] == 0x58.toByte() &&
            head[4] == 0x5A.toByte() && head[5] == 0x00.toByte()
        return when {
            isGzip -> GZIPInputStream(input)
            isXz -> XZInputStream(input)
            else -> input
        }
    }
}
