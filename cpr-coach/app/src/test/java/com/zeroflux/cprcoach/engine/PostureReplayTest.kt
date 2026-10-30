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

    private fun replay(file: File): List<Row>? {
        val lines = file.bufferedReader().use { it.readLines() }
        val header = lines.first().removePrefix("﻿").split(',')
        if ("shoulder_x" !in header) return null
        fun col(n: String) = header.indexOf(n)
        val judge = PostureJudge()
        val rows = ArrayList<Row>()
        var t0: Long? = null
        for (line in lines.drop(1).filter { it.isNotBlank() }) {
            val c = line.split(',')
            val t = c[0].trim().toLong()
            val start = t0 ?: t.also { t0 = it }
            val tS = (t - start) / 1000.0
            if (c[col("wrist_signal")].isBlank() || c[col("shoulder_x")].isBlank()) {
                rows.add(Row(tS, null))
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
            val corrected = if (arm != null) sample.copy(elbowDegrees = PoseMath.elbowDegrees(arm, ASPECT)) else sample
            rows.add(Row(tS, judge.update(corrected, arm, ASPECT)))
        }
        return rows
    }

    @Test
    fun `replay all recordings and write per frame verdicts`() {
        val dir = File(TestAssets.projectDir, "tools/testdata")
        val out = File(TestAssets.projectDir, "app/build/posture_replay").also { it.mkdirs() }
        val shares = HashMap<String, Triple<Double, Double, Double>>()
        for (file in dir.listFiles { f -> f.name.endsWith(".csv") && f.name != "labels.csv" }!!.sortedBy { it.name }) {
            val rows = replay(file) ?: continue
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
            println(String.format(Locale.ROOT, "posture %s: good=%.0f%% issue=%.0f%% (bent %.0f%%, lean %.0f%%) null=%.0f%%",
                file.name, good * 100, issue * 100, bent * 100, lean * 100, (1 - good - issue) * 100))
            if (file.name == "real-cph2467-12.csv") {
                // The team-unconfirmed bent block is at about 18-29 s; normal pushing before it must stay quiet.
                val early = rows.filter { it.tS < 15.0 }
                assertTrue("12: quiet while pushing normally before 15 s", early.none { it.reading?.issue != null })
                val bentBlock = rows.filter { it.tS in 21.0..30.0 }
                val bentShare = bentBlock.count { it.reading?.issue == PostureIssue.ELBOWS_BENT } / bentBlock.size.toDouble()
                println(String.format(Locale.ROOT, "posture 12 bent block 21-30 s flagged bent: %.0f%%", bentShare * 100))
                assertTrue("12: the bent block is flagged for most of 21-30 s", bentShare > 0.5)
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
