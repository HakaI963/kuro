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
}
