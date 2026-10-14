package com.kuro.app.engine.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Sha256Test {

    @Test
    fun `known vector for abc`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.ofBytes("abc".toByteArray())
        )
    }

    @Test
    fun `file digest matches bytes digest`() {
        val tmp = Files.createTempFile("kuro-sha", ".bin").toFile()
        try {
            tmp.writeBytes(ByteArray(300_000) { (it % 251).toByte() })
            assertEquals(Sha256.ofBytes(tmp.readBytes()), Sha256.ofFile(tmp))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `different content differs`() {
        assertNotEquals(Sha256.ofBytes("a".toByteArray()), Sha256.ofBytes("b".toByteArray()))
    }
}

class TarPathGuardTest {
    private val root = File("/data/data/com.kuro.app.debug/files/distros/proot/x")

    @Test
    fun `normal relative paths resolve inside root`() {
        val out = TarPathGuard.resolve(root, "etc/resolv.conf")
        assertEquals(File(root, "etc/resolv.conf").absolutePath, out?.absolutePath)
    }

    @Test
    fun `dot segments normalize safely`() {
        val out = TarPathGuard.resolve(root, "./usr/./lib/os-release")
        assertEquals(File(root, "usr/lib/os-release").absolutePath, out?.absolutePath)
    }

    @Test
    fun `absolute paths rejected`() {
        assertEquals(null, TarPathGuard.resolve(root, "/etc/passwd"))
    }

    @Test
    fun `parent escape rejected`() {
        assertEquals(null, TarPathGuard.resolve(root, "../evil"))
        assertEquals(null, TarPathGuard.resolve(root, "a/../../evil"))
        assertEquals(null, TarPathGuard.resolve(root, ".."))
    }

    @Test
    fun `inner dotdot that stays inside is allowed`() {
        val out = TarPathGuard.resolve(root, "usr/lib/../bin/sh")
        assertEquals(File(root, "usr/bin/sh").absolutePath, out?.absolutePath)
    }

    @Test
    fun `blank and wrapper-only names rejected`() {
        assertEquals(null, TarPathGuard.resolve(root, ""))
        assertEquals(null, TarPathGuard.resolve(root, "./"))
    }
}
