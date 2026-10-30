package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyCoachTest {
    private fun beating(t: Long, rate: Int, bent: Boolean = false) =
        LiveReading(t, TrackingState.BEATING, rate, if (bent) 120.0 else 170.0, bent)

    private fun lost(t: Long) = LiveReading(t, TrackingState.LOST, null, null, null)
    private fun paused(t: Long) = LiveReading(t, TrackingState.PAUSED, null, 170.0, false)

    private fun EmergencyCoach.feed(from: Long, to: Long, reading: (Long) -> LiveReading) {
        var t = from
        while (t <= to) {
            update(reading(t))
            t += 250
        }
    }

    @Test fun `starts on keep pushing and asks for setup`() {
        val coach = EmergencyCoach(0)
        assertEquals(EmergencyWord.KEEP_PUSHING, coach.word)
        assertTrue(coach.needsSetup)
    }

    @Test fun `the word follows the measured rate`() {
        for ((rate, word) in listOf(90 to EmergencyWord.FASTER, 110 to EmergencyWord.GOOD, 130 to EmergencyWord.SLOWER)) {
            val coach = EmergencyCoach(0)
            coach.feed(0, 1_500) { beating(it, rate) }
            assertEquals("at $rate", word, coach.word)
        }
    }

    @Test fun `range edges count as good`() {
        for (rate in listOf(LiveReading.TARGET_MIN, LiveReading.TARGET_MAX)) {
            val coach = EmergencyCoach(0)
            coach.feed(0, 1_500) { beating(it, rate) }
            assertEquals(EmergencyWord.GOOD, coach.word)
        }
    }

    @Test fun `bent arms show only when the rate is fine`() {
        val fine = EmergencyCoach(0)
        fine.feed(0, 1_500) { beating(it, 110, bent = true) }
        assertEquals(EmergencyWord.STRAIGHTEN, fine.word)
        val slow = EmergencyCoach(0)
        slow.feed(0, 1_500) { beating(it, 90, bent = true) }
        assertEquals(EmergencyWord.FASTER, slow.word)
    }

    @Test fun `a word that flickers does not show`() {
        val coach = EmergencyCoach(0)
        coach.feed(0, 1_500) { beating(it, 110) }
        coach.update(beating(1_750, 90))
        coach.update(beating(2_000, 110))
        assertEquals(EmergencyWord.GOOD, coach.word)
    }

    @Test fun `losing the arms shows keep pushing at once`() {
        val coach = EmergencyCoach(0)
        coach.feed(0, 1_500) { beating(it, 110) }
        coach.update(lost(1_750))
        assertEquals(EmergencyWord.KEEP_PUSHING, coach.word)
    }

    @Test fun `no beat never gives an instruction`() {
        val coach = EmergencyCoach(0)
        coach.feed(0, 5_000) { paused(it) }
        assertEquals(EmergencyWord.KEEP_PUSHING, coach.word)
        assertFalse(coach.needsSetup)
    }

    @Test fun `summary with no camera has time and dashes only`() {
        val coach = EmergencyCoach(1_000)
        coach.feed(1_000, 91_000) { lost(it) }
        val summary = coach.summary(91_000)
        assertEquals(90, summary.totalSeconds)
        assertEquals(0, summary.measuredSeconds)
        assertNull(summary.averageRate)
        assertNull(summary.pauses)
    }

    @Test fun `summary reports only what was measured`() {
        val coach = EmergencyCoach(0)
        coach.feed(0, 10_000) { lost(it) }
        coach.feed(10_250, 40_000) { beating(it, 110) }
        coach.feed(40_250, 44_000) { paused(it) }
        coach.feed(44_250, 60_000) { beating(it, 110) }
        val summary = coach.summary(60_000)
        assertEquals(60, summary.totalSeconds)
        assertEquals(110, summary.averageRate)
        assertEquals(1, summary.pauses)
        assertTrue(summary.measuredSeconds in 44..47)
    }

    @Test fun `time going backwards cannot make a negative total`() {
        assertEquals(0, EmergencyCoach(5_000).summary(1_000).totalSeconds)
    }

    @Test fun `spoken corrections come from the same rules as Practise`() {
        val coach = EmergencyCoach(0)
        var heard: Phrase? = null
        var t = 0L
        while (t <= 4_000 && heard == null) {
            heard = coach.nextCorrection(beating(t, 90))
            t += 250
        }
        assertEquals(Phrase.PUSH_FASTER, heard)
    }
}
