package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.audio.WavClip
import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmergencyTalkTest {
    private fun pushing(t: Long, rate: Int? = 90, bent: Boolean = false) =
        LiveReading(t, if (rate == null) TrackingState.PAUSED else TrackingState.BEATING,
            rate, if (bent) 120.0 else 170.0, bent, moving = true)

    @Test fun `rate words need half a second but posture words still need a second`() {
        for ((rate, word) in listOf(90 to EmergencyWord.FASTER, 130 to EmergencyWord.SLOWER)) {
            val coach = EmergencyCoach(0, rateHoldMs = 500)
            coach.update(pushing(0, rate))
            coach.update(pushing(250, rate))
            assertEquals(EmergencyWord.KEEP_PUSHING, coach.word)
            coach.update(pushing(500, rate))
            assertEquals(word, coach.word)
        }
        val coach = EmergencyCoach(0)
        coach.update(pushing(0, 110, bent = true))
        coach.update(pushing(500, 110, bent = true))
        assertEquals(EmergencyWord.KEEP_PUSHING, coach.word)
        coach.update(pushing(1_000, 110, bent = true))
        assertEquals(EmergencyWord.STRAIGHTEN, coach.word)
    }

    @Test fun `busy playback observes rate steadiness without consuming the correction`() {
        val coach = EmergencyCoach(0)
        for (t in 0L..1_750L step 250L) assertNull(coach.nextCorrection(pushing(t), voiceIdle = false))
        assertEquals(Phrase.PUSH_FASTER, coach.nextCorrection(pushing(2_000)))
    }

    @Test fun `a correction cleared during playback is never spoken later`() {
        val coach = EmergencyCoach(0)
        for (t in 0L..2_000L step 250L) assertNull(coach.nextCorrection(pushing(t), voiceIdle = false))
        for (t in 2_250L..4_000L step 250L) coach.nextCorrection(pushing(t, 110), voiceIdle = false)
        assertEquals(Phrase.GOOD, coach.nextCorrection(pushing(4_250, 110)))
    }

    @Test fun `relock guard is inside the one and a half second rate hold`() {
        val coach = EmergencyCoach(0)
        coach.nextCorrection(pushing(1_000, null))
        for (t in 1_250L..2_500L step 250L) assertNull(coach.nextCorrection(pushing(t)))
        assertEquals(Phrase.PUSH_FASTER, coach.nextCorrection(pushing(2_750)))
    }

    @Test fun `fixed correction gets one good after cooldown then praise every ten seconds`() {
        val coach = EmergencyCoach(0)
        for (t in 0L until 1_500L step 250L) assertNull(coach.nextCorrection(pushing(t)))
        assertEquals(Phrase.PUSH_FASTER, coach.nextCorrection(pushing(1_500)))
        for (t in 1_750L until 5_500L step 250L) assertNull(coach.nextCorrection(pushing(t, 110)))
        assertEquals(Phrase.GOOD, coach.nextCorrection(pushing(5_500, 110)))
        for (t in 5_750L until 15_500L step 250L) assertNull(coach.nextCorrection(pushing(t, 110)))
        assertEquals(Phrase.GOOD, coach.nextCorrection(pushing(15_500, 110)))
    }

    @Test fun `fillers alternate eight seconds after actual English and Kannada clip ends`() {
        for (language in listOf("en", "kn")) {
            fun duration(key: String): Long {
                val clip = WavClip.parse(File(TestAssets.assetsDir, "clips/$language/$key.wav").readBytes())
                return (clip.samples.size * 1_000L + clip.sampleRate - 1) / clip.sampleRate
            }
            val coach = EmergencyCoach(0)
            var playingUntil = listOf("em_call", "em_hands", "em_push").sumOf { duration(it) }
            var lastEnd: Long? = null
            val keys = ArrayList<String>()
            for (t in 0L..75_000L step 250L) {
                val idle = t >= playingUntil
                if (idle) lastEnd = playingUntil
                val key = coach.nextClip(pushing(t, null), idle, lastEnd) ?: continue
                assertTrue("$language: filler while busy at $t", idle)
                val gap = t - requireNotNull(lastEnd)
                assertTrue("$language: silence was ${gap}ms", gap in 8_000L..8_249L)
                keys.add(key)
                println("$language filler $key start=$t previous_end=$lastEnd silence=$gap duration=${duration(key)}")
                playingUntil = t + duration(key)
            }
            assertEquals(listOf("keep_going", "em_push", "keep_going", "em_push"),
                keys.take(4))
        }
    }

    @Test fun `filler stays silent during steps reminders tracking loss and still hands`() {
        val coach = EmergencyCoach(0)
        assertNull(coach.nextClip(pushing(20_000, null), false, 0))
        assertNull(coach.nextClip(pushing(20_250, null), true, null))
        assertNull(coach.nextClip(pushing(20_500, null).copy(moving = false), true, 0))
        assertNull(coach.nextClip(LiveReading(20_750, TrackingState.LOST, null, null, null), true, 0))
    }

    @Test fun `keep going filler does not acknowledge the next real pause`() {
        val coach = EmergencyCoach(0)
        for (t in 0L until 8_000L step 250L) assertNull(coach.nextClip(pushing(t, null), true, 0))
        assertEquals("keep_going", coach.nextClip(pushing(8_000, null), true, 0))
        var recovery: String? = null
        for (t in 8_250L..14_000L step 250L) {
            val key = coach.nextClip(pushing(t, null).copy(moving = false), true, 9_500)
            if (key != null) recovery = key
        }
        assertEquals("keep_going", recovery)
    }

    @Test fun `all four corrected issues earn good only after the remaining issues clear`() {
        val cases = listOf<(Long) -> LiveReading>(
            { pushing(it, 90) },
            { pushing(it, 130) },
            { pushing(it, 110, bent = true) },
            { pushing(it, 110).copy(posture = PostureReading(false, PostureIssue.SHOULDERS_NOT_OVER_HANDS, null, null)) },
        )
        for (issue in cases) {
            val coach = EmergencyCoach(0)
            var correction: Phrase? = null
            for (t in 0L..2_000L step 250L) correction = coach.nextCorrection(issue(t)) ?: correction
            org.junit.Assert.assertTrue(correction != null && correction != Phrase.GOOD)
            var praise: Phrase? = null
            for (t in 2_250L..6_000L step 250L) praise = coach.nextCorrection(pushing(t, 110)) ?: praise
            assertEquals(Phrase.GOOD, praise)
        }
    }
}
