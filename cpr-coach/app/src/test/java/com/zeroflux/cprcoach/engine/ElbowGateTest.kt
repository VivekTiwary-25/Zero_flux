package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElbowGateTest {
    /** Feeds [seconds] of 25 fps frames and returns the last answer. */
    private fun feed(
        gate: ElbowGate,
        startMs: Long,
        seconds: Double,
        vis: Float = 0.95f,
        sideOn: Boolean = true,
        angle: (Int) -> Double,
    ): Boolean? {
        var result: Boolean? = null
        val n = (seconds * 25).toInt()
        for (i in 0..n) result = gate.update(startMs + i * 40L, angle(i), vis, sideOn)
        return result
    }

    private fun wobble(center: Double, amp: Double = 5.0) = { i: Int -> center + amp * Math.sin(i * 0.8) }

    @Test fun stableStraight() = assertEquals(false, feed(ElbowGate(), 0, 2.0, angle = wobble(165.0)))

    @Test fun stableBent() = assertEquals(true, feed(ElbowGate(), 0, 2.0, angle = wobble(80.0)))

    @Test fun middleAngleIsNotConfident() = assertNull(feed(ElbowGate(), 0, 2.0, angle = wobble(125.0)))

    @Test fun lowVisibilityGivesNull() = assertNull(feed(ElbowGate(), 0, 2.0, vis = 0.5f, angle = wobble(80.0)))

    @Test fun oneLowVisibilityFrameResetsConfidence() {
        val g = ElbowGate()
        assertEquals(true, feed(g, 0, 2.0, angle = wobble(80.0)))
        assertNull(g.update(2_040, 80.0, 0.3f, true))
        assertNull(g.update(2_080, 80.0, 0.95f, true))
    }

    @Test fun notSideOnGivesNull() = assertNull(feed(ElbowGate(), 0, 2.0, sideOn = false, angle = wobble(80.0)))

    @Test fun noisyGivesNull() = assertNull(feed(ElbowGate(), 0, 2.0, angle = { i -> if (i % 2 == 0) 60.0 else 170.0 }))

    @Test fun tooShortGivesNull() = assertNull(feed(ElbowGate(), 0, 1.0, angle = wobble(80.0)))

    @Test fun gapGivesNull() {
        val g = ElbowGate()
        assertEquals(true, feed(g, 0, 2.0, angle = wobble(80.0)))
        assertNull(g.update(5_000, 80.0, 0.95f, true))
        assertNull(feed(g, 5_040, 0.5, angle = wobble(80.0)))
    }

    @Test fun nanAndInfinityGiveNull() {
        val g = ElbowGate()
        assertEquals(true, feed(g, 0, 2.0, angle = wobble(80.0)))
        assertNull(g.update(2_040, Double.NaN, 0.95f, true))
        assertNull(g.update(2_080, 80.0, Float.NaN, true))
        assertNull(g.update(2_120, Double.POSITIVE_INFINITY, 0.95f, true))
        assertNull(g.update(2_160, 80.0, 0.95f, true))
    }

    @Test fun timeGoingBackwardsGivesNull() {
        val g = ElbowGate()
        assertEquals(true, feed(g, 10_000, 2.0, angle = wobble(80.0)))
        assertNull(g.update(9_000, 80.0, 0.95f, true))
    }

    @Test fun hysteresisKeepsBentUntilClearlyRecovered() {
        val g = ElbowGate()
        assertEquals(true, feed(g, 0, 2.0, angle = wobble(80.0)))
        // Median 108: above the enter threshold but below the exit threshold, so still bent.
        assertEquals(true, feed(g, 2_040, 2.0, angle = wobble(108.0, 2.0)))
        // Median 108 from a fresh start is not bent.
        assertNull(feed(ElbowGate(), 0, 2.0, angle = wobble(108.0, 2.0)))
        // Clear recovery leaves bent, then straight.
        assertEquals(false, feed(g, 4_080, 2.0, angle = wobble(165.0)))
    }

    @Test fun resetForgetsEverything() {
        val g = ElbowGate()
        assertEquals(true, feed(g, 0, 2.0, angle = wobble(80.0)))
        g.reset()
        assertNull(g.update(2_040, 80.0, 0.95f, true))
    }
}
