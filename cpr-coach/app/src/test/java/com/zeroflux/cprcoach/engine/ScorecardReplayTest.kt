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

    private fun printCard(name: String, c: Scorecard) {
        println("=== Scorecard for $name ===")
        println("sessionSeconds=${c.sessionSeconds} measuredSeconds=${c.measuredSeconds} secondsInRange=${c.secondsInRange} " +
            "averageRate=${c.averageRate} elbowsStraightPercent=${c.elbowsStraightPercent} pauses=${c.pauses}")
    }

    @Test
    fun `replay real-14 and real-15 and print scorecards`() {
        // Ranges tied to the picture labels, not exact replay numbers, so small tuning does not break them.
        val (card14, _) = replay("real-cph2467-14.csv")
        printCard("real-cph2467-14.csv", card14)
        // 14: pushing 3-24 s with a locked rate for about 20 s, then a rest, pose lost after 33 s.
        assertTrue("session ${card14.sessionSeconds}", card14.sessionSeconds in 55..70)
        assertTrue("measured ${card14.measuredSeconds}", card14.measuredSeconds in 15..25)
        assertTrue("in range ${card14.secondsInRange}", card14.secondsInRange >= card14.measuredSeconds - 4)
        assertTrue("rate ${card14.averageRate}", card14.averageRate in 100..120)
        // Labelled straight throughout the push.
        assertTrue("elbows ${card14.elbowsStraightPercent}", card14.elbowsStraightPercent!! >= 85)

        val (card15, _) = replay("real-cph2467-15.csv")
        printCard("real-cph2467-15.csv", card15)
        assertTrue("session ${card15.sessionSeconds}", card15.sessionSeconds in 65..75)
        assertTrue("measured ${card15.measuredSeconds}", card15.measuredSeconds in 40..55)
        assertTrue("in range ${card15.secondsInRange}", card15.secondsInRange >= card15.measuredSeconds - 8)
        assertTrue("rate ${card15.averageRate}", card15.averageRate in 100..115)
        // About 13 s of labelled bent elbows out of about 54 s judged: roughly 76% straight.
        assertTrue("elbows ${card15.elbowsStraightPercent}", card15.elbowsStraightPercent in 65..90)
    }

    @Test
    fun `scorecard pauses on real recordings`() {
        val (card13, _) = replay("real-cph2467-13.csv")
        val (card14, _) = replay("real-cph2467-14.csv")
        val (card15, _) = replay("real-cph2467-15.csv")
        printCard("real-cph2467-13.csv", card13)
        printCard("real-cph2467-14.csv", card14)
        printCard("real-cph2467-15.csv", card15)
        // 13: pushing 4-36 s without a locked rate for stretches is not a pause (it ends about 38 s).
        assertTrue("13 pauses ${card13.pauses}", card13.pauses <= 1)
        // 14: a real rest of about 8.8 s, even though `moving` flickers.
        assertEquals(1, card14.pauses)
        // 15: the 1.7 s reposition is under 2 s.
        assertTrue("15 pauses ${card15.pauses}", card15.pauses <= 1)
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

        println("elbows straight: PostureJudge ${guardedCard.elbowsStraightPercent}%, ElbowGate ${oldGateCard.elbowsStraightPercent}%")
        // PostureJudge sees the labelled bent blocks in 15 (roughly 76% straight).
        // The old ElbowGate (BENT_BELOW_DEGREES = 100.0) never flags them, so it reads about 100%.
        assertTrue(guardedCard.elbowsStraightPercent!! in 65..90)
        assertTrue(oldGateCard.elbowsStraightPercent!! >= 95)
    }

    private companion object {
        const val ASPECT = 4f / 3f
    }
}
