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

    private fun pushing(t: Long, issue: PostureIssue?, tracking: TrackingState = TrackingState.PAUSED) = LiveReading(
        t, tracking, if (tracking == TrackingState.BEATING) 110 else null, 170.0, null, moving = true,
        posture = PostureReading(if (issue == null) true else false, issue, 170.0, null),
    )

    @Test
    fun `posture is corrected while pushing without a locked rate`() {
        val spoken = run(CoachPolicy(), 0, 5_000) { pushing(it, PostureIssue.ELBOWS_BENT) }
        assertEquals(Phrase.ELBOWS_STRAIGHT, spoken.single().second)
        assertTrue(spoken.single().first in 2_000..2_500)
    }

    @Test
    fun `posture is not corrected while paused and still`() {
        val spoken = run(CoachPolicy(), 0, 1_900) { t -> pushing(t, PostureIssue.ELBOWS_BENT).copy(moving = false) }
        assertTrue(spoken.isEmpty())
    }

    @Test
    fun `leaning says shoulders over hands`() {
        val spoken = run(CoachPolicy(), 0, 5_000) { pushing(it, PostureIssue.SHOULDERS_NOT_OVER_HANDS) }
        assertEquals(Phrase.SHOULDERS_OVER_HANDS, spoken.single().second)
    }

    @Test
    fun `shoulders over hands ranks below elbows and rate`() {
        val both = run(CoachPolicy(), 0, 4_000) { t ->
            pushing(t, PostureIssue.ELBOWS_BENT, TrackingState.BEATING).copy(elbowsBent = true)
        }
        assertEquals(Phrase.ELBOWS_STRAIGHT, both.single().second)
        val rate = run(CoachPolicy(), 0, 5_000) { t ->
            pushing(t, PostureIssue.SHOULDERS_NOT_OVER_HANDS, TrackingState.BEATING).copy(ratePerMinute = 90)
        }
        assertEquals(Phrase.PUSH_FASTER, rate.first().second)
    }

    @Test
    fun `a pause with flickering movement still says keep going`() {
        val policy = CoachPolicy()
        run(policy, 0, 8_000) { beating(it, 110) }
        val spoken = run(policy, 8_000, 13_000) { t -> paused(t).copy(moving = (t / 250) % 4 == 0L) }
        assertEquals(Phrase.KEEP_GOING, spoken.first().second)
    }

    @Test
    fun `pushing without a rate is not a pause`() {
        val policy = CoachPolicy()
        run(policy, 0, 8_000) { beating(it, 110) }
        val spoken = run(policy, 8_000, 20_000) { paused(it).copy(moving = true) }
        assertTrue(spoken.none { it.second == Phrase.KEEP_GOING })
    }

    @Test
    fun `lost tracking stays silent even with a posture issue`() {
        val spoken = run(CoachPolicy(), 0, 10_000) { lost(it).copy(moving = true, posture = PostureReading(false, PostureIssue.ELBOWS_BENT, null, null)) }
        assertTrue(spoken.isEmpty())
    }

    @Test
    fun `keep going after a real pause`() {
        val policy = CoachPolicy()
        run(policy, 0, 8_000) { beating(it, 110) }
        val spoken = run(policy, 8_000, 12_000) { paused(it) }
        assertEquals(Phrase.KEEP_GOING, spoken.first().second)
    }

    @Test
    fun `a new pause after real pushing says keep going again`() {
        val policy = CoachPolicy()
        run(policy, 0, 8_000) { beating(it, 110).copy(moving = true) }
        val first = run(policy, 8_000, 12_000) { paused(it) }
        run(policy, 12_000, 20_000) { beating(it, 110).copy(moving = true) }
        val second = run(policy, 20_000, 24_000) { paused(it) }
        assertEquals(listOf(Phrase.KEEP_GOING), first.map { it.second })
        assertEquals(listOf(Phrase.KEEP_GOING), second.map { it.second })
    }

    @Test
    fun `elbows can be corrected again once posture was good in between`() {
        val policy = CoachPolicy()
        val first = run(policy, 0, 6_000) { beating(it, 110, bent = true).copy(moving = true) }
        val stillBent = run(policy, 6_000, 12_000) { beating(it, 110, bent = true).copy(moving = true) }
        run(policy, 12_000, 20_000) { beating(it, 110).copy(moving = true) }
        val second = run(policy, 20_000, 26_000) { beating(it, 110, bent = true).copy(moving = true) }
        assertEquals(listOf(Phrase.ELBOWS_STRAIGHT), first.map { it.second })
        assertTrue(stillBent.isEmpty())
        assertEquals(listOf(Phrase.ELBOWS_STRAIGHT), second.map { it.second })
    }

    @Test
    fun `coaching carries on after a pause`() {
        val policy = CoachPolicy()
        run(policy, 0, 8_000) { beating(it, 110) }
        run(policy, 8_000, 10_250) { paused(it) }
        val spoken = run(policy, 10_250, 40_000) { beating(it, 90, bent = true) }
        assertTrue(spoken.any { it.second == Phrase.PUSH_FASTER })
        assertTrue(spoken.any { it.second == Phrase.ELBOWS_STRAIGHT })
    }

    @Test
    fun `lost tracking does not shorten the wait for a pause`() {
        val policy = CoachPolicy()
        run(policy, 0, 4_000) { beating(it, 110) }
        policy.update(paused(4_000))
        run(policy, 4_250, 5_750) { lost(it) }
        assertTrue(run(policy, 6_000, 7_500) { paused(it) }.isEmpty())
    }

    @Test
    fun `two hesitations split by lost tracking are not one pause`() {
        val metrics = SessionMetrics()
        var t = 0L
        repeat(4) { metrics.add(beating(t, 110)); t += 250 }
        repeat(4) { metrics.add(paused(t)); t += 250 }
        repeat(4) { metrics.add(lost(t)); t += 250 }
        repeat(4) { metrics.add(paused(t)); t += 250 }
        assertEquals(0, metrics.scorecard().pauses)
    }

    @Test
    fun `a rest with flickering movement counts one pause`() {
        val metrics = SessionMetrics()
        var t = 0L
        repeat(8) { metrics.add(beating(t, 110)); t += 250 }
        repeat(40) { metrics.add(paused(t).copy(moving = (t / 250) % 4 == 0L)); t += 250 } // 10 s rest, one read in four moves
        assertEquals(1, metrics.scorecard().pauses)
    }

    @Test
    fun `pushing without a rate is not a pause on the scorecard`() {
        val metrics = SessionMetrics()
        var t = 0L
        repeat(8) { metrics.add(beating(t, 110)); t += 250 }
        repeat(40) { metrics.add(paused(t).copy(moving = (t / 250) % 4 != 0L)); t += 250 }
        assertEquals(0, metrics.scorecard().pauses)
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

    @Test
    fun `stopping after pushing without a rate still waits two seconds of stillness`() {
        val policy = CoachPolicy()
        run(policy, 0, 2_250) { paused(it).copy(moving = true) }
        val early = run(policy, 2_250, 4_000) { paused(it) }
        assertTrue("spoke after under 2 s of stillness: $early", early.isEmpty())
        val later = run(policy, 4_000, 5_500) { paused(it) }
        assertEquals(Phrase.KEEP_GOING, later.single().second)
    }

    @Test
    fun `a short hesitation followed by pushing does not say keep going while pushing`() {
        val policy = CoachPolicy()
        run(policy, 0, 1_250) { paused(it) }
        val spoken = run(policy, 1_250, 2_500) { paused(it).copy(moving = true) }
        assertTrue("spoke keep going while pushing: $spoken", spoken.isEmpty())
    }

    @Test
    fun `keep going plays again when the rescuer stops after pushing with bent elbows`() {
        val policy = CoachPolicy()
        assertEquals(listOf(2_000L to Phrase.KEEP_GOING), run(policy, 0, 2_250) { paused(it) })
        assertEquals(
            listOf(8_000L to Phrase.ELBOWS_STRAIGHT),
            run(policy, 2_250, 8_250) { pushing(it, PostureIssue.ELBOWS_BENT) },
        )
        val secondPause = run(policy, 8_250, 18_000) { paused(it) }
        assertTrue("expected KEEP_GOING on the second pause, got $secondPause", secondPause.any { it.second == Phrase.KEEP_GOING })
    }

    @Test
    fun `keep going plays on a second pause after resuming good compressions`() {
        val policy = CoachPolicy()
        assertEquals(listOf(2_000L to Phrase.KEEP_GOING), run(policy, 0, 2_250) { paused(it) })
        run(policy, 2_250, 10_000) { t -> pushing(t, null, TrackingState.BEATING) }
        val secondPause = run(policy, 10_000, 16_000) { paused(it) }
        assertEquals(listOf(Phrase.KEEP_GOING), secondPause.map { it.second })
    }

    @Test
    fun `elbows straight is coached again after fixing posture and bending again`() {
        val policy = CoachPolicy()
        assertEquals(
            listOf(2_000L to Phrase.ELBOWS_STRAIGHT),
            run(policy, 0, 2_250) { pushing(it, PostureIssue.ELBOWS_BENT) },
        )
        run(policy, 2_250, 10_000) { pushing(it, null) }
        val relapsed = run(policy, 10_000, 20_000) { pushing(it, PostureIssue.ELBOWS_BENT) }
        assertEquals(listOf(Phrase.ELBOWS_STRAIGHT), relapsed.map { it.second })
    }

    @Test
    fun `brief rate wobble does not starve a continuous elbow correction`() {
        val policy = CoachPolicy()
        val spoken = run(policy, 0, 20_000) { t ->
            val rate = if (t % 2_500 < 1_000) 95 else 105
            pushing(t, PostureIssue.ELBOWS_BENT, TrackingState.BEATING).copy(ratePerMinute = rate)
        }
        assertTrue("expected ELBOWS_STRAIGHT during 20 s of bent elbows, got $spoken", spoken.any { it.second == Phrase.ELBOWS_STRAIGHT })
    }

    @Test
    fun `alternating beating and pushing without a rate does not starve a continuous elbow correction`() {
        val policy = CoachPolicy()
        val spoken = run(policy, 0, 20_000) { t ->
            if (t % 4_000 < 2_500) pushing(t, PostureIssue.ELBOWS_BENT, TrackingState.BEATING).copy(ratePerMinute = 90)
            else pushing(t, PostureIssue.ELBOWS_BENT)
        }
        assertTrue("expected ELBOWS_STRAIGHT, got $spoken", spoken.any { it.second == Phrase.ELBOWS_STRAIGHT })
    }

    @Test
    fun `brief lost frame after pause is already confirmed does not drop pending keep going`() {
        val policy = CoachPolicy()
        assertEquals(listOf(3_000L to Phrase.PUSH_FASTER), run(policy, 0, 3_250) { beating(it, 90) })
        run(policy, 4_000, 8_500) { paused(it) }
        policy.update(lost(8_500))
        val spoken = run(policy, 8_750, 10_500) { paused(it) }
        assertEquals(listOf(Phrase.KEEP_GOING), spoken.map { it.second })
    }
}
