package com.zeroflux.cprcoach.ui

import com.zeroflux.cprcoach.engine.LiveAnalyzer
import com.zeroflux.cprcoach.engine.PoseSample
import com.zeroflux.cprcoach.engine.SessionMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Recordings 10-16 through the app's own analyzer, plus fixed synthetic sessions.
 * CPR_WRITE_SAMPLES=1 rewrites assets/dashboard_samples.json; otherwise the file must match.
 */
class DashboardSamplesTest {

    @Test
    fun `sample asset matches recordings 10 to 16 and the synthetic set`() {
        val expected = testingSessions() + syntheticSessions()
        val file = File(TestAssets.assetsDir, "dashboard_samples.json")
        if (System.getenv("CPR_WRITE_SAMPLES") == "1") {
            file.parentFile?.mkdirs()
            file.writeText(DashboardLogic.encodeSamples(expected))
        }
        assertTrue("Missing ${file.path}. Run with CPR_WRITE_SAMPLES=1 to write it.", file.isFile)
        val parsed = DashboardLogic.parseSamples(file.readText())
        assertEquals(expected.map { it.id }, parsed.map { it.id })
        expected.zip(parsed).forEach { (want, got) ->
            assertEquals(want.id, want, got)
        }
        assertTrue(parsed.filter { it.tag == SessionTag.TESTING }.all { it.daysAgo in 0 until DashboardLogic.TWO_WEEKS_DAYS })
        assertTrue(parsed.filter { it.tag == SessionTag.SYNTHETIC }.all { it.daysAgo in 0 until DashboardLogic.TWO_WEEKS_DAYS })
        assertTrue(parsed.any { it.tag == SessionTag.TESTING })
        assertTrue(parsed.any { it.tag == SessionTag.SYNTHETIC })
        assertTrue(parsed.any { it.rates == null })
        val synthetic = parsed.filter { it.tag == SessionTag.SYNTHETIC }
        assertTrue(synthetic.any { it.elbowsStraightPercent == null })
        assertTrue(synthetic.flatMap { it.rates.orEmpty() }.filterNotNull().all { it in 92..128 })
        assertTrue(synthetic.flatMap { it.rates.orEmpty() }.filterNotNull().distinct().size > 10)
    }

    private fun testingSessions(): List<PractiseRecord> =
        (10..16).map { number -> replay(number) }

    private fun replay(number: Int): PractiseRecord {
        val file = File(TestAssets.projectDir, "tools/testdata/real-cph2467-$number.csv")
        val frames = readFrames(file)
        val analyzer = LiveAnalyzer()
        val metrics = SessionMetrics()
        val rates = mutableListOf<Int?>()
        val end = frames.last().timeMs
        var nextRead = frames.first().timeMs
        var nextSample = nextRead
        var index = 0
        while (nextRead <= end) {
            while (index < frames.size && frames[index].timeMs <= nextRead) {
                analyzer.addFrame(frames[index].sample)
                index++
            }
            val reading = analyzer.read(nextRead)
            metrics.add(reading)
            if (nextRead >= nextSample) {
                rates.add(reading.ratePerMinute)
                nextSample += 1_000
            }
            nextRead += 250
        }
        val card = metrics.scorecard()
        return PractiseRecord(
            id = "testing-$number",
            tag = SessionTag.TESTING,
            daysAgo = number - 9,
            minuteOfDay = 8 * 60 + (number - 10) * 35,
            durationSeconds = card.sessionSeconds,
            averageRate = card.averageRate,
            secondsInRange = card.secondsInRange,
            elbowsStraightPercent = card.elbowsStraightPercent,
            pauses = card.pauses,
            measuredSeconds = card.measuredSeconds,
            rates = rates,
        )
    }

    private data class Frame(val timeMs: Long, val sample: PoseSample?)

    private fun readFrames(file: File): List<Frame> = file.bufferedReader().use { reader ->
        val header = reader.readLine()?.removePrefix("\uFEFF")?.split(',')
        require(header?.take(4) == listOf("t_ms", "wrist_signal", "elbow_deg", "min_vis")) {
            "${file.name}: expected recorded pose CSV columns"
        }
        reader.lineSequence().filter { it.isNotBlank() }.map { line ->
            val cells = line.split(',')
            val timeMs = cells[0].trim().toLong()
            val wrist = cells[1].trim()
            val sample = if (wrist.isEmpty()) {
                null
            } else {
                PoseSample(
                    timeMs = timeMs,
                    wristSignal = wrist.toDouble(),
                    elbowDegrees = cells[2].trim().toDouble(),
                    minVisibility = cells[3].trim().toFloat(),
                )
            }
            Frame(timeMs, sample)
        }.toList()
    }
}

private fun syntheticSessions(): List<PractiseRecord> = listOf(
    synthetic(
        id = "synthetic-1",
        daysAgo = 0,
        minuteOfDay = 9 * 60 + 40,
        elbows = 88,
        pauses = 1,
        spans = listOf(8 to null, 70 to 112, 10 to 96, 20 to 118, 12 to null),
    ),
    synthetic(
        id = "synthetic-2",
        daysAgo = 1,
        minuteOfDay = 18 * 60 + 10,
        elbows = 91,
        pauses = 0,
        spans = listOf(6 to null, 50 to 104, 8 to 126, 16 to 108, 10 to null),
    ),
    synthetic(
        id = "synthetic-3",
        daysAgo = 2,
        minuteOfDay = 7 * 60 + 50,
        elbows = null,
        pauses = 2,
        spans = listOf(12 to 125, 40 to 116, 8 to 98, 15 to null),
    ),
    synthetic(
        id = "synthetic-4",
        daysAgo = 3,
        minuteOfDay = 20 * 60 + 5,
        elbows = 70,
        pauses = 3,
        spans = listOf(20 to 94, 15 to 108, 10 to 93, 15 to null),
    ),
    synthetic(
        id = "synthetic-5",
        daysAgo = 5,
        minuteOfDay = 11 * 60 + 20,
        elbows = 94,
        pauses = 1,
        spans = listOf(10 to null, 80 to 109, 10 to 114),
    ),
    PractiseRecord(
        id = "synthetic-6",
        tag = SessionTag.SYNTHETIC,
        daysAgo = 8,
        minuteOfDay = 16 * 60,
        durationSeconds = 45,
    ),
    synthetic(
        id = "synthetic-7",
        daysAgo = 10,
        minuteOfDay = 13 * 60 + 30,
        elbows = null,
        pauses = 1,
        spans = listOf(20 to 128, 30 to 112, 16 to 124, 24 to 108),
    ),
    synthetic(
        id = "synthetic-8",
        daysAgo = 13,
        minuteOfDay = 8 * 60 + 15,
        elbows = 85,
        pauses = 0,
        spans = listOf(16 to null, 40 to 111, 8 to 127, 16 to 106),
    ),
)

private fun synthetic(
    id: String,
    daysAgo: Int,
    minuteOfDay: Int,
    elbows: Int?,
    pauses: Int,
    spans: List<Pair<Int, Int?>>,
): PractiseRecord {
    // Each span is a steady pace with a small fixed wobble, so every run gives the same file.
    var second = 0
    val rates = spans.flatMap { (seconds, rate) ->
        List(seconds) {
            val wobble = (second * 7 + id.length * 3) % 9 - 4
            second++
            rate?.let { (it + wobble).coerceIn(92, 128) }
        }
    }
    val known = rates.filterNotNull()
    return PractiseRecord(
        id = id,
        tag = SessionTag.SYNTHETIC,
        daysAgo = daysAgo,
        minuteOfDay = minuteOfDay,
        durationSeconds = rates.size,
        averageRate = known.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() },
        secondsInRange = known.count { it in DashboardLogic.TARGET_MIN..DashboardLogic.TARGET_MAX },
        elbowsStraightPercent = elbows,
        pauses = pauses,
        measuredSeconds = known.size,
        rates = rates,
    )
}
