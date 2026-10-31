package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Replays real phone recordings frame by frame through [LiveAnalyzer] and [CoachPolicy], reading every 250 ms
 * like the app does (a read is never earlier than the last frame added), and checks what would be spoken.
 */
class CoachReplayTest {
    private data class Spoken(val tS: Double, val phrase: Phrase)

    private fun replay(name: String): List<Spoken> {
        val lines = File(TestAssets.projectDir, "tools/testdata/$name").bufferedReader().use { it.readLines() }
        val header = lines.first().removePrefix("﻿").split(',')
        fun col(n: String) = header.indexOf(n)
        val frames = ArrayList<PoseFrame>()
        for (line in lines.drop(1).filter { it.isNotBlank() }) {
            val c = line.split(',')
            val t = c[0].trim().toLong()
            if (c[col("wrist_signal")].isBlank() || c[col("shoulder_x")].isBlank()) {
                frames.add(PoseFrame(t, null, null, 0))
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
                sideOn = c[col("side_on")].trim() != "0",
            )
            frames.add(PoseFrame(t, sample, arm, 0, ASPECT))
        }
        val t0 = frames.first().timeMs
        val analyzer = LiveAnalyzer()
        val coach = CoachPolicy()
        val spoken = ArrayList<Spoken>()
        var next = 0
        var tick = t0 + 250
        while (tick <= frames.last().timeMs) {
            while (next < frames.size && frames[next].timeMs <= tick) analyzer.addFrame(frames[next++])
            val dbg = analyzer.read(tick)
            coach.update(dbg)?.let { spoken.add(Spoken((tick - t0) / 1000.0, it)) }
            tick += 250
        }
        println("coach replay $name: " + spoken.joinToString { "%.1fs %s".format(it.tS, it.phrase) })
        return spoken
    }

    private fun List<Spoken>.keepGoingIn(from: Double, to: Double) = any { it.phrase == Phrase.KEEP_GOING && it.tS in from..to }

    @Test
    fun `recording 14 says keep going after the rescuer stops`() {
        val spoken = replay("real-cph2467-14.csv")
        assertTrue("keep going between 26.5 and 30 s", spoken.keepGoingIn(26.5, 30.0))
    }

    @Test
    fun `recording 15 never says keep going while still pushing`() {
        val spoken = replay("real-cph2467-15.csv")
        assertTrue("straight pushing 3.5-32 s", !spoken.keepGoingIn(3.5, 32.0))
        assertTrue("resumed pushing after reposition 34.5-37.3 s", !spoken.keepGoingIn(34.5, 37.3))
        assertTrue("bent pushing 38-44 s", !spoken.keepGoingIn(38.0, 44.0))
        assertTrue("bent pushing 54-62 s", !spoken.keepGoingIn(54.0, 62.0))
        assertTrue("lean pushing 64-70 s", !spoken.keepGoingIn(64.0, 70.0))
    }

    @Test
    fun `recording 13 never says keep going while pushing`() {
        val spoken = replay("real-cph2467-13.csv")
        assertTrue("pushing 4-36 s: $spoken", !spoken.keepGoingIn(4.0, 36.0))
        assertTrue("real stop still needs its cue: $spoken", spoken.keepGoingIn(39.5, 44.5))
    }

    @Test
    fun `recording 15 corrects the bent elbows while pushing`() {
        val spoken = replay("real-cph2467-15.csv")
        assertTrue("elbows straight in a bent block", spoken.any { it.phrase == Phrase.ELBOWS_STRAIGHT && it.tS in 38.0..62.0 })
    }

    @Test
    fun `recording 15 corrects the elbows in both bent blocks and still leans`() {
        val spoken = replay("real-cph2467-15.csv")
        assertTrue("elbows in first bent block 38-44 s", spoken.any { it.phrase == Phrase.ELBOWS_STRAIGHT && it.tS in 38.0..44.0 })
        assertTrue("elbows in second bent block 54-62 s", spoken.any { it.phrase == Phrase.ELBOWS_STRAIGHT && it.tS in 54.0..62.0 })
        assertTrue("shoulders over hands near 70 s", spoken.any { it.phrase == Phrase.SHOULDERS_OVER_HANDS && it.tS in 64.0..76.0 })
    }

    private companion object {
        const val ASPECT = 4f / 3f
    }
}
