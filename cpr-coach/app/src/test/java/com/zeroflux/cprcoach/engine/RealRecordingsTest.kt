package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.Locale

@RunWith(Parameterized::class)
class RealRecordingsTest(private val fileName: String) {

    @Test
    fun `recorded frames produce only plausible live readings`() {
        val file = File(TestAssets.projectDir, "tools/testdata/$fileName")
        val frames = readFrames(file)
        val analyzer = LiveAnalyzer()
        val readings = mutableListOf<LiveReading>()
        val stateMs = TrackingState.entries.associateWith { 0L }.toMutableMap()
        val endMs = frames.last().timeMs
        var nextReadMs = frames.first().timeMs

        fun readAt(timeMs: Long) {
            val reading = analyzer.read(timeMs)
            readings.add(reading)
            // The displayed state lasts until the next poll, clipped to the recording's end.
            val durationMs = minOf(READ_INTERVAL_MS, endMs - timeMs)
            stateMs[reading.tracking] = stateMs.getValue(reading.tracking) + durationMs
        }

        for (frame in frames) {
            // Poll through gaps before adding a future frame: never look ahead in the CSV.
            while (nextReadMs < frame.timeMs) {
                readAt(nextReadMs)
                nextReadMs += READ_INTERVAL_MS
            }
            analyzer.addFrame(frame.sample)
            if (nextReadMs == frame.timeMs) {
                readAt(nextReadMs)
                nextReadMs += READ_INTERVAL_MS
            }
        }

        val durationMs = endMs - frames.first().timeMs
        val rates = readings.mapNotNull { it.ratePerMinute }
        val shares = TrackingState.entries.joinToString(" ") { state ->
            val percent = stateMs.getValue(state) * 100.0 / durationMs
            "$state=${String.format(Locale.ROOT, "%.1f", percent)}%"
        }
        val rateRange = if (rates.isEmpty()) "none" else "${rates.minOrNull()}..${rates.maxOrNull()}/min"
        val beatingMs = stateMs.getValue(TrackingState.BEATING)
        println("$fileName: $shares rate=$rateRange BEATING=${beatingMs}ms")

        for (reading in readings) {
            val rate = reading.ratePerMinute ?: continue
            val context = "$fileName at ${reading.timeMs}ms (${reading.tracking}): $rate/min"
            assertTrue("Rate outside 60..170: $context", rate in 60..170)
            if (fileName in TIGHT_RATE_FILES) {
                assertTrue("Rate outside 85..135: $context", rate in 85..135)
            }
        }
        if (fileName == NEGATIVE_FILE) {
            assertTrue("$fileName reported BEATING for ${beatingMs}ms (maximum 2000ms)", beatingMs <= 2_000L)
        }
        // No beat-share assertion: real recordings include non-pushing stretches and have no human count.
    }

    private data class RecordedFrame(val timeMs: Long, val sample: PoseSample?)

    private fun readFrames(file: File): List<RecordedFrame> = file.bufferedReader().use { reader ->
        val header = reader.readLine()?.removePrefix("\uFEFF")?.split(',')
        require(header?.take(4) == listOf("t_ms", "wrist_signal", "elbow_deg", "min_vis")) {
            "${file.name}: expected recorded pose CSV columns"
        }
        val frames = reader.lineSequence().filter { it.isNotBlank() }.mapIndexed { index, line ->
            val cells = line.split(',')
            require(cells.size >= 4) { "${file.name}: incomplete row ${index + 2}" }
            val timeMs = cells[0].trim().toLong()
            // Empty wrist signal is a missing pose, not a zero-valued wrist measurement.
            val wrist = cells[1].trim()
            val sample = if (wrist.isEmpty()) null else PoseSample(
                timeMs = timeMs,
                wristSignal = wrist.toDouble(),
                elbowDegrees = cells[2].trim().toDouble(),
                minVisibility = cells[3].trim().toFloat(),
            )
            RecordedFrame(timeMs, sample)
        }.toList()
        require(frames.size >= 2 && frames.last().timeMs > frames.first().timeMs) {
            "${file.name}: recording must span time"
        }
        require(frames.zipWithNext().all { (a, b) -> b.timeMs > a.timeMs }) {
            "${file.name}: timestamps must be strictly increasing"
        }
        frames
    }

    companion object {
        private const val READ_INTERVAL_MS = 250L
        private const val NEGATIVE_FILE = "negative-cph2467-nobody.csv"
        private val TIGHT_RATE_FILES = setOf("real-cph2467-5.csv", "real-cph2467-7.csv")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun recordings(): List<Array<String>> =
            (1..9).map { arrayOf("real-cph2467-$it.csv") } + listOf(arrayOf(NEGATIVE_FILE))
    }
}
