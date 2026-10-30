package com.zeroflux.cprcoach.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedFormatTest {
    @Test fun `formats minutes and seconds`() {
        assertEquals("00:00", formatElapsed(0))
        assertEquals("01:05", formatElapsed(65))
        assertEquals("60:00", formatElapsed(3600))
    }

    @Test fun `negative time shows zero`() {
        assertEquals("00:00", formatElapsed(-5))
    }
}
