package com.kuro.app.engine.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 5e: final executable path plumbing — provenance carries the path,
 * su fallback command shape, location-blocked detection.
 */
class ExecLocationTest {

    @Test
    fun `provenance round-trips the final path`() {
        val p = ProotProvenance(
            source = "apk-lib",
            sha256 = "abc123",
            versionLine = "proot 5.4.0",
            installedAt = 12345L,
            elfProof = listOf("PT_INTERP=absent (static)"),
            path = "/data/app/com.kuro.app.debug/lib/arm64/libkuro-proot.so"
        )
        val parsed = ProotProvenance.parse(p.toLines())
        assertEquals(p, parsed)
    }

    @Test
    fun `legacy provenance without path parses with null path`() {
        val parsed = ProotProvenance.parse(
            listOf(
                "source=bundled-asset",
                "sha256=?",
                "version=proot 5.3.0",
                "installedAt=999",
                "elf=PT_INTERP=absent (static)"
            )
        )
        assertEquals("bundled-asset", parsed!!.source)
        assertNull(parsed.path)
        assertEquals(listOf("PT_INTERP=absent (static)"), parsed.elfProof)
    }

    @Test
    fun `su fallback command refreshes dst from src as root`() {
        val cmd = ProotAcquisition.suInstallCommand(
            File("/data/data/x/files/bin/.proot-candidate"),
            File("/data/local/tmp/kuro-proot")
        )
        assertEquals(
            listOf(
                "su", "-c",
                "rm -f '/data/local/tmp/kuro-proot' && " +
                    "cp '/data/data/x/files/bin/.proot-candidate' " +
                    "'/data/local/tmp/kuro-proot' && " +
                    "chmod 700 '/data/local/tmp/kuro-proot'"
            ),
            cmd
        )
    }

    @Test
    fun `location-blocked means error13 spawn failure`() {
        assertTrue(
            ProotAcquisition.isLocationBlocked("execve: error=13, Permission denied", -1)
        )
        assertTrue(ProotAcquisition.isLocationBlocked("execve: EACCES", -1))
        // Real binary failures must NOT count as location-blocked.
        assertFalse(ProotAcquisition.isLocationBlocked("", 1))
        assertFalse(ProotAcquisition.isLocationBlocked("bad version", 0))
        assertFalse(ProotAcquisition.isLocationBlocked("timeout after 10000ms", -2))
        assertFalse(ProotAcquisition.isLocationBlocked("execve: error=2, No such file", -1))
    }

    @Test
    fun `tmp fallback path is fixed`() {
        assertEquals("/data/local/tmp/kuro-proot", ProotAcquisition.tmpFallbackFile().absolutePath)
        assertEquals("libkuro-proot.so", ProotAcquisition.APK_LIB_NAME)
    }
}
