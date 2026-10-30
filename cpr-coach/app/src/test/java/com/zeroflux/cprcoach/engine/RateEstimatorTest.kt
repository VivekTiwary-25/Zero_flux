package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

class RateEstimatorTest {

    private fun feed(
        estimator: RateEstimator,
        perMinute: Double,
        seconds: Double = 8.0,
        fps: Double = 24.0,
        amplitude: Double = 0.08,
        noise: Double = 0.01,
        drift: Double = 0.02,
        jitterMs: Int = 8,
        wave: (Double) -> Double = { sin(2 * PI * it) },
    ) {
        val random = Random(42)
        val count = (seconds * fps).toInt()
        for (i in 0 until count) {
            val t = i / fps
            val timeMs = (t * 1000).toLong() + random.nextInt(jitterMs + 1)
            val value = 1.2 + drift * t + amplitude * wave(t * perMinute / 60) + noise * random.nextGaussian()
            estimator.add(timeMs, value)
        }
    }

    private fun rateOf(estimator: RateEstimator): Double {
        val reading = estimator.read()
        assertTrue("expected a beat, got $reading", reading is BeatReading.Beat)
        return (reading as BeatReading.Beat).perMinute
    }

    @Test
    fun `measures rates across the useful range`() {
        for (rate in listOf(70.0, 90.0, 100.0, 110.0, 120.0, 130.0, 150.0)) {
            val estimator = RateEstimator()
            feed(estimator, rate)
            assertEquals("at $rate/min", rate, rateOf(estimator), 3.0)
        }
    }

    @Test
    fun `survives a slow, uneven camera`() {
        val estimator = RateEstimator()
        feed(estimator, 110.0, fps = 12.0, jitterMs = 25)
        assertEquals(110.0, rateOf(estimator), 4.0)
    }

    @Test
    fun `does not halve the rate when every other compression is weaker`() {
        val estimator = RateEstimator()
        feed(estimator, 110.0, wave = { c ->
            val strength = if (c.toInt() % 2 == 0) 1.0 else 0.7
            strength * sin(2 * PI * c)
        })
        assertEquals(110.0, rateOf(estimator), 4.0)
    }

    @Test
    fun `says nothing until it has enough data`() {
        val estimator = RateEstimator()
        feed(estimator, 110.0, seconds = 2.0)
        assertEquals(BeatReading.Warming, estimator.read())
    }

    @Test
    fun `still arms are a pause, not a rate`() {
        val estimator = RateEstimator()
        feed(estimator, 110.0, amplitude = 0.0, noise = 0.002, drift = 0.0)
        assertEquals(BeatReading.NoBeat, estimator.read())
    }

    @Test
    fun `random hand wobble is not reported as a beat`() {
        val estimator = RateEstimator()
        feed(estimator, 110.0, amplitude = 0.0, noise = 0.03, drift = 0.0)
        assertEquals(BeatReading.NoBeat, estimator.read())
    }

    @Test
    fun `stopping with a shaky hand does not keep showing the old rate`() {
        for (seed in 1L..20L) {
            val estimator = RateEstimator()
            feed(estimator, 110.0)
            val random = Random(seed)
            for (i in 0 until 60) estimator.add(8_100L + i * 40, 1.36 + 0.03 * random.nextGaussian())
            assertEquals("seed $seed", BeatReading.NoBeat, estimator.read())
        }
    }

    @Test
    fun `does not halve a fast rate when alternate strokes are weaker`() {
        // Known limit: below about 60% depth on alternate strokes it reads half the rate. Check against real traces.
        val estimator = RateEstimator()
        feed(estimator, 130.0, wave = { c ->
            val strength = if (c.toInt() % 2 == 0) 1.0 else 0.6
            strength * sin(2 * PI * c)
        })
        assertEquals(130.0, rateOf(estimator), 4.0)
    }

    @Test
    fun `steady compressions with noise keep their rate after every seed`() {
        for (seed in 1L..20L) {
            val estimator = RateEstimator()
            val random = Random(seed)
            for (i in 0 until 240) {
                val t = i * 40L
                estimator.add(t, 1.2 + 0.08 * sin(2 * PI * t / 1000.0 * 110 / 60) + 0.015 * random.nextGaussian())
            }
            assertEquals("seed $seed", 110.0, rateOf(estimator), 4.0)
        }
    }

    @Test
    fun `does not double a slow rate with a recoil bounce`() {
        val estimator = RateEstimator()
        feed(estimator, 65.0, wave = { c ->
            val p = c - c.toInt()
            -kotlin.math.exp(-(((p - 0.5) / 0.08) * ((p - 0.5) / 0.08))) - 0.35 * kotlin.math.exp(-((p / 0.15) * (p / 0.15)))
        })
        assertEquals(65.0, rateOf(estimator), 5.0)
    }

    @Test
    fun `too few frames gives no rate rather than an aliased one`() {
        val estimator = RateEstimator()
        feed(estimator, 110.0, fps = 3.0, jitterMs = 0)
        assertEquals(BeatReading.Warming, estimator.read())
    }

    @Test
    fun `a pause shows up within two seconds`() {
        val estimator = RateEstimator()
        feed(estimator, 110.0)
        // The window still holds old compressions, but the arms have now stopped.
        for (i in 0 until 40) estimator.add(8_100L + i * 40, 1.36)
        assertEquals(BeatReading.NoBeat, estimator.read())
    }
}
