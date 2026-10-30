package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Synthetic traces only: these checks are not evidence of real or clinical accuracy. */
class RateSlowCameraTest {
    @Test
    fun `twelve fps retains every settled beat despite jitter and missing observations`() {
        for (bpm in listOf(100, 110, 120, 130)) for (seed in seeds) {
            val settled = observe(bpm, seed).filter { it.timeMs >= 6_000 }
            assertTrue("nominal=$bpm seed=$seed: insufficient checkpoints", settled.size >= 20)
            // The unchanged robustness fixture exposes false NoBeat readings at 6799/7372ms
            // for 130/min, seed 7. Check every reading, not just successful detections.
            for (o in settled) {
                val label = "nominal=$bpm seed=$seed at ${o.timeMs}ms: ${o.reading}"
                assertTrue(label, o.reading is BeatReading.Beat)
                assertEquals(label, o.truth, (o.reading as BeatReading.Beat).perMinute, 5.0)
            }
            println("SYNTHETIC ONLY: nominal=$bpm seed=$seed: ${settled.size} raw beat checkpoints")
        }
    }

    @Test
    fun `stopping at twelve fps with random shake clears the old beat`() {
        for (seed in seeds) {
            val observations = observe(110, seed, stopMs = 8_000)
            for (o in observations.filter { it.timeMs in 6_000L..7_999L }) {
                assertTrue("seed=$seed before stop at ${o.timeMs}: ${o.reading}", o.reading is BeatReading.Beat)
            }
            // After 2.5 seconds, no checkpoint may keep or reacquire the old beat.
            for (o in observations.filter { it.timeMs >= 10_500 }) {
                assertEquals("seed=$seed after stop at ${o.timeMs}", BeatReading.NoBeat, o.reading)
            }
        }
    }

    @Test
    fun `pure random wobble at twelve fps is not a beat`() {
        for (seed in seeds) {
            for (o in observe(110, seed, stopMs = 0).filter { it.timeMs >= 6_000 }) {
                assertEquals("seed=$seed at ${o.timeMs}", BeatReading.NoBeat, o.reading)
            }
        }
    }

    @Test
    fun `too few irregular frames remains warming rather than aliasing`() {
        for (seed in seeds) {
            val settled = observe(110, seed, fps = 3).filter { it.timeMs >= 6_000 }
            assertTrue(settled.isNotEmpty())
            for (o in settled) {
                assertEquals("seed=$seed at ${o.timeMs}", BeatReading.Warming, o.reading)
            }
        }
    }

    private data class Observation(val timeMs: Long, val truth: Double, val reading: BeatReading)

    private fun observe(bpm: Int, seed: Long, fps: Int = 12, stopMs: Long = Long.MAX_VALUE): List<Observation> {
        val estimator = RateEstimator()
        val random = Random(seed)
        val observations = mutableListOf<Observation>()
        var ms = 0.0
        var nextReadMs = 0L
        while (ms <= 18_000) {
            val t = ms / 1000.0
            val timeMs = ms.toLong()
            val truth = bpm - 3.0 + 6.0 * t / 18.0
            val cycles = ((bpm - 3.0) * t + 3.0 * t * t / 18.0) / 60.0
            val phase = cycles - floor(cycles)
            val stroke = if (phase < 0.22) (1 - cos(PI * phase / 0.22)) / 2
                else (1 + cos(PI * (phase - 0.22) / 0.78)) / 2
            val depth = 0.16 + 0.045 * sin(2 * PI * t / 11.0)
            val commonShift = 0.05 * sin(2 * PI * 0.8 * t)
            val tilt = 0.14 * sin(2 * PI * t / 23.0)
            val hipY = 0.5 + commonShift
            val wristY = hipY + (1.0 - depth * stroke) * cos(tilt) + 0.3 * sin(tilt)
            val noise = random.nextGaussian()
            val signal = if (timeMs >= stopMs) 1.0 + 0.03 * noise
                else wristY - hipY + 0.008 * sin(2 * PI * 1.3 * t + 0.4) + 0.004 * noise
            val lowVisibility = random.nextDouble() < 0.035
            // Preserve RateRobustnessTest's exact random draw sequence, including its
            // invisible-landmark noise draw, but omit those samples instead of corrupting values.
            if (lowVisibility) random.nextGaussian()
            val dropped = random.nextDouble() < 0.06
            if (!lowVisibility && !dropped) estimator.add(timeMs, signal)
            if (timeMs >= nextReadMs) {
                observations += Observation(timeMs, truth, estimator.read())
                nextReadMs = timeMs + 500
            }
            ms += 1000.0 / fps * (0.8 + random.nextDouble() * 0.4)
        }
        return observations
    }

    private companion object {
        val seeds = listOf(7L, 42L, 2026L)
    }
}
