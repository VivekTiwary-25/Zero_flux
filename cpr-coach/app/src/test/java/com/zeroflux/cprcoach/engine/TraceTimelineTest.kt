// gradlew.bat testDebugUnitTest --tests '*TraceTimelineTest*' -DCPR_TRACE=...
// Gradle does not forward this property by default. Use $env:CPR_TRACE='...' in PowerShell.
// Add --rerun-tasks when changing CPR_TRACE so Gradle does not reuse an earlier test result.
package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.CameraUiState
import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/** Opt-in diagnostic. No recording-specific coaching expectations. */
class TraceTimelineTest {
    private data class Played(val timeMs: Long, val key: String)
    private data class Snapshot(val timeMs: Long, val live: CameraUiState.Live?, val done: Boolean)

    @Test
    fun `print what the session shows and says for a trace`() {
        val path = System.getProperty("CPR_TRACE")?.takeIf { it.isNotBlank() }
            ?: System.getenv("CPR_TRACE")?.takeIf { it.isNotBlank() }
        assumeTrue("Set CPR_TRACE to a session CSV to run the timeline", path != null)
        val requested = File(requireNotNull(path))
        // Gradle runs tests from app/, but project-relative paths are convenient at the command line.
        val trace = if (requested.isAbsolute || requested.isFile) requested
            else File(TestAssets.projectDir, path)
        require(trace.isFile) { "Trace does not exist: ${trace.absolutePath}" }
        val frames = readFrames(trace)
        require(frames.isNotEmpty()) { "Trace has no frames: $trace" }
        require(frames.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs }) {
            "Trace timestamps must be nondecreasing"
        }

        var clockMs = 1_000L
        val played = ArrayList<Played>()
        val audio = object : SessionAudio {
            override fun play(key: String) { played.add(Played(clockMs, key)) }
            override fun startMetronome() = Unit
            override fun stopMetronome() = Unit
        }
        val session = PractiseSession(audio)
        // Surface engine failures instead of letting the session's recovery hide a broken replay.
        session.onEngineError = { throw AssertionError("Trace analysis failed at $clockMs ms", it) }

        // Setup is outside the recording. This also lets a trace begin with no person in view.
        val setupArm = ArmLandmarks(
            0.50f, 0.30f, 0.9f, 0.50f, 0.45f, 0.9f,
            0.50f, 0.60f, 0.9f, 0.30f, 0.55f, 0.9f,
        )
        val framingDeadlineMs = clockMs + 5_000L
        while (!(session.ui.value as CameraUiState.Framing).ready && clockMs < framingDeadlineMs) {
            session.onFrame(PoseFrame(clockMs, null, setupArm, 0L, ASPECT))
            if (!(session.ui.value as CameraUiState.Framing).ready) clockMs += 40L
        }
        assertTrue("Framing must be ready", (session.ui.value as CameraUiState.Framing).ready)
        session.start(clockMs)
        assertTrue("Start must enter countdown", session.ui.value is CameraUiState.Countdown)
        val countdownDeadlineMs = clockMs + 5_000L
        while (session.ui.value is CameraUiState.Countdown && clockMs < countdownDeadlineMs) {
            clockMs += TICK_MS
            session.tick(clockMs)
        }
        assertTrue("Countdown must enter live", session.ui.value is CameraUiState.Live)
        val liveStartMs = clockMs
        val shiftMs = liveStartMs - frames.first().timeMs
        val durationMs = frames.last().timeMs - frames.first().timeMs
        // Include the last frame at the next normal tick, never adding a whole extra second of silence.
        val lastTickMs = liveStartMs + ((durationMs + TICK_MS - 1) / TICK_MS) * TICK_MS
        val snapshots = linkedMapOf<Long, Snapshot>()
        var nextFrame = 0
        var tickMs = liveStartMs
        while (tickMs <= lastTickMs) {
            while (nextFrame < frames.size && frames[nextFrame].timeMs + shiftMs <= tickMs) {
                val frame = frames[nextFrame++]
                val timeMs = frame.timeMs + shiftMs
                check(timeMs >= clockMs) { "Frame preceded the replay clock" }
                clockMs = timeMs
                session.onFrame(frame.copy(timeMs = timeMs, sample = frame.sample?.copy(timeMs = timeMs)))
            }
            check(tickMs >= clockMs) { "Tick preceded the latest frame" }
            clockMs = tickMs
            session.tick(clockMs)
            val second = (clockMs - liveStartMs) / 1_000L
            snapshots[second] = Snapshot(
                clockMs - liveStartMs,
                session.ui.value as? CameraUiState.Live,
                session.scorecard.value != null,
            )
            tickMs += TICK_MS
        }

        val output = buildString {
            appendLine("Trace: ${trace.name}; frames=${frames.size}; duration=${seconds(durationMs)}s")
            appendLine("t is a live-relative second [t,t+1); UI is the last 250 ms tick in that second.")
            appendLine("Setup/countdown precede t=0; final tick=${seconds(lastTickMs - liveStartMs)}s.")
            appendLine("Elbow median is the posture judge's value; -- means unknown. Default 120 s session.")
            for ((second, snapshot) in snapshots) {
                val live = snapshot.live.takeUnless { snapshot.done }
                val phrases = played.filter { (it.timeMs - liveStartMs) in second * 1_000L until (second + 1) * 1_000L }
                    .joinToString(", ") { "${seconds(it.timeMs - liveStartMs)}s:${it.key}" }
                appendLine(
                    "t=${second}s sample=${seconds(snapshot.timeMs)}s " +
                        "tracking=${if (snapshot.done) "DONE" else live?.tracking ?: "--"} " +
                        "rate=${live?.ratePerMinute ?: "--"} " +
                        "posture_good=${live?.posture?.good ?: "--"} " +
                        "posture_issue=${live?.posture?.issue ?: "--"} " +
                        "elbow_median=${live?.posture?.elbowDegrees?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "--"} " +
                        "phrases=${phrases.ifEmpty { "--" }}",
                )
            }
            appendLine("Phrases (live-relative time, including countdown):")
            for (event in played) appendLine("${seconds(event.timeMs - liveStartMs)}s ${event.key}")
        }
        print(output)
        val destination = File(TestAssets.projectDir, "app/build/trace-timeline.txt")
        requireNotNull(destination.parentFile).mkdirs()
        destination.writeText(output, Charsets.UTF_8)
    }

    private fun readFrames(trace: File): List<PoseFrame> = trace.bufferedReader().use { reader ->
        val header = requireNotNull(reader.readLine()) { "Empty trace: $trace" }
            .removePrefix("\uFEFF").split(',').map { it.trim() }
        val columns = header.withIndex().associate { it.value to it.index }
        fun col(name: String): Int = requireNotNull(columns[name]) { "Missing CSV column $name" }
        val required = listOf(
            "t_ms", "wrist_signal", "elbow_deg", "min_vis", "side", "side_on",
            "shoulder_x", "shoulder_y", "shoulder_vis", "elbow_x", "elbow_y", "elbow_vis",
            "wrist_x", "wrist_y", "wrist_vis", "hip_x", "hip_y", "hip_vis",
        )
        required.forEach { col(it) }
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

    private companion object {
        const val ASPECT = 4f / 3f
        const val TICK_MS = 250L
        fun seconds(timeMs: Long): String = String.format(Locale.ROOT, "%.2f", timeMs / 1_000.0)
    }
}
