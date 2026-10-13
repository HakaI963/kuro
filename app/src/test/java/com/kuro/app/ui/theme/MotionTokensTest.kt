package com.kuro.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTokensTest {

    @Test
    fun `token durations match contract`() {
        assertEquals(150, MotionTokens.FAST_MS)
        assertEquals(300, MotionTokens.MEDIUM_MS)
        assertEquals(450, MotionTokens.SLOW_MS)
        assertEquals(1500, MotionTokens.INTRO_TOTAL_MS)
    }
}
