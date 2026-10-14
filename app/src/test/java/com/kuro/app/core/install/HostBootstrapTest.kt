package com.kuro.app.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostBootstrapTest {

    @Test
    fun pin_isOnRootfsTag() {
        assertEquals(
            "bootstrap_com.kuro.app.tar",
            HostBootstrap.KURO.fileName
        )
        assertTrue(HostBootstrap.KURO.sha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(HostBootstrap.KURO.url.startsWith(HostBootstrap.RELEASE_BASE))
        assertTrue(HostBootstrap.KURO.url.endsWith(HostBootstrap.KURO.fileName))
        assertTrue(HostBootstrap.KURO.minBytes >= 50L * 1024L * 1024L)
    }

    @Test
    fun forApplicationId_selectsKuroPin() {
        assertEquals(
            HostBootstrap.KURO,
            HostBootstrap.forApplicationId("com.kuro.app")
        )
        assertEquals(HostBootstrap.KURO, HostBootstrap.forApplicationId("unknown"))
    }

    @Test
    fun downloadsFromRelease_singleAppShipsBootstrap() {
        assertFalse(HostBootstrap.downloadsFromRelease("com.kuro.app"))
    }
}
