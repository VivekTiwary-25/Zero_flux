package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.CameraUiState
import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

class PauseVoiceReplayTest {
    private data class Played(val timeMs: Long, val key: String)

    private class Replay {
        private var clockMs = 1_000L
        val played = ArrayList<Played>()
        private val audio = object : SessionAudio {
            override fun play(key: String) { played.add(Played(clockMs, key)) }
            override fun startMetronome() = Unit
            override fun stopMetronome() = Unit
        }
        val session = PractiseSession(audio)
        var liveStartMs = 0L
            private set
        private var nextTickMs = 0L

        fun start(framingFrame: PoseFrame) {
            val deadlineMs = clockMs + 5_000L
            while (!(session.ui.value as CameraUiState.Framing).ready && clockMs < deadlineMs) {
                session.onFrame(at(framingFrame, clockMs))
                if (!(session.ui.value as CameraUiState.Framing).ready) clockMs += 40L
            }
            assertTrue("Framing must be ready before start", (session.ui.value as CameraUiState.Framing).ready)
            session.start(clockMs)
            assertTrue(session.ui.value is CameraUiState.Countdown)
            val countdownEndMs = clockMs + 3_000L
            while (clockMs < countdownEndMs) {
                clockMs += TICK_MS
                session.tick(clockMs)
            }
            assertTrue("Countdown must finish before recording frames", session.ui.value is CameraUiState.Live)
            liveStartMs = clockMs
            nextTickMs = liveStartMs + TICK_MS
        }

        fun frame(frame: PoseFrame) {
            // Tick through gaps before delivering a future camera result.
            while (nextTickMs < frame.timeMs) tick()
            require(frame.timeMs >= clockMs) { "Replay clock moved backwards" }
            clockMs = frame.timeMs
            session.onFrame(frame)
            if (nextTickMs == frame.timeMs) tick()
        }

        fun tickThrough(recordingTimeMs: Long) {
            while (nextTickMs <= liveStartMs + recordingTimeMs) tick()
        }

        private fun tick() {
            require(nextTickMs >= clockMs) { "Tick preceded the latest frame" }
            clockMs = nextTickMs
            session.tick(clockMs)
            nextTickMs += TICK_MS
        }

        fun keepGoingTimes() = played.filter { it.key == "keep_going" }.map { it.timeMs - liveStartMs }

        fun timeline(): String = played.joinToString { "${it.timeMs - liveStartMs}ms ${it.key}" }
    }

    @Test
    fun `recording 14 plays keep going during the real pause but not while pushing`() {
        val frames = recordedFrames()
        val replay = Replay()
        replay.start(frames.first())
        val shiftMs = replay.liveStartMs - frames.first().timeMs
        for (frame in frames) replay.frame(at(frame, frame.timeMs + shiftMs))
        val timeline = replay.timeline()
        println("PractiseSession recording 14 play(): $timeline")
        val pauses = replay.keepGoingTimes()
        assertTrue("Expected keep_going at 26.5-30.5 s: $timeline", pauses.any { it in 26_500L..30_500L })
        assertTrue("No keep_going during pushing at 3-24 s: $timeline", pauses.none { it in 3_000L..24_000L })
    }

    @Test
    fun `still hands prompt once and a new pause prompts again after resuming`() {
        val replay = Replay()
        replay.start(syntheticFrame(0L, pushing = true))

        fun segment(fromMs: Long, toMs: Long, pushing: Boolean) {
            for (relativeMs in fromMs until toMs step 40L) {
                replay.frame(at(syntheticFrame(relativeMs, pushing), replay.liveStartMs + relativeMs))
            }
            replay.tickThrough(toMs)
        }

        segment(0L, 12_000L, pushing = true)
        val firstPushing = replay.session.ui.value as CameraUiState.Live
        segment(12_000L, 16_000L, pushing = false)
        val firstPause = replay.keepGoingTimes()

        segment(16_000L, 28_000L, pushing = true)
        val afterResume = replay.keepGoingTimes()
        val resumedPushing = replay.session.ui.value as CameraUiState.Live
        segment(28_000L, 32_000L, pushing = false)
        println("PractiseSession synthetic two pauses play(): ${replay.timeline()}")
        val bothPauses = replay.keepGoingTimes()
        val timeline = replay.timeline()
        assertEquals("Synthetic input must establish a beat: $timeline", TrackingState.BEATING, firstPushing.tracking)
        assertEquals("Resuming must establish a new beat: $timeline", TrackingState.BEATING, resumedPushing.tracking)
        assertTrue("No pause cue during steady pushing: $timeline", firstPause.none { it < 12_000L })
        assertEquals("Exactly one cue in four seconds of stillness: $timeline", 1, firstPause.size)
        assertEquals("Resuming must not itself add a pause cue: $timeline", firstPause, afterResume)
        assertEquals("A second pause must get its own cue: ${replay.timeline()}", 2, bothPauses.size)
        assertTrue("First cue within four seconds of stillness: $timeline", firstPause.single() - 12_000L in 1_750L..3_750L)
        assertTrue("Second cue within four seconds of stillness: $timeline", bothPauses.last() - 28_000L in 1_750L..3_750L)
    }

    private fun recordedFrames(): List<PoseFrame> =
        File(TestAssets.projectDir, "tools/testdata/real-cph2467-14.csv").bufferedReader().use { reader ->
            val header = reader.readLine().removePrefix("\uFEFF").split(',')
            fun col(name: String) = header.indexOf(name).also { require(it >= 0) { "Missing CSV column $name" } }
            reader.lineSequence().filter { it.isNotBlank() }.map { line ->
                val cells = line.split(',')
                fun value(name: String) = cells[col(name)].trim()
                fun f(name: String) = value(name).toFloat()
                val timeMs = value("t_ms").toLong()
                if (value("wrist_signal").isEmpty() || value("shoulder_x").isEmpty()) {
                    PoseFrame(timeMs, null, null, value("latency_ms").toLong(), ASPECT)
                } else {
                    val arm = ArmLandmarks(
                        f("shoulder_x"), f("shoulder_y"), f("shoulder_vis"),
                        f("elbow_x"), f("elbow_y"), f("elbow_vis"),
                        f("wrist_x"), f("wrist_y"), f("wrist_vis"),
                        f("hip_x"), f("hip_y"), f("hip_vis"),
                    )
                    val sample = PoseSample(
                        timeMs = timeMs,
                        wristSignal = value("wrist_signal").toDouble(),
                        elbowDegrees = PoseMath.elbowDegrees(arm, ASPECT),
                        minVisibility = f("min_vis"),
                        elbowVisibility = arm.elbowVis,
                        sideOn = value("side_on") != "0",
                    )
                    PoseFrame(timeMs, sample, arm, value("latency_ms").toLong(), ASPECT)
                }
            }.toList()
        }

    private companion object {
        const val TICK_MS = 250L
        const val ASPECT = 4f / 3f
        val ARM = ArmLandmarks(
            0.50f, 0.30f, 0.9f, 0.50f, 0.45f, 0.9f,
            0.50f, 0.60f, 0.9f, 0.30f, 0.55f, 0.9f,
        )

        fun at(frame: PoseFrame, timeMs: Long) = frame.copy(
            timeMs = timeMs,
            sample = frame.sample?.copy(timeMs = timeMs),
        )

        fun syntheticFrame(timeMs: Long, pushing: Boolean): PoseFrame {
            // Both stops are at a zero crossing, so still hands introduce no signal jump.
            val signal = if (pushing) 0.2 + 0.08 * sin(2 * PI * timeMs / 1_000.0 * 120.0 / 60.0) else 0.2
            return PoseFrame(timeMs, PoseSample(timeMs, signal, 180.0, 0.9f), ARM, 0L, ASPECT)
        }
    }
}
