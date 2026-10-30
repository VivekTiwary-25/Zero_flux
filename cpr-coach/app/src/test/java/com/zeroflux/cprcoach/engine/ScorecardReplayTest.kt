package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Replays real phone recordings (real-cph2467-14.csv and real-cph2467-15.csv) through
 * [LiveAnalyzer] and [SessionMetrics] (PoseFrame with [ArmLandmarks], aspect 4/3,
 * reads every 250 ms never before the last frame) and prints the scorecard.
 */
class ScorecardReplayTest {

    private fun loadFrames(name: String): List<PoseFrame> {
        val file = File(TestAssets.projectDir, "tools/testdata/$name")
        val lines = file.bufferedReader().use { it.readLines() }
        val header = lines.first().removePrefix("\uFEFF").split(',')
        fun col(n: String) = header.indexOf(n)
        val frames = ArrayList<PoseFrame>()
        for (line in lines.drop(1).filter { it.isNotBlank() }) {
            val c = line.split(',')
            val t = c[col("t_ms")].trim().toLong()
            if (c[col("wrist_signal")].isBlank() || c[col("shoulder_x")].isBlank()) {
                frames.add(PoseFrame(t, null, null, 0L, ASPECT))
                continue
            }
            fun f(n: String) = c[col(n)].trim().toFloat()
            val arm = ArmLandmarks(
                f("shoulder_x"), f("shoulder_y"), f("shoulder_vis"),
                f("elbow_x"), f("elbow_y"), f("elbow_vis"),
                f("wrist_x"), f("wrist_y"), f("wrist_vis"),
                f("hip_x"), f("hip_y"), f("hip_vis"),
            )
            val sideOn = if ("side_on" in header) c[col("side_on")].trim() != "0" else true
            val side = if ("side" in header && c[col("side")].isNotBlank()) c[col("side")].trim() == "R" else null
            val sample = PoseSample(
                timeMs = t,
                wristSignal = c[col("wrist_signal")].trim().toDouble(),
                elbowDegrees = PoseMath.elbowDegrees(arm, ASPECT),
                minVisibility = f("min_vis"),
                elbowVisibility = arm.elbowVis,
                sideOn = sideOn,
            )
            frames.add(PoseFrame(t, sample, arm, 0L, ASPECT, side))
        }
        return frames
    }

    private fun replay(name: String): Pair<Scorecard, List<LiveReading>> {
        val frames = loadFrames(name)
        val t0 = frames.first().timeMs
        val analyzer = LiveAnalyzer()
        val metrics = SessionMetrics()
        val readings = ArrayList<LiveReading>()
        var next = 0
        var tick = t0 + 250L
        while (tick <= frames.last().timeMs) {
            while (next < frames.size && frames[next].timeMs <= tick) {
                analyzer.addFrame(frames[next++])
            }
            val reading = analyzer.read(tick)
            metrics.add(reading)
            readings.add(reading)
            tick += 250L
        }
        return metrics.scorecard() to readings
    }

    @Test
    fun `replay real-14 and real-15 and print scorecards`() {
        val (card14, _) = replay("real-cph2467-14.csv")
        println("=== Scorecard for real-cph2467-14.csv ===")
        println("sessionSeconds: ${card14.sessionSeconds}")
        println("measuredSeconds: ${card14.measuredSeconds}")
        println("secondsInRange: ${card14.secondsInRange}")
        println("averageRate: ${card14.averageRate}")
        println("elbowsStraightPercent: ${card14.elbowsStraightPercent}")
        println("pauses: ${card14.pauses}")

        assertEquals(63, card14.sessionSeconds)
        assertEquals(20, card14.measuredSeconds)
        assertEquals(20, card14.secondsInRange)
        assertEquals(113, card14.averageRate)
        assertEquals(95, card14.elbowsStraightPercent)
        assertEquals(0, card14.pauses)

        val (card15, _) = replay("real-cph2467-15.csv")
        println("=== Scorecard for real-cph2467-15.csv ===")
        println("sessionSeconds: ${card15.sessionSeconds}")
        println("measuredSeconds: ${card15.measuredSeconds}")
        println("secondsInRange: ${card15.secondsInRange}")
        println("averageRate: ${card15.averageRate}")
        println("elbowsStraightPercent: ${card15.elbowsStraightPercent}")
        println("pauses: ${card15.pauses}")

        assertEquals(71, card15.sessionSeconds)
        assertEquals(49, card15.measuredSeconds)
        assertEquals(45, card15.secondsInRange)
        assertEquals(107, card15.averageRate)
        assertEquals(75, card15.elbowsStraightPercent)
        assertEquals(1, card15.pauses)
    }

    @Test
    fun `elbow straight percent comes from PostureJudge rather than ElbowGate`() {
        val frames15 = loadFrames("real-cph2467-15.csv")
        val t0 = frames15.first().timeMs
        val guardedAnalyzer = LiveAnalyzer()
        val oldGateAnalyzer = LiveAnalyzer()
        val guardedMetrics = SessionMetrics()
        val oldGateMetrics = SessionMetrics()

        var next = 0
        var tick = t0 + 250L
        while (tick <= frames15.last().timeMs) {
            while (next < frames15.size && frames15[next].timeMs <= tick) {
                val f = frames15[next++]
                guardedAnalyzer.addFrame(f)
                oldGateAnalyzer.addFrame(f.sample)
            }
            guardedMetrics.add(guardedAnalyzer.read(tick))
            oldGateMetrics.add(oldGateAnalyzer.read(tick))
            tick += 250L
        }

        val guardedCard = guardedMetrics.scorecard()
        val oldGateCard = oldGateMetrics.scorecard()

        // PostureJudge identifies bent elbows in 15, yielding 75%.
        // The old ElbowGate (BENT_BELOW_DEGREES = 100.0) never flagged any frames as bent, yielding 100%.
        assertEquals(75, guardedCard.elbowsStraightPercent)
        assertEquals(100, oldGateCard.elbowsStraightPercent)
    }

    private companion object {
        const val ASPECT = 4f / 3f
    }
}
