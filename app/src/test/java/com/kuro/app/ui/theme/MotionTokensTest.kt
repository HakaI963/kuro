package com.kuro.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTokensTest {

    @Test
    fun `stagger grows 12ms per item and caps`() {
        assertEquals(0, MotionTokens.staggerDelayMs(0))
        assertEquals(12, MotionTokens.staggerDelayMs(1))
        assertEquals(96, MotionTokens.staggerDelayMs(8))
        assertEquals(12 * MotionTokens.STAGGER_MAX_ITEMS, MotionTokens.staggerDelayMs(100))
        assertEquals(0, MotionTokens.staggerDelayMs(-1))
    }

    @Test
    fun `token durations match contract`() {
        assertEquals(150, MotionTokens.FAST_MS)
        assertEquals(300, MotionTokens.MEDIUM_MS)
        assertEquals(450, MotionTokens.SLOW_MS)
        assertEquals(1500, MotionTokens.INTRO_TOTAL_MS)
    }
}
