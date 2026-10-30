package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostureWiringTest {
    private fun reading(t: Long, issue: PostureIssue?, rate: Int = 110, elbowsBent: Boolean? = null) = LiveReading(
        t, TrackingState.BEATING, rate, 150.0, elbowsBent, true,
        PostureReading(issue == null, issue, 150.0, 0.1),
    )

    @Test fun coachSpeaksElbowsOnlyForElbowsBentPosture() {
        val bent = CoachPolicy()
        var said: Phrase? = null
        for (t in 0..8000L step 250) bent.update(reading(t, PostureIssue.ELBOWS_BENT))?.let { said = it }
        assertEquals(Phrase.ELBOWS_STRAIGHT, said)
        for (issue in listOf(PostureIssue.SHOULDERS_NOT_OVER_HANDS, PostureIssue.NOT_SIDE_ON)) {
            val c = CoachPolicy()
            val phrases = (0..12000L step 250).mapNotNull { c.update(reading(it, issue)) }
            assertTrue("$issue stays silent in voice", Phrase.ELBOWS_STRAIGHT !in phrases)
        }
    }

    @Test fun postureWinsOverLegacyElbowFlag() {
        val c = CoachPolicy()
        val phrases = (0..8000L step 250).mapNotNull { c.update(reading(it, null, elbowsBent = true)) }
        assertTrue(Phrase.ELBOWS_STRAIGHT !in phrases)
    }

    @Test fun emergencyWordsFollowRateThenPosture() {
        val e = EmergencyCoach(0)
        for (t in 0..3000L step 250) e.update(reading(t, PostureIssue.ELBOWS_BENT))
        assertEquals(EmergencyWord.STRAIGHTEN_ARMS, e.word)
        for (t in 3250..6500L step 250) e.update(reading(t, PostureIssue.SHOULDERS_NOT_OVER_HANDS))
        assertEquals(EmergencyWord.SHOULDERS_OVER_HANDS, e.word)
        for (t in 6750..9000L step 250) e.update(reading(t, PostureIssue.ELBOWS_BENT, rate = 80))
        assertEquals(EmergencyWord.FASTER, e.word)
        assertEquals("posture_straighten_arms", EmergencyWord.STRAIGHTEN_ARMS.key)
    }

    @Test fun notSideOnIsAHintNotAWord() {
        val e = EmergencyCoach(0)
        for (t in 0..3000L step 250) e.update(reading(t, PostureIssue.NOT_SIDE_ON))
        assertEquals(EmergencyWord.GOOD, e.word)
        assertTrue(e.turnPhoneHint)
        e.update(reading(3250, null))
        assertTrue(!e.turnPhoneHint)
    }

    @Test fun legacyElbowFlagStillGivesOldWord() {
        val e = EmergencyCoach(0)
        for (t in 0..3000L step 250) {
            e.update(LiveReading(t, TrackingState.BEATING, 110, 80.0, true, true))
        }
        assertEquals(EmergencyWord.STRAIGHTEN, e.word)
    }

    @Test fun oldAddFrameStillWorksAndPostureIsNull() {
        val a = LiveAnalyzer()
        for (i in 0..60) a.addFrame(PoseSample(i * 33L, 0.5, 170.0, 0.95f))
        assertNull(a.read(60 * 33L).posture)
    }
}
