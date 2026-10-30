package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.CameraUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PractiseSessionTest {

    private class FakeAudio : SessionAudio {
        val played = ArrayList<String>()
        var metronome = false
        override fun play(key: String) { played.add(key) }
        override fun startMetronome() { metronome = true }
        override fun stopMetronome() { metronome = false }
    }

    private val goodArm = ArmLandmarks(
        0.50f, 0.30f, 0.9f, 0.52f, 0.45f, 0.9f, 0.54f, 0.60f, 0.9f, 0.30f, 0.55f, 0.9f,
    )

    private var clock = 1_000L
    private val audio = FakeAudio()
    private val session = PractiseSession(audio, totalSeconds = 30)

    private fun frame(rate: Double?): PoseFrame {
        val sample = rate?.let {
            val wave = sin(2 * PI * clock / 1000.0 * it / 60)
            PoseSample(clock, 0.2 + 0.08 * wave, 170.0, 0.9f)
        }
        return PoseFrame(clock, sample, if (rate != null) goodArm else null, 30)
    }

    /** Advances time in 40 ms frames, ticking every 240 ms. */
    private fun run(ms: Long, rate: Double?) {
        val end = clock + ms
        var n = 0
        while (clock < end) {
            session.onFrame(frame(rate))
            if (n++ % 6 == 0) session.tick(clock)
            clock += 40
        }
    }

    private fun startSession() {
        run(1_500, 110.0)
        assertTrue((session.ui.value as CameraUiState.Framing).ready)
        session.start(clock)
        run(3_200, 110.0)
    }

    @Test
    fun `start is refused until framing is ready`() {
        session.start(clock)
        assertTrue(session.ui.value is CameraUiState.Framing)
        assertTrue(audio.played.isEmpty())
    }

    @Test
    fun `countdown leads to a live session with the metronome`() {
        run(1_500, 110.0)
        session.start(clock)
        assertEquals(listOf("countdown"), audio.played)
        assertTrue(session.ui.value is CameraUiState.Countdown)
        assertFalse(audio.metronome)
        run(3_200, 110.0)
        assertTrue(session.ui.value is CameraUiState.Live)
        assertTrue(audio.metronome)
    }

    @Test
    fun `live session measures the rate and ends with a real scorecard`() {
        startSession()
        run(8_000, 110.0)
        val live = session.ui.value as CameraUiState.Live
        assertEquals(110.0, live.ratePerMinute!!.toDouble(), 3.0)
        assertEquals(true, live.inRange)
        run(25_000, 110.0)
        val card = session.scorecard.value
        assertNotNull(card)
        assertEquals("session_done", audio.played.last())
        assertFalse(audio.metronome)
        assertTrue(card!!.secondsInRange > 20)
    }

    @Test
    fun `no person means no number and no made-up score`() {
        startSession()
        run(5_000, 110.0)
        run(3_000, null)
        val live = session.ui.value as CameraUiState.Live
        assertEquals(TrackingState.LOST, live.tracking)
        assertNull(live.ratePerMinute)
        assertTrue(audio.metronome)
    }

    @Test
    fun `pausing stops the clock and the metronome`() {
        startSession()
        run(2_000, 110.0)
        session.togglePause()
        session.tick(clock)
        val before = (session.ui.value as CameraUiState.Live).secondsLeft
        assertFalse(audio.metronome)
        run(10_000, 110.0)
        assertEquals(before, (session.ui.value as CameraUiState.Live).secondsLeft)
        session.togglePause()
        assertTrue(audio.metronome)
    }

    @Test
    fun `stopping early gives a scorecard without the congratulations`() {
        startSession()
        run(5_000, 110.0)
        session.stop()
        assertNotNull(session.scorecard.value)
        assertFalse(audio.played.contains("session_done"))
        assertFalse(audio.metronome)
    }
}
