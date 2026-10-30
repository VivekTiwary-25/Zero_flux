package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.CameraUiState
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Movement, false pauses, the Emergency word and the real recordings in tools/testdata. */
class MovingAndReplayTest {

    // ---- helpers -------------------------------------------------------------------------

    private fun reading(
        t: Long,
        tracking: TrackingState,
        rate: Int? = null,
        moving: Boolean = false,
        bent: Boolean? = false,
    ) = LiveReading(t, tracking, rate, if (tracking == TrackingState.LOST) null else 170.0, bent, moving)

    /** Pushes at [bpm] with [amp] torso lengths of travel, 25 frames a second, from [fromMs] to [toMs]. */
    private fun LiveAnalyzer.push(fromMs: Long, toMs: Long, bpm: Double = 110.0, amp: Double = 0.15, still: Boolean = false) {
        var t = fromMs
        while (t < toMs) {
            val wave = if (still) 0.0 else sin(2 * PI * t / 1000.0 * bpm / 60)
            addFrame(PoseSample(t, 0.3 + amp * wave, 170.0, 0.95f))
            t += 40
        }
    }

    private fun movingAt(a: LiveAnalyzer, t: Long) = a.read(t).moving

    // ---- moving ---------------------------------------------------------------------------

    @Test fun `moving turns on within half a second of pushing and off soon after stopping`() {
        val a = LiveAnalyzer()
        a.push(0, 3_000, still = true)
        assertFalse(movingAt(a, 2_960))
        a.push(3_000, 3_500)
        assertTrue("pushing for 0.5 s", movingAt(a, 3_460))
        a.push(3_500, 8_000)
        assertTrue(movingAt(a, 7_960))
        a.push(8_000, 8_800, still = true)
        assertFalse("0.8 s after stopping", movingAt(a, 8_760))
    }

    @Test fun `moving does not flicker on a steady push`() {
        val a = LiveAnalyzer()
        a.push(0, 1_000, still = true)
        var t = 1_000L
        var flips = 0
        var last = false
        while (t < 10_000) {
            a.push(t, t + 40)
            val m = a.read(t).moving
            if (t > 1_600) {
                if (m != last) flips++
            }
            last = m
            t += 40
        }
        assertEquals("settles once and stays", 0, flips)
    }

    @Test fun `slow drift and sensor noise are not movement`() {
        val a = LiveAnalyzer()
        val r = Random(3)
        var t = 0L
        while (t < 6_000) {
            a.addFrame(PoseSample(t, 0.3 + 0.05 * t / 1000.0 + 0.004 * r.nextGaussian(), 170.0, 0.95f))
            assertFalse("at $t", a.read(t).moving)
            t += 40
        }
    }

    // ---- reading a rate sooner and holding it briefly -----------------------------------

    @Test fun `a dropout while still moving keeps the last rate for at most two seconds`() {
        val a = LiveAnalyzer()
        a.push(0, 9_000, amp = 0.15)
        assertEquals(TrackingState.BEATING, a.read(8_960).tracking)
        val before = a.read(8_960).ratePerMinute!!
        // Irregular, still moving, but no beat.
        val r = Random(5)
        // A bare estimator fed the same frames shows when the beat itself is lost.
        val bare = RateEstimator()
        var t0 = 0L
        while (t0 < 9_000) {
            bare.add(t0, 0.3 + 0.15 * sin(2 * PI * t0 / 1000.0 * 110 / 60))
            t0 += 40
        }
        var t = 9_000L
        var beatLostAt = -1L
        val seen = ArrayList<LiveReading>()
        while (t < 16_000) {
            val v = 0.3 + 0.06 * r.nextGaussian()
            a.addFrame(PoseSample(t, v, 170.0, 0.95f))
            bare.add(t, v)
            if (beatLostAt < 0 && bare.read() !is BeatReading.Beat) beatLostAt = t
            seen += a.read(t)
            t += 40
        }
        assertTrue(beatLostAt > 0)
        val held = seen.filter { it.timeMs >= beatLostAt && it.tracking == TrackingState.BEATING }
        assertTrue("some rate was held", held.isNotEmpty())
        assertTrue("held rate stays the measured one", held.all { it.ratePerMinute == before })
        assertTrue("never longer than two seconds", held.last().timeMs - beatLostAt <= LiveAnalyzer.HOLD_RATE_MS + 80)
        assertEquals(TrackingState.PAUSED, seen.last().tracking)
        assertNull(seen.last().ratePerMinute)
    }

    @Test fun `stopping shows no rate once the arms are still`() {
        val a = LiveAnalyzer()
        a.push(0, 9_000)
        a.push(9_000, 12_500, still = true)
        val reading = a.read(12_460)
        assertEquals(TrackingState.PAUSED, reading.tracking)
        assertNull(reading.ratePerMinute)
        assertFalse(reading.moving)
    }

    @Test fun `a steady push gets a rate within the old time`() {
        val a = LiveAnalyzer()
        var first = -1L
        var t = 0L
        while (t < 8_000 && first < 0) {
            a.push(t, t + 40)
            if (a.read(t).ratePerMinute != null) first = t
            t += 40
        }
        assertTrue("first rate at $first", first in 1..3_600)
    }

    // ---- pauses ----------------------------------------------------------------------------

    @Test fun `paused but moving is not a pause in the scorecard`() {
        val m = SessionMetrics()
        for (t in 0..20_000L step 250) m.add(reading(t, TrackingState.PAUSED, moving = true))
        assertEquals(0, m.scorecard().pauses)
        val real = SessionMetrics()
        for (t in 0..20_000L step 250) real.add(reading(t, TrackingState.PAUSED, moving = false))
        assertEquals(1, real.scorecard().pauses)
    }

    @Test fun `moving without a rate does not prompt keep going`() {
        val policy = CoachPolicy()
        val spoken = (0L..20_000L step 250).mapNotNull { policy.update(reading(it, TrackingState.PAUSED, moving = true)) }
        assertTrue(spoken.isEmpty())
        val still = CoachPolicy()
        val heard = (0L..20_000L step 250).mapNotNull { still.update(reading(it, TrackingState.PAUSED, moving = false)) }
        assertEquals(listOf(Phrase.KEEP_GOING), heard)
    }

    @Test fun `unsure elbows are silent`() {
        val policy = CoachPolicy()
        val spoken = (0L..20_000L step 250).mapNotNull {
            policy.update(reading(it, TrackingState.BEATING, rate = 110, bent = null))
        }
        assertFalse(spoken.any { it == Phrase.ELBOWS_STRAIGHT })
        val coach = EmergencyCoach(0)
        for (t in 0L..5_000L step 250) coach.update(reading(t, TrackingState.BEATING, rate = 110, bent = null))
        assertEquals(EmergencyWord.GOOD, coach.word)
    }

    // ---- Practise ---------------------------------------------------------------------------

    private class FakeAudio : SessionAudio {
        override fun play(key: String) {}
        override fun startMetronome() {}
        override fun stopMetronome() {}
    }

    @Test fun `practise shows detecting while moving with no rate yet`() {
        val goodArm = ArmLandmarks(0.50f, 0.30f, 0.9f, 0.52f, 0.45f, 0.9f, 0.54f, 0.60f, 0.9f, 0.30f, 0.55f, 0.9f)
        val session = PractiseSession(FakeAudio(), totalSeconds = 60)
        var clock = 1_000L
        fun run(ms: Long, still: Boolean = false) {
            val end = clock + ms
            var n = 0
            while (clock < end) {
                val wave = if (still) 0.0 else sin(2 * PI * clock / 1000.0 * 110 / 60)
                session.onFrame(PoseFrame(clock, PoseSample(clock, 0.3 + 0.08 * wave, 170.0, 0.9f), goodArm, 30))
                if (n++ % 6 == 0) session.tick(clock)
                clock += 40
            }
        }
        run(1_500, still = true)
        session.start(clock)
        run(3_400, still = true)
        assertFalse((session.ui.value as CameraUiState.Live).detecting)
        run(600)
        val early = session.ui.value as CameraUiState.Live
        assertNull(early.ratePerMinute)
        assertTrue("moving, no rate yet", early.detecting)
        run(8_000)
        val later = session.ui.value as CameraUiState.Live
        assertTrue(later.ratePerMinute != null)
        assertFalse(later.detecting)
        session.togglePause()
        assertFalse((session.ui.value as CameraUiState.Live).detecting)
    }

    // ---- Emergency word ---------------------------------------------------------------------

    @Test fun `a new word shows after one second`() {
        val coach = EmergencyCoach(0)
        for (t in 0L..750L step 250) coach.update(reading(t, TrackingState.BEATING, rate = 90, moving = true))
        assertEquals(EmergencyWord.KEEP_PUSHING, coach.word)
        coach.update(reading(1_000, TrackingState.BEATING, rate = 90, moving = true))
        assertEquals(EmergencyWord.FASTER, coach.word)
    }

    @Test fun `moving with a brief gap in the rate keeps the word for three seconds`() {
        val coach = EmergencyCoach(0)
        for (t in 0L..6_000L step 250) coach.update(reading(t, TrackingState.BEATING, rate = 90, moving = true))
        assertEquals(EmergencyWord.FASTER, coach.word)
        for (t in 6_250L..9_000L step 250) {
            coach.update(reading(t, TrackingState.PAUSED, moving = true))
            assertEquals("at $t", EmergencyWord.FASTER, coach.word)
        }
        coach.update(reading(9_250, TrackingState.PAUSED, moving = true))
        assertEquals(EmergencyWord.KEEP_PUSHING, coach.word)
    }

    @Test fun `not moving drops the word at once`() {
        val coach = EmergencyCoach(0)
        for (t in 0L..6_000L step 250) coach.update(reading(t, TrackingState.BEATING, rate = 90, moving = true))
        coach.update(reading(6_250, TrackingState.PAUSED, moving = false))
        assertEquals(EmergencyWord.KEEP_PUSHING, coach.word)
    }

    @Test fun `a phantom blip does not linger`() {
        val coach = EmergencyCoach(0)
        for (t in 0L..4_000L step 250) coach.update(reading(t, TrackingState.PAUSED, moving = true))
        for (t in 4_250L..6_000L step 250) coach.update(reading(t, TrackingState.BEATING, rate = 70, moving = true))
        coach.update(reading(6_250, TrackingState.PAUSED, moving = true))
        assertEquals(EmergencyWord.KEEP_PUSHING, coach.word)
    }

    @Test fun `summary still needs ten measured seconds`() {
        val coach = EmergencyCoach(0)
        for (t in 0L..8_000L step 250) coach.update(reading(t, TrackingState.BEATING, rate = 110, moving = true))
        assertNull(coach.summary(8_000).averageRate)
        for (t in 8_250L..14_000L step 250) coach.update(reading(t, TrackingState.BEATING, rate = 110, moving = true))
        assertEquals(110, coach.summary(14_000).averageRate)
    }

    @Test fun `setup hint returns after three seconds without arms and goes when they are seen`() {
        val coach = EmergencyCoach(0)
        for (t in 0L..2_000L step 250) coach.update(reading(t, TrackingState.PAUSED))
        assertFalse(coach.needsSetup)
        for (t in 2_250L..4_750L step 250) coach.update(reading(t, TrackingState.LOST))
        assertFalse("under three seconds lost", coach.needsSetup)
        coach.update(reading(5_500, TrackingState.LOST))
        assertTrue(coach.needsSetup)
        coach.update(reading(5_750, TrackingState.WARMING))
        assertFalse(coach.needsSetup)
    }

    // ---- real recordings -----------------------------------------------------------------------

    private class Replay(
        val states: List<LiveReading>,
        val words: List<EmergencyWord>,
        val pauses: Int,
    )

    private fun dataDir(): File = listOf("../tools/testdata", "tools/testdata", "cpr-coach/tools/testdata")
        .map { File(it) }.first { it.isDirectory }

    private fun replay(name: String): Replay {
        val rows = File(dataDir(), name).readLines().drop(1).map { it.split(",") }
        val analyzer = LiveAnalyzer()
        val metrics = SessionMetrics()
        val coach = EmergencyCoach(rows.first()[0].toLong())
        val readings = ArrayList<LiveReading>()
        val words = ArrayList<EmergencyWord>()
        var next = rows.first()[0].toLong()
        for (r in rows) {
            val t = r[0].toLong()
            val wrist = r.getOrNull(1)?.toDoubleOrNull()
            if (wrist != null) analyzer.addFrame(PoseSample(t, wrist, r[2].toDouble(), r[3].toFloat()))
            while (t >= next) {
                val reading = analyzer.read(next)
                readings += reading
                metrics.add(reading)
                coach.update(reading)
                words += coach.word
                next += 250
            }
        }
        return Replay(readings, words, metrics.scorecard().pauses)
    }

    private fun Replay.share(test: (LiveReading) -> Boolean) = states.count(test).toDouble() / states.size

    @Test fun `nobody pushing gives no rate, no word and mostly no movement`() {
        val r = replay("negative-cph2467-nobody.csv")
        assertTrue(r.states.none { it.ratePerMinute != null })
        assertTrue(r.words.all { it == EmergencyWord.KEEP_PUSHING })
        assertTrue("moving ${r.share { it.moving }}", r.share { it.moving } < 0.25)
        assertEquals(0, r.pauses)
    }

    @Test fun `real pushing gets a rate near the metronome most of the time without false pauses`() {
        val usable = (4..9).map { replay("real-cph2467-$it.csv") }
        for ((i, r) in usable.withIndex()) {
            val rates = r.states.mapNotNull { it.ratePerMinute }
            assertTrue("file ${i + 4}: rates $rates", rates.all { it in 84..130 })
            assertTrue("file ${i + 4}: pauses ${r.pauses}", r.pauses <= 1)
        }
        // Floors set below what the code reaches today, so a slip back to mostly-PAUSED fails.
        assertTrue(usable[1].share { it.tracking == TrackingState.BEATING } >= 0.5)  // file 5
        assertTrue(usable[2].share { it.tracking == TrackingState.BEATING } >= 0.45) // file 6
        assertTrue(usable[3].share { it.tracking == TrackingState.BEATING } >= 0.4)  // file 7
        assertTrue(usable[5].share { it.tracking == TrackingState.BEATING } >= 0.4)  // file 9
        // While a rate shows, the rescuer is moving.
        for (r in usable) assertTrue(r.states.filter { it.ratePerMinute != null }.count { !it.moving } <= r.states.size / 20)
        // The word is up for most of the time in the best recording.
        assertTrue(usable[1].words.count { it != EmergencyWord.KEEP_PUSHING } > usable[1].words.size / 2)
    }

    @Test fun `the older low frame rate recording gives no confident rate`() {
        val r = replay("real-rmx3392-1.csv")
        assertTrue(r.states.count { it.ratePerMinute != null } <= 2)
    }
}
