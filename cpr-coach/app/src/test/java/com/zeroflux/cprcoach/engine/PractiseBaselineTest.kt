// Locks what Practise does on recordings 10-16 to build ee682a1: every 250 ms read, every spoken line
// with its time, and the scorecard. tools/check_practise.ps1 runs this after every merge.
//
// Regenerate tools/testdata/practise_baseline ONLY from ee682a1's app code (PowerShell, from cpr-coach):
//   $env:CPR_WRITE_BASELINE="1"
//   ./gradlew.bat testDebugUnitTest --tests '*PractiseBaselineTest*' --rerun-tasks
package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.CameraUiState
import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class PractiseBaselineTest(private val fileName: String) {

    @Test
    fun `practise replay matches ee682a1 byte for byte`() {
        val file = File(TestAssets.projectDir, "tools/testdata/$fileName")
        val first = replay(file)
        assertEquals("replay is not deterministic", first, replay(file))

        val baseline = File(TestAssets.projectDir, "tools/testdata/practise_baseline/${file.nameWithoutExtension}.txt")
        val raw = System.getenv("CPR_WRITE_BASELINE") ?: System.getProperty("CPR_WRITE_BASELINE")
        if (raw == "1") {
            requireNotNull(baseline.parentFile).mkdirs()
            baseline.writeBytes(first.toByteArray(Charsets.UTF_8))
        }
        assertTrue("Missing baseline $baseline", baseline.isFile)
        val expected = baseline.readBytes().toString(Charsets.UTF_8)
        if (expected == first) return
        val a = expected.lines()
        val b = first.lines()
        val line = (0 until minOf(a.size, b.size)).firstOrNull { a[it] != b[it] } ?: minOf(a.size, b.size)
        fail(
            "$fileName: Practise changed at line ${line + 1}\n" +
                "was: ${a.getOrNull(line)}\nnow: ${b.getOrNull(line)}",
        )
    }

    private fun replay(file: File): String {
        val frames = readFrames(file)
        var clockMs = 1_000L
        val output = StringBuilder()
        var liveStartMs = 0L
        val audio = object : SessionAudio {
            override fun play(key: String) { output.append("say ").append(clockMs - liveStartMs).append(' ').append(key).append('\n') }
            override fun startMetronome() { output.append("metronome on ").append(clockMs - liveStartMs).append('\n') }
            override fun stopMetronome() { output.append("metronome off ").append(clockMs - liveStartMs).append('\n') }
            override fun stopVoice() { output.append("stop voice ").append(clockMs - liveStartMs).append('\n') }
        }
        val session = PractiseSession(audio)
        session.onEngineError = { throw AssertionError("Analysis failed at $clockMs ms", it) }

        // Setup is outside the recording, as in TraceTimelineTest.
        val setupArm = ArmLandmarks(
            0.50f, 0.30f, 0.9f, 0.50f, 0.45f, 0.9f,
            0.50f, 0.60f, 0.9f, 0.30f, 0.55f, 0.9f,
        )
        while (!(session.ui.value as CameraUiState.Framing).ready) {
            check(clockMs < 10_000L) { "Framing never became ready" }
            session.onFrame(PoseFrame(clockMs, null, setupArm, 0L, ASPECT))
            if (!(session.ui.value as CameraUiState.Framing).ready) clockMs += 40L
        }
        session.start(clockMs)
        while (session.ui.value is CameraUiState.Countdown) {
            clockMs += TICK_MS
            session.tick(clockMs)
        }
        liveStartMs = clockMs
        val shiftMs = liveStartMs - frames.first().timeMs
        val lastTickMs = liveStartMs + frames.last().timeMs - frames.first().timeMs
        var nextFrame = 0
        var tickMs = liveStartMs
        while (tickMs <= lastTickMs && session.scorecard.value == null) {
            while (nextFrame < frames.size && frames[nextFrame].timeMs + shiftMs <= tickMs) {
                val frame = frames[nextFrame++]
                clockMs = frame.timeMs + shiftMs
                session.onFrame(frame.copy(timeMs = clockMs, sample = frame.sample?.copy(timeMs = clockMs)))
            }
            clockMs = tickMs
            session.tick(clockMs)
            output.append(tickMs - liveStartMs).append(' ').append(session.ui.value).append('\n')
            tickMs += TICK_MS
        }
        // Shorter recordings end with the Stop button, the way a tester ends a run.
        session.stop()
        output.append("scorecard ").append(session.scorecard.value).append('\n')
        return output.toString()
    }

    private fun readFrames(trace: File): List<PoseFrame> = trace.bufferedReader().use { reader ->
        val header = requireNotNull(reader.readLine()) { "Empty trace: $trace" }
            .removePrefix("﻿").split(',').map { it.trim() }
        val columns = header.withIndex().associate { it.value to it.index }
        fun col(name: String): Int = requireNotNull(columns[name]) { "Missing CSV column $name" }
        reader.lineSequence().filter { it.isNotBlank() }.map { line ->
            val cells = line.split(',')
            fun value(name: String) = cells[col(name)].trim()
            fun f(name: String) = value(name).toFloat()
            val timeMs = value("t_ms").toLong()
            val latencyMs = columns["latency_ms"]?.let { cells[it].trim().toLongOrNull() } ?: 0L
            val nearRight = when (value("side")) {
                "R" -> true
                "L" -> false
                else -> null
            }
            if (value("wrist_signal").isEmpty() || value("shoulder_x").isEmpty()) {
                PoseFrame(timeMs, null, null, latencyMs, ASPECT, nearRight)
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
                    worldElbowDegrees = columns["world_elbow_deg"]?.let { cells[it].trim().toDoubleOrNull() },
                )
                PoseFrame(timeMs, sample, arm, latencyMs, ASPECT, nearRight)
            }
        }.toList()
    }

    companion object {
        private const val ASPECT = 4f / 3f
        private const val TICK_MS = 250L

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun recordings(): List<Array<String>> = (10..16).map { arrayOf("real-cph2467-$it.csv") }
    }
}
