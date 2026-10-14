package com.kuro.app.engine.proot

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Step 5d: OCI layout detection/parsing + compression sniffing. */
class OciLayoutTest {

    private val indexJson = """
        {"schemaVersion":2,"mediaType":"application/vnd.oci.image.index.v1+json",
         "manifests":[{"mediaType":"application/vnd.oci.image.manifest.v1+json",
         "digest":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
         "size":525}]}
    """.trimIndent()

    private val manifestJson = """
        {"schemaVersion":2,
         "config":{"mediaType":"application/vnd.oci.image.config.v1+json",
          "digest":"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
          "size":500},
         "layers":[{"mediaType":"application/vnd.oci.image.layer.v1.tar+gzip",
          "digest":"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
          "size":12345}]}
    """.trimIndent()

    @Test
    fun `manifest digest parsed from index`() {
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            OciLayout.manifestDigest(indexJson)
        )
    }

    @Test
    fun `layer digests exclude config digest`() {
        assertEquals(
            listOf("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"),
            OciLayout.layerDigests(manifestJson)
        )
    }

    @Test
    fun `missing keys parse empty`() {
        assertEquals(null, OciLayout.manifestDigest("{}"))
        assertEquals(emptyList<String>(), OciLayout.layerDigests("{}"))
        assertEquals(emptyList<String>(), OciLayout.layerDigests("""{"layers":[]}"""))
    }

    @Test
    fun `whiteout markers detected`() {
        assertTrue(OciLayout.isWhiteout("etc/.wh.hostname"))
        assertTrue(OciLayout.isWhiteout(".wh..wh..opq"))
        assertTrue(OciLayout.isWhiteout("usr/lib/.wh.foo"))
        assertFalse(OciLayout.isWhiteout("etc/hostname"))
        assertFalse(OciLayout.isWhiteout("bin/sh"))
    }

    @Test
    fun `gzip magic dispatches to gzip`() {
        val raw = "hello-oci".toByteArray()
        val gz = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(raw) }
        }.toByteArray()
        val opened = OciLayout.openDecompressed(BufferedInputStream(ByteArrayInputStream(gz)))
        assertTrue(opened is GZIPInputStream)
        assertEquals("hello-oci", opened.use { it.readBytes().toString(Charsets.UTF_8) })
    }

    @Test
    fun `xz magic dispatches to xz reader`() {
        val xzMagic = byteArrayOf(
            0xFD.toByte(), 0x37.toByte(), 0x7A.toByte(),
            0x58.toByte(), 0x5A.toByte(), 0x00.toByte(), 0x01, 0x02
        )
        val opened = OciLayout.openDecompressed(BufferedInputStream(ByteArrayInputStream(xzMagic)))
        assertTrue(opened is XZInputStream)
        runCatching { opened.close() }
    }

    @Test
    fun `plain tar passes through`() {
        val tar = buildTar(mapOf("etc/hostname" to "kuro".toByteArray()))
        val opened = OciLayout.openDecompressed(BufferedInputStream(ByteArrayInputStream(tar)))
        assertFalse(opened is GZIPInputStream)
        assertFalse(opened is XZInputStream)
    }

    @Test
    fun `oci staging detected only with layout plus index`() {
        val dir = Files.createTempDirectory("oci-detect").toFile()
        try {
            assertFalse(OciLayout.isOciStaging(dir))
            File(dir, "index.json").writeText(indexJson)
            assertFalse(OciLayout.isOciStaging(dir))
            File(dir, "oci-layout").writeText("""{"imageLayoutVersion":"1.0.0"}""")
            assertTrue(OciLayout.isOciStaging(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `materialize builds rootfs from gzip layer and drops layout`() {
        val dir = Files.createTempDirectory("oci-mat").toFile()
        try {
            val layerTar = buildTar(
                mapOf(
                    "etc/hostname" to "kuro-fedora".toByteArray(),
                    "bin/sh" to "#!/bin/sh".toByteArray(),
                    "etc/.wh.stale" to "x".toByteArray()
                )
            )
            val layerGz = ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write(layerTar) }
            }.toByteArray()
            val layerDigest = Sha256.ofBytes(layerGz)
            val manifest = manifestJson.replace(
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                layerDigest
            )
            val manifestDigest = Sha256.ofBytes(manifest.toByteArray())
            val index = indexJson.replace(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                manifestDigest
            )
            File(dir, "oci-layout").writeText("""{"imageLayoutVersion":"1.0.0"}""")
            File(dir, "index.json").writeText(index)
            val blobs = File(dir, "blobs/sha256").also { it.mkdirs() }
            File(blobs, manifestDigest).writeText(manifest)
            File(blobs, layerDigest).writeBytes(layerGz)

            val files = OciLayout.materialize(dir)

            assertTrue(files >= 2)
            assertEquals("kuro-fedora", File(dir, "etc/hostname").readText())
            assertTrue(File(dir, "bin/sh").isFile)
            // Whiteout marker never materializes.
            assertFalse(File(dir, "etc/.wh.stale").exists())
            // OCI bookkeeping is gone; a plain rootfs remains.
            assertFalse(File(dir, "index.json").exists())
            assertFalse(File(dir, "blobs").exists())
            assertFalse(OciLayout.isOciStaging(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `layer blob rejects traversal`() {
        val dir = Files.createTempDirectory("oci-evil").toFile()
        try {
            val evil = buildTar(mapOf("../evil" to "x".toByteArray()))
            val blob = File(dir, "evil.tar").also { it.writeBytes(evil) }
            var failed = false
            try {
                OciLayout.extractLayerBlob(blob, File(dir, "out").also { it.mkdirs() })
            } catch (_: java.io.IOException) {
                failed = true
            }
            assertTrue(failed)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun buildTar(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(out).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            files.forEach { (name, bytes) ->
                val entry = TarArchiveEntry(name)
                entry.size = bytes.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
        return out.toByteArray()
    }
}
