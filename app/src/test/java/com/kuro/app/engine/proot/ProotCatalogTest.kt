package com.kuro.app.engine.proot

import com.kuro.app.ui.screens.terminal.DistroCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotCatalogTest {

    @Test
    fun `catalog covers the five supported distros`() {
        val ids = DistroCatalog.entries.map { it.id }.toSet()
        assertEquals(setOf("fedora", "kali", "ubuntu", "debian", "alpine"), ids)
    }

    @Test
    fun `every entry is aarch64 with a real tarball URL and size`() {
        DistroCatalog.entries.forEach { entry ->
            assertEquals("aarch64", entry.arch)
            assertTrue(entry.tarballUrl.startsWith("https://"))
            assertTrue(entry.tarballUrl.endsWith(".tar.xz"))
            assertTrue(entry.tarballBytes > 0)
            assertTrue(entry.sourceUrl.startsWith("https://"))
        }
    }

    @Test
    fun `kali pins an upstream checksum, others are size-verified`() {
        val kali = DistroCatalog.byId("kali")
        assertNotNull(kali)
        assertTrue(kali!!.sha256!!.matches(Regex("[0-9a-f]{64}")))
        DistroCatalog.entries.filter { it.id != "kali" }.forEach {
            assertNull(it.sha256)
        }
    }

    @Test
    fun `lookup by id and display name`() {
        assertEquals("Ubuntu", DistroCatalog.byId("ubuntu")?.name)
        assertEquals("Ubuntu", DistroCatalog.displayNameOf("ubuntu"))
        assertEquals("nope", DistroCatalog.displayNameOf("nope"))
        assertNull(DistroCatalog.byId("nope"))
    }

    @Test
    fun `official fallbacks present except debian`() {
        val withFallback = setOf("fedora", "kali", "ubuntu", "alpine")
        DistroCatalog.entries.forEach { entry ->
            if (entry.id in withFallback) {
                assertNotNull("fallback for ${entry.id}", entry.fallbackUrl)
                val fb = entry.fallbackUrl!!
                assertTrue(fb.startsWith("https://"))
                assertTrue(fb != entry.tarballUrl)
                assertTrue("fallback bytes for ${entry.id}", entry.fallbackBytes > 0)
            } else {
                // Debian: no stable official direct rootfs tarball exists.
                assertEquals("debian", entry.id)
                assertNull(entry.fallbackUrl)
            }
        }
    }

    @Test
    fun `per-url expectations resolve to the right source`() {
        val alpine = DistroCatalog.byId("alpine")!!
        assertEquals(alpine.tarballBytes, alpine.expectedBytesFor(alpine.tarballUrl))
        assertEquals(alpine.fallbackBytes, alpine.expectedBytesFor(alpine.fallbackUrl!!))
        assertNull(alpine.sha256For(alpine.tarballUrl))
        assertEquals(alpine.fallbackSha256, alpine.sha256For(alpine.fallbackUrl!!))
        val kali = DistroCatalog.byId("kali")!!
        assertEquals(kali.sha256, kali.sha256For(kali.tarballUrl))
        assertEquals(kali.fallbackSha256, kali.sha256For(kali.fallbackUrl!!))
    }
}
