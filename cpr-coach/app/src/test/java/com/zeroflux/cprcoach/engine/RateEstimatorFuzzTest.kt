package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * Real-person motion crashed the app on a phone: when every correlation peak was negative, pickLag
 * found no match. Messy signals must give a reading, never an exception.
 */
class RateEstimatorFuzzTest {

    private fun randomSignal(random: Random, estimator: RateEstimator) {
        val fps = 4.0 + random.nextDouble() * 28
        val seconds = 3.0 + random.nextDouble() * 6
        val perMinute = random.nextDouble() * 400
        val amplitude = random.nextDouble() * 0.3
        val noise = random.nextDouble() * 0.3
        val square = random.nextBoolean()
        val stutter = random.nextDouble() * 0.5
        var t = 0.0
        while (t < seconds) {
            t += (1 / fps) * (1 + random.nextDouble() * 2 * stutter)
            val wave = sin(2 * PI * t * perMinute / 60)
            val shaped = if (square) (if (wave > 0) 1.0 else -1.0) else wave
            estimator.add((t * 1000).toLong(), amplitude * shaped + noise * random.nextGaussian())
        }
    }

    @Test
    fun `messy motion never throws and only reports rates in range`() {
        for (seed in 1L..4000L) {
            val estimator = RateEstimator()
            val random = Random(seed)
            randomSignal(random, estimator)
            val reading = try {
                estimator.read()
            } catch (e: RuntimeException) {
                throw AssertionError("seed $seed threw $e", e)
            }
            if (reading is BeatReading.Beat) {
                assertTrue("seed $seed gave $reading", reading.perMinute in 50.0..190.0)
            }
        }
    }

    @Test
    fun `analyzer survives messy motion, dropouts and repeated reads`() {
        for (seed in 1L..800L) {
            val random = Random(seed)
            val analyzer = LiveAnalyzer()
            var t = 0L
            repeat(300) {
                t += 20 + random.nextInt(200)
                val visible = random.nextDouble() > 0.1
                analyzer.addFrame(
                    if (random.nextDouble() < 0.05) null
                    else PoseSample(t, random.nextGaussian() * 0.3, 60 + random.nextDouble() * 120, if (visible) 0.9f else 0.1f),
                )
                repeat(random.nextInt(3)) { analyzer.read(t + random.nextInt(800)) }
            }
        }
    }
}
