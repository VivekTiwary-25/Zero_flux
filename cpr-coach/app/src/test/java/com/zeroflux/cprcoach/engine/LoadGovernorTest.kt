package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadGovernorTest {
    private fun feed(g: LoadGovernor, from: Long, seconds: Int, latency: Long?, thermal: Int = 0): Long {
        var t = from
        repeat(seconds * 10) { g.update(t, latency, thermal); t += 100 }
        return t
    }

    @Test fun emptyWindowDoesNotCrashOrMove() {
        val g = LoadGovernor()
        assertEquals(60L, g.update(0, null, 0))
        assertEquals(60L, g.update(10_000, null, 1))
    }

    @Test fun highLatencyStepsDownThenStops() {
        val g = LoadGovernor()
        feed(g, 0, 60, 300)
        assertEquals(2, g.step)
        assertEquals(90L, g.gapMs)
    }

    @Test fun thermalModerateStepsDownImmediately() {
        val g = LoadGovernor()
        assertEquals(75L, g.update(0, null, 2))
        assertEquals(90L, g.update(1, null, 3))
    }

    @Test fun stepsUpSlowlyAfterCoolingNotEverySecond() {
        val g = LoadGovernor(2)
        var t = feed(g, 0, 20, 60)
        assertEquals(2, g.step)
        t = feed(g, t, 20, 60)
        assertEquals(1, g.step)
        feed(g, t, 5, 60)
        assertEquals(1, g.step)
    }

    @Test fun hotPhoneNeverStepsUp() {
        val g = LoadGovernor(2)
        feed(g, 0, 120, 50, thermal = 2)
        assertEquals(2, g.step)
    }

    @Test fun neverBelowTenFps() {
        assertTrue(LoadGovernor.GAPS_MS.all { it <= 100L })
    }

    @Test fun midLatencyHolds() {
        val g = LoadGovernor(1)
        feed(g, 0, 120, 120)
        assertEquals(1, g.step)
    }

    @Test
    fun `every step stays above the rate estimator's minimum on a 30 fps camera`() {
        val cameraFrameMs = 1000.0 / 30
        for (gap in LoadGovernor.GAPS_MS) {
            val framesWaited = kotlin.math.ceil(gap / cameraFrameMs)
            val perSecond = 1000.0 / (framesWaited * cameraFrameMs)
            assertTrue("gap $gap gives $perSecond a second", perSecond >= 10.0)
        }
    }
}
