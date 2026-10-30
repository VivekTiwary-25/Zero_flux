package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/** Synthetic side-on frames cover both signed lean directions and camera facings. */
class LeanDirectionTest {
    private companion object {
        const val FPS = 30
        const val ASPECT = 4f / 3f
        const val BASELINE_END_MS = 8_000L
        const val BACK_START_MS = 8_000L
        const val BACK_END_MS = 14_000L
        const val STRAIGHT_START_MS = 14_000L
        const val FORWARD_START_MS = 20_000L
        const val FORWARD_END_MS = 26_000L
        const val FINAL_STRAIGHT_START_MS = 26_000L
        const val END_MS = 32_000L
        const val ARM_LENGTH = 0.4
        const val LEAN_DISTANCE = 0.12 // 30% of the two-segment arm length.
    }

    private data class TimedReading(val timeMs: Long, val reading: PostureReading)

    private fun run(mirror: Boolean): List<TimedReading> {
        val judge = PostureJudge()
        val rng = Random(if (mirror) 82L else 81L)
        val frames = ArrayList<TimedReading>()
        val count = END_MS * FPS / 1_000
        for (frame in 0..count) {
            val t = frame * 1_000L / FPS
            val seconds = t / 1_000.0
            val compression = sin(2 * PI * (110.0 / 60.0) * seconds)
            val wristY = 0.68 + 0.012 * compression + 0.0015 * rng.nextGaussian()
            val elbowAngle = Math.toRadians(168.0 + 4.0 * compression + 1.0 * rng.nextGaussian())
            val elbowX = 0.60 - 0.2 * sin(PI - elbowAngle)
            val elbowY = 0.48
            val wristX = 0.60 + 0.0015 * rng.nextGaussian()

            val leanOffset = when (t) {
                in BACK_START_MS until BACK_END_MS -> LEAN_DISTANCE
                in FORWARD_START_MS until FORWARD_END_MS -> -LEAN_DISTANCE
                else -> 0.0
            }
            val shoulderX = wristX - leanOffset / ARM_LENGTH + 0.0015 * rng.nextGaussian()
            val shoulderY = 0.28 + 0.0015 * rng.nextGaussian()
            val hipX = if (mirror) 0.78 else 0.42
            val hipY = 0.72
            fun x(value: Double) = (if (mirror) 1.0 - value else value).toFloat()
            val arm = ArmLandmarks(
                x(shoulderX), shoulderY.toFloat(), 0.96f,
                x(elbowX), (elbowY + 0.0015 * rng.nextGaussian()).toFloat(), 0.96f,
                x(wristX), wristY.toFloat(), 0.96f,
                hipX.toFloat(), hipY.toFloat(), 0.96f,
            )
            val sample = PoseSample(t, (hipY - wristY) / 0.4, 168.0 + 4.0 * compression + rng.nextGaussian(), 0.96f)
            frames += TimedReading(t, judge.update(sample, arm, ASPECT))
        }
        return frames
    }

    private fun firstIssue(frames: List<TimedReading>, fromMs: Long, issue: PostureIssue) =
        frames.firstOrNull { it.timeMs >= fromMs && it.reading.issue == issue }?.timeMs

    private fun firstClear(frames: List<TimedReading>, fromMs: Long) =
        frames.firstOrNull { it.timeMs >= fromMs && it.reading.issue == null }?.timeMs

    private fun assertLeanAndClear(frames: List<TimedReading>, onset: Long, end: Long, name: String) {
        val firedAt = firstIssue(frames, onset, PostureIssue.SHOULDERS_NOT_OVER_HANDS)
        assertTrue("$name did not flag within 3 s", firedAt != null && firedAt - onset <= 3_000L)
        val clearedAt = firstClear(frames, end)
        assertTrue("$name did not clear within 3 s", clearedAt != null && clearedAt - end <= 3_000L)
        val shareFlagged = frames.count { it.timeMs in onset until end && it.reading.issue == PostureIssue.SHOULDERS_NOT_OVER_HANDS }.toDouble() /
            frames.count { it.timeMs in onset until end }
        println("$name: flag after ${firedAt!! - onset}ms, clear after ${clearedAt!! - end}ms, share flagged ${"%.1f".format(100 * shareFlagged)}%")
        assertTrue("$name flagged only ${100 * shareFlagged}%", shareFlagged >= 0.5)
    }

    @Test fun bothLeanDirectionsFlagAndClearForBothFacings() {
        for (mirror in listOf(false, true)) {
            val frames = run(mirror)
            val facing = if (mirror) "mirrored" else "original"
            assertTrue("$facing baseline should be learned", frames.any { it.timeMs >= BASELINE_END_MS && it.reading.good != null })
            assertTrue("$facing initial straight stretch should stay quiet", frames.filter { it.timeMs < BACK_START_MS }.all { it.reading.issue == null })

            assertLeanAndClear(frames, BACK_START_MS, BACK_END_MS, "$facing lean back")
            assertTrue("$facing straight interval should be quiet after clearing", frames.filter { it.timeMs in STRAIGHT_START_MS + 3_000L until FORWARD_START_MS }.all { it.reading.issue == null })
            assertLeanAndClear(frames, FORWARD_START_MS, FORWARD_END_MS, "$facing lean forward")
            assertTrue("$facing final straight stretch should be quiet after clearing", frames.filter { it.timeMs in FINAL_STRAIGHT_START_MS + 3_000L..END_MS }.all { it.reading.issue == null })
        }
    }
}
