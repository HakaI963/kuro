package com.kuro.app.engine.proot

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Step 5: streaming SHA-256 used to verify rootfs tarballs when the catalog
 * provides a checksum. Pure JVM — covered by unit tests.
 */
object Sha256 {
    fun ofBytes(bytes: ByteArray): String = hex(digest(bytes))

    fun ofFile(file: File, onBytes: (Long) -> Unit = {}): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(128 * 1024)
        var total = 0L
        FileInputStream(file).use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                total += n
                onBytes(total)
            }
        }
        return hex(md.digest())
    }

    private fun digest(bytes: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes)
    }

    private fun hex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        bytes.forEachIndexed { i, b ->
            val v = b.toInt() and 0xFF
            chars[i * 2] = Character.forDigit(v ushr 4, 16)
            chars[i * 2 + 1] = Character.forDigit(v and 0x0F, 16)
        }
        return String(chars)
    }
}
