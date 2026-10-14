package com.kuro.app.engine.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfProbeTest {

    private fun header(
        eiClass: Int = 2,
        eiData: Int = 1,
        eType: Int = 3,
        eMachine: Int = 183,
        phoff: Long = 64,
        withInterp: Boolean = false,
        interp: String = "/lib/ld-linux-aarch64.so.1"
    ): ByteArray {
        val order = if (eiData == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val interpBytes = if (withInterp) (interp + "\u0000").toByteArray() else ByteArray(0)
        val phSize = 56
        val total = (phoff + phSize + interpBytes.size).toInt().coerceAtLeast(4096)
        val buf = ByteBuffer.allocate(total).order(order)
        buf.put(0x7F.toByte()).put('E'.code.toByte()).put('L'.code.toByte()).put('F'.code.toByte())
        buf.put(eiClass.toByte()).put(eiData.toByte()).put(1).put(0)
        buf.position(16)
        buf.putShort(eType.toShort())
        buf.putShort(eMachine.toShort())
        buf.putInt(1) // version
        // e_entry(24:8) e_phoff(32:8) e_shoff(40:8) e_flags(48:4)
        // e_ehsize(52:2) e_phentsize(54:2) e_phnum(56:2) ...
        // Write absolute:
        put64(buf, order, 24, 0L)
        put64(buf, order, 32, phoff)
        put64(buf, order, 40, 0L)
        put32(buf, order, 48, 0)
        put16(buf, order, 52, 64)
        put16(buf, order, 54, 56)
        put16(buf, order, 56, 1)
        // program header at phoff: p_type(4) p_flags(4) p_offset(8) p_vaddr(8)
        // p_paddr(8) p_filesz(8) p_memsz(8) p_align(8)
        val p = phoff.toInt()
        put32(buf, order, p, if (withInterp) 3 else 1)
        put32(buf, order, p + 4, 5)
        put64(buf, order, p + 8, (phoff + phSize).toLong())
        put64(buf, order, p + 16, 0L)
        put64(buf, order, p + 24, 0L)
        put64(buf, order, p + 32, interpBytes.size.toLong())
        put64(buf, order, p + 40, interpBytes.size.toLong())
        put64(buf, order, p + 48, 1L)
        System.arraycopy(interpBytes, 0, buf.array(), (phoff + phSize).toInt(), interpBytes.size)
        return buf.array()
    }

    private fun put16(buf: ByteBuffer, order: ByteOrder, at: Int, v: Int) {
        buf.order(order).putShort(at, v.toShort())
    }

    private fun put32(buf: ByteBuffer, order: ByteOrder, at: Int, v: Int) {
        buf.order(order).putInt(at, v)
    }

    private fun put64(buf: ByteBuffer, order: ByteOrder, at: Int, v: Long) {
        buf.order(order).putLong(at, v)
    }

    private fun report(bytes: ByteArray): ElfReport? {
        val tmp = File.createTempFile("elfprobe", ".bin").apply { writeBytes(bytes); deleteOnExit() }
        return ElfProbe.inspect(tmp)
    }

    @Test
    fun `static-pie aarch64 passes`() {
        val r = report(header())!!
        assertTrue(r.is64Bit && r.isAarch64 && r.isPie && !r.hasInterp)
        assertNull(ElfProbe.verdict(r))
        assertEquals(5, r.proofLines().size)
    }

    @Test
    fun `non-pie exec rejected with reason`() {
        val r = report(header(eType = 2))!!
        val v = ElfProbe.verdict(r)
        assertNotNull(v)
        assertTrue(v!!.contains("non-PIE"))
    }

    @Test
    fun `linux loader interp rejected with path`() {
        val r = report(header(withInterp = true))!!
        val v = ElfProbe.verdict(r)
        assertNotNull(v)
        assertTrue(v!!.contains("/lib/ld-linux-aarch64.so.1"))
    }

    @Test
    fun `android loader interp accepted`() {
        val r = report(header(withInterp = true, interp = "/system/bin/linker64"))!!
        assertNull(ElfProbe.verdict(r))
    }

    @Test
    fun `wrong machine rejected`() {
        val r = report(header(eMachine = 62))!!
        assertTrue(ElfProbe.verdict(r)!!.contains("183"))
    }

    @Test
    fun `non-elf returns null`() {
        val tmp = File.createTempFile("notelf", ".bin").apply {
            writeBytes("hello world, definitely not elf".toByteArray())
            deleteOnExit()
        }
        assertNull(ElfProbe.inspect(tmp))
    }

    @Test
    fun `apkindex parser finds proot-static version`() {
        val index = "C:abc123\nP:busybox-static\nV:1.36.1-r0\n\n" +
            "C:def456\nP:proot-static\nV:5.4.0-r2\nA:aarch64\n\n" +
            "P:other\nV:9\n"
        assertEquals("5.4.0-r2", ProotAcquisition.parseApkIndexVersion(index, "proot-static"))
        assertEquals(null, ProotAcquisition.parseApkIndexVersion(index, "missing"))
    }
}
