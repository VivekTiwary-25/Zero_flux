// PowerShell: $env:CPR_TRACE='tools/testdata/real-cph2467-15.csv'
// Optional: $env:CPR_CLIP_LANGUAGE='en,kn'; CPR_TRACE also accepts comma-separated CSV paths.
// ./gradlew.bat testDebugUnitTest --tests '*EmergencyTimelineTest*' --rerun-tasks
// Use the environment variable: Gradle does not forward -DCPR_TRACE by default.
package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.audio.WavClip
import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

/** Opt-in diagnostic for any pose CSV. No recording-specific coaching expectations. */
class EmergencyTimelineTest {
    private data class Spoken(
        val timeMs: Long,
        val key: String,
        val durationMs: Long,
        val endMs: Long,
        val previousClipEndMs: Long?,
    )
    private data class Snapshot(
        val reading: LiveReading,
        val word: EmergencyWord,
        val instructionKey: String,
        val voiceIdle: Boolean,
        val lastClipEndedMs: Long?,
    )

    @Test
    fun `print what Emergency shows and says for a trace`() {
        val paths = option("CPR_TRACE")
        assumeTrue("Set CPR_TRACE to a session CSV to run the Emergency timeline", paths != null)
        val languages = (option("CPR_CLIP_LANGUAGE") ?: "en").split(',').map { it.trim() }
        for (path in requireNotNull(paths).split(',').map { it.trim() }) {
            for (language in languages) printTimeline(path, language)
        }
    }

    private fun printTimeline(path: String, language: String) {
        require(language == "en" || language == "kn") { "Unsupported clip language: $language" }
        val requested = File(path)
        // Gradle runs tests from app/. Also accept paths relative to the Android project.
        val trace = if (requested.isAbsolute || requested.isFile) requested else File(TestAssets.projectDir, path)
        require(trace.isFile) { "Trace does not exist: ${trace.absolutePath}" }
        val frames = readFrames(trace)
        require(frames.isNotEmpty()) { "Trace has no frames: $trace" }
        require(frames.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs }) {
            "Trace timestamps must be nondecreasing"
        }

        val startMs = frames.first().timeMs
        val durationMs = frames.last().timeMs - startMs
        // Consume the final frame on the next regular read, without an extra second of silence.
        val lastTickMs = startMs + ((durationMs + TICK_MS - 1) / TICK_MS) * TICK_MS
        val analyzer = LiveAnalyzer()
        val coach = EmergencyCoach(startMs, rateHoldMs = 500)
        val spoken = ArrayList<Spoken>()
        val playback = ReplayPlayback(spoken, language)
        playback.start(startMs)
        val snapshots = ArrayList<Snapshot>()
        var nextFrame = 0
        var lastFrameMs = startMs
        var tickMs = startMs
        var lastSequenceTickMs: Long? = null
        while (tickMs <= lastTickMs) {
            while (nextFrame < frames.size && frames[nextFrame].timeMs <= tickMs) {
                val frame = frames[nextFrame++]
                analyzer.addFrame(frame)
                lastFrameMs = frame.timeMs
            }
            check(tickMs >= lastFrameMs) { "Read preceded the latest frame" }
            // Completion callbacks can drain the scripted queue between host polls.
            playback.advanceTo(tickMs)
            // EmergencyHost: sequence tick, read, update, word, then observe policy even while voice is busy.
            if (lastSequenceTickMs?.let { tickMs - it >= 1_000L } != false) {
                lastSequenceTickMs = tickMs
                playback.tick(tickMs)
            }
            val reading = analyzer.read(tickMs)
            coach.update(reading)
            val word = coach.word
            val voiceIdle = playback.voiceIdle
            val lastClipEndedMs = playback.lastClipEndedMs
            coach.nextClip(reading, voiceIdle, lastClipEndedMs)?.let {
                checkRateLine(it, reading)
                playback.speak(it, tickMs)
            }
            val instructionKey = if (word == EmergencyWord.KEEP_PUSHING) playback.stepKey ?: word.key else word.key
            snapshots.add(Snapshot(reading, word, instructionKey, voiceIdle, lastClipEndedMs))
            tickMs += TICK_MS
        }
        check(nextFrame == frames.size) { "Replay did not consume every frame" }
        check(spoken.zipWithNext().all { (a, b) -> a.endMs <= b.timeMs }) { "Voice clips overlapped" }

        val output = buildString {
            appendLine("Trace: ${trace.name}; language=$language; frames=${frames.size}; duration=${seconds(durationMs)}s")
            appendLine("t is recording-relative [t,t+1); state is the last 250 ms read in that second.")
            appendLine("Final read=${seconds(lastTickMs - startMs)}s; word is the screen instruction key; coach_word is the held coach word.")
            appendLine("$language audio is ready at t=0; clip completion uses WAV duration, without device/loading latency.")
            appendLine(
                "Summary: lines=${spoken.size}; lines_per_minute=" +
                    String.format(Locale.ROOT, "%.3f", spoken.size * 60_000.0 / durationMs.coerceAtLeast(1)) +
                    "; last_clip_end_ms=${playback.lastClipEndedMs?.minus(startMs) ?: "--"}; " +
                    "stacked_lines=0; false_rate_lines=0",
            )
            for ((second, snapshot) in snapshots.associateBy { (it.reading.timeMs - startMs) / 1_000L }) {
                val reading = snapshot.reading
                val keys = spoken.filter { (it.timeMs - startMs) in second * 1_000L until (second + 1) * 1_000L }
                    .joinToString(", ") { "${seconds(it.timeMs - startMs)}s:${it.key}" }
                appendLine(
                    "t=${second}s sample=${seconds(reading.timeMs - startMs)}s " +
                        "tracking=${reading.tracking} rate=${reading.ratePerMinute ?: "--"} " +
                        "posture_issue=${reading.posture?.issue ?: "--"} " +
                        "word=${snapshot.instructionKey} coach_word=${snapshot.word} spoken=${keys.ifEmpty { "--" }}",
                )
            }
            appendLine("Word stretches (250 ms samples; [start,end), final endpoint is the final read):")
            var stretchStartMs = startMs
            var instructionKey = snapshots.first().instructionKey
            for (snapshot in snapshots.drop(1)) {
                if (snapshot.instructionKey != instructionKey) {
                    appendLine("${seconds(stretchStartMs - startMs)}-${seconds(snapshot.reading.timeMs - startMs)}s $instructionKey")
                    stretchStartMs = snapshot.reading.timeMs
                    instructionKey = snapshot.instructionKey
                }
            }
            appendLine("${seconds(stretchStartMs - startMs)}-${seconds(lastTickMs - startMs)}s $instructionKey")
            appendLine("Spoken keys (recording-relative times; completion may be beyond the final read):")
            for (event in spoken) {
                appendLine(
                    "VOICE t_ms=${event.timeMs - startMs} key=${event.key} duration_ms=${event.durationMs} " +
                        "end_ms=${event.endMs - startMs} previous_clip_end_ms=${event.previousClipEndMs?.minus(startMs) ?: "--"} " +
                        "silence_ms=${event.previousClipEndMs?.let { event.timeMs - it } ?: "--"}",
                )
            }
            appendLine("Exact host reads (voice_idle and last_clip_end are before choosing this read's correction):")
            for (snapshot in snapshots) {
                appendLine(
                    "READ t_ms=${snapshot.reading.timeMs - startMs} word=${snapshot.instructionKey} " +
                        "coach_word=${snapshot.word} voice_idle=${snapshot.voiceIdle} " +
                        "last_clip_end_ms=${snapshot.lastClipEndedMs?.minus(startMs) ?: "--"} ${snapshot.reading}",
                )
            }
        }
        print(output)
        val destination = File(TestAssets.projectDir, "app/build/emergency-timeline-${trace.nameWithoutExtension}-$language.txt")
        requireNotNull(destination.parentFile).mkdirs()
        destination.writeText(output, Charsets.UTF_8)
        File(destination.parentFile, "emergency-timeline.txt").writeText(output, Charsets.UTF_8)
    }

    /** EmergencyPlayback's single-clip queue, driven by clip durations instead of Android callbacks. */
    private class ReplayPlayback(private val spoken: MutableList<Spoken>, private val language: String) {
        private val sequence = EmergencySequence()
        private val pending = ArrayDeque<String>()
        private val durationsMs = HashMap<String, Long>()
        private var currentKey: String? = null
        private var doneMs = 0L
        var stepKey: String? = null
            private set
        var lastClipEndedMs: Long? = null
            private set
        val voiceIdle: Boolean get() = currentKey == null && pending.isEmpty()

        fun start(nowMs: Long) {
            pending.addAll(sequence.start(nowMs))
            playNext(nowMs)
        }

        fun advanceTo(nowMs: Long) {
            while (currentKey != null && doneMs <= nowMs) {
                val completedMs = doneMs
                currentKey = null
                lastClipEndedMs = completedMs
                playNext(completedMs)
            }
        }

        fun tick(nowMs: Long) {
            pending.addAll(sequence.tick(nowMs))
            playNext(nowMs)
        }

        fun speak(key: String, nowMs: Long) {
            check(voiceIdle) { "Correction must not interrupt or queue behind another clip" }
            pending.add(key)
            playNext(nowMs)
        }

        private fun playNext(nowMs: Long) {
            if (currentKey != null) return
            if (pending.isEmpty()) {
                stepKey = null
                return
            }
            val key = pending.removeFirst()
            currentKey = key
            stepKey = when (key) {
                "em_call" -> "em_step_call"
                "em_hands" -> "em_step_hands"
                "em_push" -> "em_step_push"
                else -> null
            }
            val durationMs = durationsMs.getOrPut(key) {
                val file = File(TestAssets.assetsDir, "clips/$language/$key.wav")
                require(file.isFile) { "Missing $language clip: $file" }
                val clip = WavClip.parse(file.readBytes())
                (clip.samples.size * 1_000L + clip.sampleRate - 1) / clip.sampleRate
            }
            doneMs = nowMs + durationMs
            check(spoken.lastOrNull()?.let { it.endMs <= nowMs } != false) { "Voice clips overlapped at $nowMs" }
            spoken.add(Spoken(nowMs, key, durationMs, doneMs, lastClipEndedMs))
        }
    }

    private fun checkRateLine(key: String, reading: LiveReading) {
        val rate = reading.ratePerMinute
        when (key) {
            Phrase.PUSH_FASTER.key -> check(
                reading.tracking == TrackingState.BEATING && rate != null && rate < LiveReading.TARGET_MIN,
            ) { "False faster line at $reading" }
            Phrase.SLOW_DOWN.key -> check(
                reading.tracking == TrackingState.BEATING && rate != null && rate > LiveReading.TARGET_MAX,
            ) { "False slower line at $reading" }
        }
    }

    private fun readFrames(trace: File): List<PoseFrame> = trace.bufferedReader().use { reader ->
        val header = requireNotNull(reader.readLine()) { "Empty trace: $trace" }
            .removePrefix("\uFEFF").split(',').map { it.trim() }
        val columns = header.withIndex().associate { it.value to it.index }
        fun col(name: String): Int = requireNotNull(columns[name]) { "Missing CSV column $name" }
        val required = listOf(
            "t_ms", "wrist_signal", "min_vis", "side", "side_on",
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
        fun option(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: System.getProperty(name)?.takeIf { it.isNotBlank() }
        fun seconds(timeMs: Long): String = String.format(Locale.ROOT, "%.2f", timeMs / 1_000.0)
    }
}
