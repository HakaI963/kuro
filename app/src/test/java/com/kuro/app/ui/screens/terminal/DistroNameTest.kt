package com.kuro.app.ui.screens.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 5d: distro-derived wizard names, user edits preserved. */
class DistroNameTest {

    @Test
    fun `pristine field auto-defaults to selected distro`() {
        assertEquals("alpine", DistroNamePolicy.onDistroSelected("", null, "alpine"))
        assertEquals("debian", DistroNamePolicy.onDistroSelected("   ", null, "debian"))
    }

    @Test
    fun `field holding previous auto follows distro changes`() {
        // Select alpine -> "alpine"; switch to debian -> "debian".
        val first = DistroNamePolicy.onDistroSelected("", null, "alpine")
        assertEquals("alpine", first)
        assertEquals("debian", DistroNamePolicy.onDistroSelected(first, "alpine", "debian"))
    }

    @Test
    fun `user-typed text survives distro changes`() {
        assertEquals("mybox", DistroNamePolicy.onDistroSelected("mybox", "alpine", "debian"))
        assertEquals("mybox", DistroNamePolicy.onDistroSelected("mybox", "debian", "fedora"))
        assertEquals("mybox", DistroNamePolicy.onDistroSelected("mybox", null, "ubuntu"))
    }

    @Test
    fun `cleared field re-arms auto-default`() {
        assertEquals("fedora", DistroNamePolicy.onDistroSelected("", "alpine", "fedora"))
    }

    @Test
    fun `pristine detection`() {
        assertTrue(DistroNamePolicy.isPristine("", null))
        assertTrue(DistroNamePolicy.isPristine("", "alpine"))
        assertTrue(DistroNamePolicy.isPristine("alpine", "alpine"))
        assertFalse(DistroNamePolicy.isPristine("mybox", "alpine"))
        assertFalse(DistroNamePolicy.isPristine("mybox", null))
    }

    @Test
    fun `placeholder is computed per distro`() {
        assertEquals("e.g. alpine-mobile", DistroNamePolicy.placeholder("alpine"))
        assertEquals("e.g. debian-mobile", DistroNamePolicy.placeholder("debian"))
        assertEquals("e.g. kali-mobile", DistroNamePolicy.placeholder("kali"))
        // No selection yet: generic, still no hardcoded distro example.
        assertEquals("e.g. container-mobile", DistroNamePolicy.placeholder(null))
        assertEquals("e.g. container-mobile", DistroNamePolicy.placeholder(""))
    }

    @Test
    fun `full wizard walk matches definition of done`() {
        // Select alpine -> field shows "alpine", placeholder "e.g. alpine-mobile".
        var name = DistroNamePolicy.onDistroSelected("", null, "alpine")
        var auto: String? = if (name == "alpine") "alpine" else null
        assertEquals("alpine", name)
        assertEquals("e.g. alpine-mobile", DistroNamePolicy.placeholder("alpine"))
        // Select debian -> updates (still auto).
        name = DistroNamePolicy.onDistroSelected(name, auto, "debian")
        auto = if (name == "debian") "debian" else auto
        assertEquals("debian", name)
        // User types custom text -> further changes preserve it.
        name = "pixel-desk"
        assertEquals("pixel-desk", DistroNamePolicy.onDistroSelected(name, auto, "ubuntu"))
    }
}
