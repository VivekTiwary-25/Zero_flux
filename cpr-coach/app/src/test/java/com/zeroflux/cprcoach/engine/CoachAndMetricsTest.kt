package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachAndMetricsTest {

    private fun beating(t: Long, rate: Int, bent: Boolean = false) =
        LiveReading(t, TrackingState.BEATING, rate, if (bent) 130.0 else 170.0, bent)

    private fun paused(t: Long) = LiveReading(t, TrackingState.PAUSED, null, 170.0, false)

    private fun lost(t: Long) = LiveReading(t, TrackingState.LOST, null, null, null)

    /** Runs the policy every 250 ms and returns (time, phrase) for each phrase it spoke. */
    private fun run(policy: CoachPolicy, fromMs: Long, toMs: Long, make: (Long) -> LiveReading) =
        (fromMs until toMs step 250).mapNotNull { t -> policy.update(make(t))?.let { t to it } }

    @Test
    fun `a correction waits for steady readings`() {
        val spoken = run(CoachPolicy(), 0, 5_000) { beating(it, 90) }
        assertEquals(Phrase.PUSH_FASTER, spoken.single().second)
        assertTrue(spoken.single().first >= 3_000)
    }

    @Test
    fun `a brief wobble says nothing`() {
        val policy = CoachPolicy()
        val spoken = run(policy, 0, 12_000) { t -> beating(t, if (t % 4_000 < 2_000) 90 else 110) }
        assertFalse(spoken.any { it.second == Phrase.PUSH_FASTER })
    }

    @Test
    fun `never repeats the same phrase back to back`() {
        val spoken = run(CoachPolicy(), 0, 60_000) { beating(it, 90) }
        assertEquals(listOf(Phrase.PUSH_FASTER), spoken.map { it.second })
    }

    @Test
    fun `rate outranks elbows`() {
        val spoken = run(CoachPolicy(), 0, 5_000) { beating(it, 130, bent = true) }
        assertEquals(Phrase.SLOW_DOWN, spoken.single().second)
    }

    @Test
    fun `respects the cooldown between different phrases`() {
        val policy = CoachPolicy(cooldownMs = 6_000)
        val spoken = run(policy, 0, 20_000) { t -> beating(t, if (t < 5_000) 90 else 130) }
        assertEquals(listOf(Phrase.PUSH_FASTER, Phrase.SLOW_DOWN), spoken.map { it.second })
        assertTrue(spoken[1].first - spoken[0].first >= 6_000)
    }

    @Test
    fun `tracking lost is silent`() {
        assertTrue(run(CoachPolicy(), 0, 30_000) { lost(it) }.isEmpty())
    }

    @Test
    fun `keep going after a real pause`() {
        val policy = CoachPolicy()
        run(policy, 0, 8_000) { beating(it, 110) }
        val spoken = run(policy, 8_000, 12_000) { paused(it) }
        assertEquals(Phrase.KEEP_GOING, spoken.first().second)
    }

    @Test
    fun `scorecard counts only measured time`() {
        val metrics = SessionMetrics()
        var t = 0L
        repeat(40) { metrics.add(beating(t, 110)); t += 250 } // 10 s in range
        repeat(40) { metrics.add(beating(t, 90)); t += 250 } // 10 s too slow
        repeat(40) { metrics.add(lost(t)); t += 250 } // 10 s unseen
        repeat(16) { metrics.add(paused(t)); t += 250 } // 4 s paused
        val card = metrics.scorecard()
        assertEquals(20, card.measuredSeconds)
        assertEquals(10, card.secondsInRange)
        assertEquals(100, card.averageRate)
        assertEquals(1, card.pauses)
    }

    @Test
    fun `scorecard shows nothing when nothing was seen`() {
        val metrics = SessionMetrics()
        var t = 0L
        repeat(40) { metrics.add(lost(t)); t += 250 }
        val card = metrics.scorecard()
        assertNull(card.averageRate)
        assertNull(card.elbowsStraightPercent)
    }

    @Test
    fun `analyzer never reports a number once tracking is lost`() {
        val analyzer = LiveAnalyzer()
        var t = 0L
        repeat(200) {
            val wave = kotlin.math.sin(2 * Math.PI * t / 1000.0 * 110 / 60)
            analyzer.addFrame(PoseSample(t, 1.2 + 0.08 * wave, 170.0, 0.9f))
            t += 40
        }
        val tracked = analyzer.read(t)
        assertEquals(TrackingState.BEATING, tracked.tracking)
        assertEquals(110.0, tracked.ratePerMinute!!.toDouble(), 3.0)

        repeat(30) {
            analyzer.addFrame(PoseSample(t, 1.2, 170.0, 0.1f))
            t += 40
        }
        val gone = analyzer.read(t)
        assertEquals(TrackingState.LOST, gone.tracking)
        assertNull(gone.ratePerMinute)
    }
}
