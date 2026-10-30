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
    fun `a pause shows up within two seconds`() {
        val estimator = RateEstimator()
        feed(estimator, 110.0)
        // The window still holds old compressions, but the arms have now stopped.
        for (i in 0 until 40) estimator.add(8_100L + i * 40, 1.36)
        assertEquals(BeatReading.NoBeat, estimator.read())
    }
}
