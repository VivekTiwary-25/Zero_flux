// Locks pace samples from adf9409. Same replay as RealRecordingsTest: LiveAnalyzer,
// poll every 250 ms, never read a frame before its CSV time.
//
// Regenerate tools/testdata/rate_golden from cpr-coach (PowerShell):
//   $env:ANDROID_HOME="$env:LOCALAPPDATA/Android/Sdk"
//   $env:CPR_WRITE_GOLDEN="1"
//   ./gradlew.bat testDebugUnitTest --tests '*RateGoldenTest*' --rerun-tasks
// Compare: same command with CPR_WRITE_GOLDEN unset. Run it twice.
package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class RateGoldenTest(private val fileName: String) {

    @Test
    fun `250 ms pace samples match the golden file`() {
        val file = File(TestAssets.projectDir, "tools/testdata/$fileName")
        val first = replay(file)
        val second = replay(file)
        assertSameSamples(fileName, first, second, "replay is not deterministic")

        val golden = File(TestAssets.projectDir, "tools/testdata/rate_golden/$fileName")
        if (writeGolden()) {
            requireNotNull(golden.parentFile).mkdirs()
            golden.writeText(formatSamples(first), Charsets.UTF_8)
        }
        if (!golden.isFile) {
            fail("Missing golden for $fileName. Set CPR_WRITE_GOLDEN=1 and rerun to regenerate.")
        }
        val expected = readGolden(golden)
        assertSameSamples(fileName, expected, first, "pace changed")
    }

    private data class RecordedFrame(val timeMs: Long, val sample: PoseSample?)

    private data class PaceSample(val t: Long, val tracking: String, val rate: Int?)

    private fun replay(file: File): List<PaceSample> {
        val frames = readFrames(file)
        val analyzer = LiveAnalyzer()
        val samples = mutableListOf<PaceSample>()
        var nextReadMs = frames.first().timeMs

        fun readAt(timeMs: Long) {
            val reading = analyzer.read(timeMs)
            samples.add(PaceSample(reading.timeMs, reading.tracking.name, reading.ratePerMinute))
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
        check(samples.isNotEmpty()) { "$fileName: replay produced no samples" }
        return samples
    }

    /** Same CSV read as RealRecordingsTest: empty wrist signal is a missing pose. */
    private fun readFrames(file: File): List<RecordedFrame> = file.bufferedReader().use { reader ->
        val header = reader.readLine()?.removePrefix("\uFEFF")?.split(',')
        require(header?.take(4) == listOf("t_ms", "wrist_signal", "elbow_deg", "min_vis")) {
            "${file.name}: expected recorded pose CSV columns"
        }
        val frames = reader.lineSequence().filter { it.isNotBlank() }.mapIndexed { index, line ->
            val cells = line.split(',')
            require(cells.size >= 4) { "${file.name}: incomplete row ${index + 2}" }
            val timeMs = cells[0].trim().toLong()
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

    private fun assertSameSamples(
        recording: String,
        expected: List<PaceSample>,
        actual: List<PaceSample>,
        reason: String,
    ) {
        val shared = minOf(expected.size, actual.size)
        for (index in 0 until shared) {
            if (expected[index] != actual[index]) {
                fail(
                    "$recording first differs at t=${expected[index].t}: $reason; " +
                        "expected ${format(expected[index])}, was ${format(actual[index])}",
                )
            }
        }
        if (expected.size != actual.size) {
            if (actual.size < expected.size) {
                fail(
                    "$recording first differs at t=${expected[shared].t}: $reason; " +
                        "expected ${format(expected[shared])}, was missing",
                )
            }
            fail(
                "$recording first differs at t=${actual[shared].t}: $reason; " +
                    "expected missing, was ${format(actual[shared])}",
            )
        }
    }

    private fun format(sample: PaceSample): String =
        "${sample.t},${sample.tracking},${sample.rate?.toString().orEmpty()}"

    private fun formatSamples(samples: List<PaceSample>): String = buildString {
        append("t,tracking,rate\n")
        for (sample in samples) {
            append(format(sample))
            append('\n')
        }
    }

    private fun readGolden(file: File): List<PaceSample> {
        val lines = file.readText(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
        require(lines.firstOrNull() == "t,tracking,rate") { "${file.name}: expected golden header t,tracking,rate" }
        return lines.drop(1).map { line ->
            val cells = line.split(',')
            require(cells.size == 3) { "${file.name}: bad golden row $line" }
            PaceSample(
                t = cells[0].toLong(),
                tracking = cells[1],
                rate = cells[2].takeIf { it.isNotEmpty() }?.toInt(),
            )
        }
    }

    companion object {
        private const val READ_INTERVAL_MS = 250L

        private fun writeGolden(): Boolean {
            val raw = System.getenv("CPR_WRITE_GOLDEN") ?: System.getProperty("CPR_WRITE_GOLDEN")
            return raw == "1" || raw.equals("true", ignoreCase = true)
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun recordings(): List<Array<String>> {
            val dir = File(TestAssets.projectDir, "tools/testdata")
            val names = dir.listFiles { file ->
                file.isFile && (
                    (file.name.startsWith("real-cph2467-") && file.name.endsWith(".csv")) ||
                        file.name == "negative-cph2467-nobody.csv"
                    )
            }?.map { it.name }?.sorted().orEmpty()
            require(names.contains("negative-cph2467-nobody.csv")) {
                "negative-cph2467-nobody.csv not found in ${dir.absolutePath}"
            }
            require(names.any { it.startsWith("real-cph2467-") }) {
                "no real-cph2467 recordings in ${dir.absolutePath}"
            }
            return names.map { arrayOf(it) }
        }
    }
}
