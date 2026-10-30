package com.zeroflux.cprcoach.ui

import com.zeroflux.cprcoach.engine.PoseFrame
import com.zeroflux.cprcoach.engine.PoseSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class BeatIndicatorTest {
    private val period = 545.0

    @Test
    fun `swell is full on the beat and gone before the next`() {
        assertEquals(1f, BeatMath.swell(0.0), 1e-6f)
        assertEquals(0f, BeatMath.swell(0.6), 1e-6f)
        assertTrue(BeatMath.swell(0.1) > BeatMath.swell(0.3))
        assertEquals(0f, BeatMath.swell(5.0), 1e-6f)
    }

    @Test
    fun `offset is measured from the nearest beat`() {
        // Phase 0.25 now: the last beat was 136 ms ago.
        val now = 10_000L
        val lastBeat = now - 0.25 * period
        assertEquals(0.0, BeatMath.offsetMs((lastBeat).toLong(), now, 0.25, period)!!, 1.0)
        assertEquals(-100.0, BeatMath.offsetMs((lastBeat - 100).toLong(), now, 0.25, period)!!, 1.0)
        assertEquals(100.0, BeatMath.offsetMs((lastBeat - period + 100).toLong(), now, 0.25, period)!!, 1.0)
        // Just before the next beat counts as early for that beat, not as a late one.
        val early = BeatMath.offsetMs((lastBeat + period - 50).toLong(), now, 0.25, period)!!
        assertEquals(-50.0, early, 1.0)
        assertTrue(BeatMath.onBeat(early, period))
        assertFalse(BeatMath.onBeat(200.0, period))
        assertNull(BeatMath.offsetMs(0, 0, Double.NaN, period))
        assertNull(BeatMath.offsetMs(0, 0, 0.5, 0.0))
    }

    private fun frame(t: Long, wrist: Double?) =
        PoseFrame(t, wrist?.let { PoseSample(t, it, 170.0, 0.9f) }, null, 40L)

    @Test
    fun `push log holds only detected pushes and caps its size`() {
        val log = PushLog()
        val out = LongArray(8)
        // Flat wrist: nothing detected, nothing shown.
        for (i in 0 until 100) log.onFrame(frame(i * 33L, -1.0))
        assertEquals(0, log.snapshot(out))
        // A real 110 per minute rhythm.
        for (i in 0 until 600) {
            val t = 5_000L + i * 33L
            log.onFrame(frame(t, -1.0 + 0.15 * cos(2 * PI * 110 / 60.0 * (t - 5000) / 1000.0)))
        }
        val n = log.snapshot(out)
        assertEquals(8, n)
        assertTrue((1 until n).all { out[it] > out[it - 1] })
        assertNotNull(out[0])
        log.clear()
        assertEquals(0, log.snapshot(out))
        log.onFrame(frame(1, null)) // no person is not an error
    }
}
