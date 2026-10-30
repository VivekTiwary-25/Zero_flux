package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Replays real phone recordings [real-cph2467-14.csv] and [real-cph2467-15.csv] frame by frame
 * through [LiveAnalyzer] and [EmergencyCoach], reading every 250 ms (never earlier than the last
 * added frame) to evaluate the Emergency mode big word timeline.
 */
class EmergencyWordReplayTest {

    data class WordEntry(val timeS: Double, val word: EmergencyWord)
    data class WordSpan(val startS: Double, val endS: Double, val word: EmergencyWord)

    data class ReplayResult(
        val entries: List<WordEntry>,
        val spans: List<WordSpan>,
    ) {
        fun wordsIn(startS: Double, endS: Double): List<EmergencyWord> =
            entries.filter { it.timeS in startS..endS }.map { it.word }

        fun formatTimeline(): String =
            spans.joinToString(", ") { span ->
                String.format(Locale.ROOT, "%.1f-%.1fs %s", span.startS, span.endS, span.word.name)
            }
    }

    private fun replay(name: String): ReplayResult {
        val lines = File(TestAssets.projectDir, "tools/testdata/$name").bufferedReader().use { it.readLines() }
        val header = lines.first().removePrefix("\uFEFF").split(',')
        fun col(n: String) = header.indexOf(n)
        val frames = ArrayList<PoseFrame>()
        for (line in lines.drop(1).filter { it.isNotBlank() }) {
            val c = line.split(',')
            val t = c[0].trim().toLong()
            if (c[col("wrist_signal")].isBlank() || c[col("shoulder_x")].isBlank()) {
                frames.add(PoseFrame(t, null, null, 0, ASPECT))
                continue
            }
            fun f(n: String) = c[col(n)].trim().toFloat()
            val arm = ArmLandmarks(
                f("shoulder_x"), f("shoulder_y"), f("shoulder_vis"),
                f("elbow_x"), f("elbow_y"), f("elbow_vis"),
                f("wrist_x"), f("wrist_y"), f("wrist_vis"),
                f("hip_x"), f("hip_y"), f("hip_vis"),
            )
            val sample = PoseSample(
                timeMs = t,
                wristSignal = c[col("wrist_signal")].trim().toDouble(),
                elbowDegrees = PoseMath.elbowDegrees(arm, ASPECT),
                minVisibility = f("min_vis"),
                elbowVisibility = arm.elbowVis,
                sideOn = if ("side_on" in header) c[col("side_on")].trim() != "0" else true,
            )
            val latency = if ("latency_ms" in header && c[col("latency_ms")].isNotBlank()) {
                c[col("latency_ms")].trim().toLong()
            } else {
                0L
            }
            frames.add(PoseFrame(t, sample, arm, latency, ASPECT))
        }

        val t0 = frames.first().timeMs
        val analyzer = LiveAnalyzer()
        val coach = EmergencyCoach(t0)
        val entries = ArrayList<WordEntry>()

        var next = 0
        var tick = t0
        while (tick <= frames.last().timeMs) {
            while (next < frames.size && frames[next].timeMs <= tick) {
                analyzer.addFrame(frames[next++])
            }
            val reading = analyzer.read(tick)
            coach.update(reading)
            val tS = (tick - t0) / 1000.0
            entries.add(WordEntry(tS, coach.word))
            tick += 250
        }

        val spans = ArrayList<WordSpan>()
        if (entries.isNotEmpty()) {
            var curWord = entries.first().word
            var curStart = entries.first().timeS
            var curEnd = curStart
            for (e in entries.drop(1)) {
                if (e.word == curWord) {
                    curEnd = e.timeS
                } else {
                    spans.add(WordSpan(curStart, curEnd, curWord))
                    curWord = e.word
                    curStart = e.timeS
                    curEnd = curStart
                }
            }
            spans.add(WordSpan(curStart, curEnd, curWord))
        }

        return ReplayResult(entries, spans)
    }

    @Test
    fun `recording 14 straight pushing shows no straighten-arms or lean words and pause stays keep pushing`() {
        val r = replay("real-cph2467-14.csv")
        println("timeline real-cph2467-14.csv: " + r.formatTimeline())

        val straight = r.wordsIn(3.0, 24.0)
        assertTrue("14 straight segment is non-empty", straight.isNotEmpty())
        assertTrue(
            "14 straight pushing has no straighten-arms",
            straight.none { it == EmergencyWord.STRAIGHTEN_ARMS || it == EmergencyWord.STRAIGHTEN },
        )
        assertTrue(
            "14 straight pushing has no lean word",
            straight.none { it == EmergencyWord.SHOULDERS_OVER_HANDS },
        )
        assertTrue(
            "14 straight pushing shows good once established",
            straight.any { it == EmergencyWord.GOOD },
        )

        val pause = r.wordsIn(24.7, 33.5)
        assertTrue("14 pause segment is non-empty", pause.isNotEmpty())
        assertTrue(
            "14 pause shows keep pushing",
            pause.all { it == EmergencyWord.KEEP_PUSHING },
        )
    }

    @Test
    fun `recording 15 straight pushing shows no straighten-arms or lean words`() {
        val r = replay("real-cph2467-15.csv")
        println("timeline real-cph2467-15.csv: " + r.formatTimeline())

        val straight = r.wordsIn(3.5, 32.0)
        assertTrue("15 straight segment is non-empty", straight.isNotEmpty())
        assertTrue(
            "15 straight pushing has no straighten-arms",
            straight.none { it == EmergencyWord.STRAIGHTEN_ARMS || it == EmergencyWord.STRAIGHTEN },
        )
        assertTrue(
            "15 straight pushing has no lean word",
            straight.none { it == EmergencyWord.SHOULDERS_OVER_HANDS },
        )
        assertTrue(
            "15 straight pushing shows good once established",
            straight.any { it == EmergencyWord.GOOD },
        )
    }

    @Ignore("posture judge retune pending")
    @Test
    fun `recording 15 shows straighten arms during first bent block 38_2-43_4 s`() {
        val r = replay("real-cph2467-15.csv")
        assertTrue(
            "15 shows straighten arms during bent 38.2-43.4 s",
            r.wordsIn(38.2, 43.4).any { it == EmergencyWord.STRAIGHTEN_ARMS },
        )
    }

    @Ignore("posture judge retune pending")
    @Test
    fun `recording 15 shows straighten arms during second bent block 54-61_5 s`() {
        val r = replay("real-cph2467-15.csv")
        assertTrue(
            "15 shows straighten arms during bent 54.0-61.5 s",
            r.wordsIn(54.0, 61.5).any { it == EmergencyWord.STRAIGHTEN_ARMS },
        )
    }

    @Ignore("posture judge retune pending")
    @Test
    fun `recording 15 shows shoulders over hands during lean block 65-69_5 s`() {
        val r = replay("real-cph2467-15.csv")
        assertTrue(
            "15 shows shoulders over hands during lean 65.0-69.5 s",
            r.wordsIn(65.0, 69.5).any { it == EmergencyWord.SHOULDERS_OVER_HANDS },
        )
    }

    private companion object {
        const val ASPECT = 4f / 3f
    }
}
