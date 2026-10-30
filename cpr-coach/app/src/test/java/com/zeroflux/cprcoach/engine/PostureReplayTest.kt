package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Replays every recording that has joint coordinates through [PostureJudge] and writes
 * app/build/posture_replay/<file> (t_s,good,issue) for tools/posture_report.py, which adds the labels.
 *
 * Most recordings were made with a rotation bug that swapped the aspect in the stored elbow_deg. The phone was
 * landscape, so angles are recomputed from the raw coordinates at the true 4:3.
 * Older recordings have no side_on column, so side-on is assumed true there.
 */
class PostureReplayTest {
    private data class Row(val tS: Double, val reading: PostureReading?)
    private class Replay(val rows: List<Row>, val judged: Int, val rejected: Int, val lostBase: Int, val lostGuarded: Int)

    private fun replay(file: File): Replay? {
        val lines = file.bufferedReader().use { it.readLines() }
        val header = lines.first().removePrefix("﻿").split(',')
        if ("shoulder_x" !in header) return null
        fun col(n: String) = header.indexOf(n)
        val judge = PostureJudge()
        val guard = LandmarkGuard()
        // LOST frames with and without the guard in front of the same analyzer (the sample-only path has none).
        val base = LiveAnalyzer()
        val guarded = LiveAnalyzer()
        var lostBase = 0
        var lostGuarded = 0
        val rows = ArrayList<Row>()
        var t0: Long? = null
        for (line in lines.drop(1).filter { it.isNotBlank() }) {
            val c = line.split(',')
            val t = c[0].trim().toLong()
            val start = t0 ?: t.also { t0 = it }
            val tS = (t - start) / 1000.0
            if (c[col("wrist_signal")].isBlank() || c[col("shoulder_x")].isBlank()) {
                rows.add(Row(tS, null))
                if (base.read(t).tracking == TrackingState.LOST) lostBase++
                if (guarded.read(t).tracking == TrackingState.LOST) lostGuarded++
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
            val sample = PoseSample(
                timeMs = t,
                wristSignal = c[col("wrist_signal")].trim().toDouble(),
                elbowDegrees = c[col("elbow_deg")].trim().toDouble(),
                minVisibility = f("min_vis"),
                elbowVisibility = arm.elbowVis,
                sideOn = sideOn,
            )
            val frame = PoseFrame(t, sample, arm, 0L, ASPECT, if ("side" in header) c[col("side")].trim() == "R" else null)
            base.addFrame(sample)
            guarded.addFrame(frame)
            if (base.read(t).tracking == TrackingState.LOST) lostBase++
            if (guarded.read(t).tracking == TrackingState.LOST) lostGuarded++
            // As the app does: a rejected frame is dropped entirely.
            if (!guard.check(frame).accepted) {
                rows.add(Row(tS, null))
                continue
            }
            val corrected = if (arm != null) sample.copy(elbowDegrees = PoseMath.elbowDegrees(arm, ASPECT)) else sample
            rows.add(Row(tS, judge.update(corrected, arm, ASPECT)))
        }
        return Replay(rows, guard.judged, guard.rejects, lostBase, lostGuarded)
    }

    @Test
    fun `replay all recordings and write per frame verdicts`() {
        val dir = File(TestAssets.projectDir, "tools/testdata")
        val out = File(TestAssets.projectDir, "app/build/posture_replay").also { it.mkdirs() }
        val shares = HashMap<String, Triple<Double, Double, Double>>()
        for (file in dir.listFiles { f -> f.name.endsWith(".csv") && f.name != "labels.csv" }!!.sortedBy { it.name }) {
            val played = replay(file) ?: continue
            val rows = played.rows
            File(out, file.name).bufferedWriter().use { w ->
                w.write("t_s,good,issue\n")
                for (r in rows) {
                    val p = r.reading
                    w.write(String.format(Locale.ROOT, "%.3f,%s,%s\n", r.tS, p?.good?.toString() ?: "", p?.issue?.name ?: ""))
                }
            }
            val n = rows.size.toDouble()
            val good = rows.count { it.reading?.good == true } / n
            val issue = rows.count { it.reading?.issue != null } / n
            shares[file.name] = Triple(good, issue, 1 - good - issue)
            val bent = rows.count { it.reading?.issue == PostureIssue.ELBOWS_BENT } / n
            val lean = rows.count { it.reading?.issue == PostureIssue.SHOULDERS_NOT_OVER_HANDS } / n
            println(String.format(Locale.ROOT, "guard %s: rejected %d of %d judged frames (%.1f%%)",
                file.name, played.rejected, played.judged, 100.0 * played.rejected / maxOf(1, played.judged)))
            println(String.format(Locale.ROOT, "lost %s: frames LOST without guard %d, with guard %d (of %d rows)",
                file.name, played.lostBase, played.lostGuarded, rows.size))
            println(String.format(Locale.ROOT, "posture %s: good=%.0f%% issue=%.0f%% (bent %.0f%%, lean %.0f%%) null=%.0f%%",
                file.name, good * 100, issue * 100, bent * 100, lean * 100, (1 - good - issue) * 100))
            if (file.name == "real-cph2467-12.csv") {
                // The team-unconfirmed bent block is at about 18-29 s; normal pushing before it must not read as bent.
                val early = rows.filter { it.tS < 15.0 }
                assertTrue("12: quiet while pushing normally before 15 s", early.none { it.reading?.issue != null })
                val bentBlock = rows.filter { it.tS in 21.0..30.0 }
                val bentShare = bentBlock.count { it.reading?.issue == PostureIssue.ELBOWS_BENT } / bentBlock.size.toDouble()
                println(String.format(Locale.ROOT, "posture 12 bent block 21-30 s flagged bent: %.0f%%", bentShare * 100))
                assertTrue("12: the bent block is flagged for most of 21-30 s", bentShare > 0.5)
            }
            if (file.name == "real-cph2467-14.csv") {
                // Labelled from saved pictures: straight 3-24 s.
                assertTrue("14: nothing said while straight", rows.filter { it.tS in 3.0..24.0 }.none { it.reading?.issue != null })
            }
            if (file.name == "real-cph2467-15.csv") {
                // Labelled from saved pictures (not by a person); the lean block is the team's account.
                fun share(a: Double, b: Double, issue: PostureIssue): Double {
                    val block = rows.filter { it.tS in a..b }
                    return block.count { it.reading?.issue == issue } / block.size.toDouble()
                }
                // Straight blocks: the first 2.5 s after a bent block are the median and sustain lag.
                assertTrue("15: nothing said in straight 3.5-32 s", rows.filter { it.tS in 3.5..32.0 }.none { it.reading?.issue != null })
                assertTrue("15: nothing said in straight 34.5-37.3 s", rows.filter { it.tS in 34.5..37.3 }.none { it.reading?.issue != null })
                assertTrue("15: nothing said in straight 46.5-53.4 s", rows.filter { it.tS in 46.5..53.4 }.none { it.reading?.issue != null })
                val bent1 = share(38.2, 43.4, PostureIssue.ELBOWS_BENT)
                val bent2 = share(54.0, 61.5, PostureIssue.ELBOWS_BENT)
                val lean = share(65.0, 69.5, PostureIssue.SHOULDERS_NOT_OVER_HANDS)
                val leanFirst = rows.firstOrNull { it.tS >= 65.0 && it.reading?.issue == PostureIssue.SHOULDERS_NOT_OVER_HANDS }?.tS
                val bent2Good = rows.count { it.tS in 54.0..61.5 && it.reading?.good == true } / rows.count { it.tS in 54.0..61.5 }.toDouble()
                println(String.format(Locale.ROOT, "posture 15: bent block 38.2-43.4 s bent %.0f%%, bent block 54-61.5 s bent %.0f%% (called good %.0f%%), lean block 65-69.5 s lean %.0f%% first at %s s",
                    bent1 * 100, bent2 * 100, bent2Good * 100, lean * 100, leanFirst?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "never"))
                assertTrue("15: first bent block flagged bent for most of it", bent1 > 0.5)
                // The second bend is mild (elbow 144-166, median 151): only the personal baseline (178) lifts the threshold to it.
                assertTrue("15: second bent block flagged bent for at least half of it", bent2 >= 0.5)
                assertTrue("15: second bent block is not called good", bent2Good < 0.1)
                assertTrue("15: lean block flagged for a clear share", lean > 0.25)
                assertTrue("15: lean fires within 3.5 s of the lean block (measured 2.8 s: 1 s median plus 1 s sustain)", leanFirst != null && leanFirst - 65.0 <= 3.5)
            }
            if (file.name == "real-cph2467-11.csv") {
                assertTrue("11 (straight throughout) never says bent",
                    rows.none { it.reading?.issue == PostureIssue.ELBOWS_BENT })
            }
        }
        assertTrue("some recordings replayed", shares.isNotEmpty())
    }

    private companion object {
        const val ASPECT = 4f / 3f
    }
}
