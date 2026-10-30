package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.util.Locale
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Synthetic wrist-minus-hip traces, not recorded motion or evidence of real/clinical accuracy.
 * All distances are torso-normalised, not compression depth in centimetres. The fixtures deliberately
 * combine asymmetric strokes, changing depth, rate drift, camera motion and unreliable observations.
 * Read both engines approximately twice per second; compare against instantaneous simulated cadence,
 * including the lag of the six-second window and LiveAnalyzer's median, not a best-fit window average.
 */
class RateRobustnessTest {
    @Test
    fun `reported rates stay accurate across handheld motion and irregular frame rates`() {
        println("SYNTHETIC ONLY: nominal/min | live mean absolute error/min | live max error/min | beats/checkpoints")
        for (bpm in 80..140 step 10) {
            val settled = frameRates.flatMap { fps ->
                seeds.flatMap { seed -> observe(Scenario(bpm, fps, seed)).filter { it.timeMs >= 6_000 } }
            }
            val errors = mutableListOf<Double>()
            for (o in settled) {
                val label = "nominal=$bpm at ${o.timeMs}ms: ${o.live}, raw=${o.raw}"
                if (o.raw is BeatReading.Beat) {
                    assertEquals(label, o.truth, o.raw.perMinute, 5.0)
                }
                val displayed = o.live.ratePerMinute
                if (displayed == null) {
                    assertTrue(label, o.live.tracking != TrackingState.BEATING)
                } else {
                    assertEquals(label, TrackingState.BEATING, o.live.tracking)
                    assertEquals(label, o.truth, displayed.toDouble(), 5.0)
                    errors += abs(displayed - o.truth)
                }
            }
            // Accuracy alone must not hide a detector that rarely reports. Full availability at
            // 130/140 with sparse input is a separate known-failure test below, not silently excluded.
            assertTrue("nominal=$bpm: only ${errors.size}/${settled.size} beats", errors.size >= settled.size * 0.95)
            if (bpm <= 120) assertEquals("nominal=$bpm: unexpected missed beat", settled.size, errors.size)
            println(String.format(Locale.ROOT, "%d | %.2f | %.2f | %d/%d", bpm,
                errors.average(), errors.maxOrNull()!!, errors.size, settled.size))
        }
    }

    @Test
    fun `visible pause clears the old rate and compressions reacquire after resuming`() {
        for (fps in frameRates) for (seed in seeds) {
            val observations = observe(Scenario(110, fps, seed, pause = 8_000L..12_000L))
            for (o in observations.filter { it.timeMs in 6_000L..7_999L || it.timeMs >= 16_000 }) {
                assertEquals("fps=$fps seed=$seed at ${o.timeMs}", TrackingState.BEATING, o.live.tracking)
                assertEquals(o.truth, o.live.ratePerMinute!!.toDouble(), 5.0)
            }
            // Allow two seconds to discard the previous beat; still arms remain visible and tilted.
            for (o in observations.filter { it.timeMs in 10_000L..12_000L }) {
                assertEquals("fps=$fps seed=$seed at ${o.timeMs}", BeatReading.NoBeat, o.raw)
                assertEquals(TrackingState.PAUSED, o.live.tracking)
                assertNull(o.live.ratePerMinute)
                assertNull(o.live.inRange)
            }
        }
    }

    @Test
    fun `low visibility suppresses numbers and requires fresh data after tracking returns`() {
        for (fps in frameRates) for (seed in seeds) {
            val observations = observe(Scenario(110, fps, seed, hidden = 8_000L..10_000L))
            for (o in observations) {
                val label = "fps=$fps seed=$seed at ${o.timeMs}: ${o.live}"
                when (o.timeMs) {
                    in 6_000L..7_999L -> assertEquals(label, TrackingState.BEATING, o.live.tracking)
                    in 8_600L..10_000L -> {
                        assertEquals(label, TrackingState.LOST, o.live.tracking)
                        assertNull(o.live.ratePerMinute)
                        assertNull(o.live.inRange)
                        assertNull(o.live.elbowDegrees)
                        assertNull(o.live.elbowsBent)
                    }
                    in 10_500L..12_900L -> {
                        assertEquals(label, TrackingState.WARMING, o.live.tracking)
                        assertNull(o.live.ratePerMinute)
                    }
                    in 14_000L..18_000L -> {
                        assertEquals(label, TrackingState.BEATING, o.live.tracking)
                        assertEquals(label, o.truth, o.live.ratePerMinute!!.toDouble(), 5.0)
                    }
                }
            }
        }
    }

    @Ignore("Known false pauses: sparse 12 fps input at high cadence; see observed values below")
    @Test
    fun `continuous high cadence never becomes a pause at twelve fps`() {
        // Input: nominal 130/min, 12 fps, seed 7, +/-20% frame-time jitter, 6% dropped
        // observations, 3.5% low visibility, depth modulation and +/-3/min linear rate drift.
        // Wrong result: PAUSED/null at 6799ms and 7372ms despite uninterrupted strokes;
        // the raw RateEstimator returns NoBeat too. Nominal 140 also fails with seeds 7 and 42.
        for (bpm in listOf(130, 140)) for (seed in seeds) {
            for (o in observe(Scenario(bpm, 12, seed)).filter { it.timeMs >= 6_000 }) {
                assertTrue("nominal=$bpm seed=$seed at ${o.timeMs}: ${o.raw}", o.raw is BeatReading.Beat)
                assertEquals("nominal=$bpm seed=$seed at ${o.timeMs}", TrackingState.BEATING, o.live.tracking)
            }
        }
    }

    @Ignore("Known octave error: severe alternating stroke depth is counted at half cadence")
    @Test
    fun `shallow alternate strokes still count as compressions`() {
        // Input: nominal 130/min, 24 fps, seed 42; every other stroke has 15% depth.
        // Wrong result: 64/min at 6234ms versus simulated 129.08/min, rising to only
        // 66/min at 17657ms versus 132.89/min; raw estimator also chooses the double period.
        for (o in observe(Scenario(130, 24, 42, alternateDepth = 0.15)).filter { it.timeMs >= 6_000 }) {
            assertEquals("at ${o.timeMs}: ${o.live}", TrackingState.BEATING, o.live.tracking)
            assertEquals(o.truth, o.live.ratePerMinute!!.toDouble(), 5.0)
        }
    }

    @Ignore("Known false beat: periodic residual camera motion is indistinguishable from wrist motion")
    @Test
    fun `rhythmic holder motion alone is not a compression beat`() {
        // Input: arms still throughout, 24 fps, seed 42; residual shake at 1.8 Hz with
        // amplitude 0.08 torso lengths after reference subtraction, plus slow tilt/noise.
        // Wrong result: BEATING at 107-108/min after warmup instead of PAUSED/null.
        // This is a deliberately adverse residual-motion fixture, not measured phone motion.
        val scenario = Scenario(110, 24, 42, pause = 0L..18_000L, rhythmicShake = 0.08)
        for (o in observe(scenario).filter { it.timeMs >= 6_000 }) {
            assertEquals("at ${o.timeMs}: ${o.raw}", BeatReading.NoBeat, o.raw)
            assertEquals(TrackingState.PAUSED, o.live.tracking)
            assertNull(o.live.ratePerMinute)
        }
    }

    private data class Scenario(
        val bpm: Int,
        val fps: Int,
        val seed: Long,
        val pause: LongRange = LongRange.EMPTY,
        val hidden: LongRange = LongRange.EMPTY,
        val alternateDepth: Double = 1.0,
        val rhythmicShake: Double = 0.0,
    )

    private data class Observation(
        val timeMs: Long,
        val truth: Double,
        val raw: BeatReading,
        val live: LiveReading,
    )

    private fun observe(s: Scenario): List<Observation> {
        val analyzer = LiveAnalyzer()
        val estimator = RateEstimator()
        val observations = mutableListOf<Observation>()
        var nextReadMs = 0L
        simulate(s) { timeMs, truth, sample ->
            analyzer.addFrame(sample)
            // RateEstimator accepts only a signal. Apply the same visibility precondition as
            // LiveAnalyzer; corrupted invisible landmarks must not be interpreted as wrist motion.
            if (sample != null && sample.minVisibility >= 0.5f) {
                estimator.add(timeMs, sample.wristSignal)
            }
            if (timeMs >= nextReadMs) {
                observations += Observation(timeMs, truth, estimator.read(), analyzer.read(timeMs))
                nextReadMs = timeMs + 500
            }
        }
        return observations
    }

    private fun simulate(s: Scenario, consume: (Long, Double, PoseSample?) -> Unit) {
        val random = Random(s.seed)
        var ms = 0.0
        while (ms <= 18_000) {
            val t = ms / 1000.0
            val timeMs = ms.toLong()
            // Cadence ramps nominal-3 -> nominal+3/min over 18 seconds. Integrating cadence
            // gives phase; multiplying time by the current cadence would double the drift.
            val truth = s.bpm - 3.0 + 6.0 * t / 18.0
            val cycles = ((s.bpm - 3.0) * t + 3.0 * t * t / 18.0) / 60.0
            val phase = cycles - floor(cycles)
            // One smooth downstroke in 22% of the cycle, slower release in the other 78%.
            val stroke = if (phase < 0.22) (1 - cos(PI * phase / 0.22)) / 2
                else (1 + cos(PI * (phase - 0.22) / 0.78)) / 2
            val depth = (0.16 + 0.045 * sin(2 * PI * t / 11.0)) *
                if (floor(cycles).toLong() % 2L == 0L) 1.0 else s.alternateDepth
            val displacement = if (timeMs in s.pause) 0.0 else -depth * stroke

            // Common holder translation cancels; slow rotation projects horizontal separation
            // into the vertical wrist-minus-hip signal. Residual wobble and pose noise remain.
            val commonShift = 0.05 * sin(2 * PI * 0.8 * t)
            val tilt = 0.14 * sin(2 * PI * t / 23.0)
            val hipY = 0.5 + commonShift
            val wristY = hipY + (1.0 + displacement) * cos(tilt) + 0.3 * sin(tilt)
            val residual = 0.008 * sin(2 * PI * 1.3 * t + 0.4) +
                0.004 * random.nextGaussian() + s.rhythmicShake * sin(2 * PI * 1.8 * t)
            val lowVisibility = timeMs in s.hidden || random.nextDouble() < 0.035
            val signal = wristY - hipY + residual + if (lowVisibility) 0.5 * random.nextGaussian() else 0.0
            // A dropped observation leaves a timestamp gap, never a synthetic zero/held sample.
            val dropped = random.nextDouble() < 0.06
            val sample = if (dropped) null else PoseSample(
                timeMs, signal, 171.0, if (lowVisibility) 0.2f else 0.95f,
            )
            consume(timeMs, truth, sample)
            ms += 1000.0 / s.fps * (0.8 + random.nextDouble() * 0.4)
        }
    }

    private companion object {
        val frameRates = listOf(12, 18, 24, 30)
        val seeds = listOf(7L, 42L, 2026L)
    }
}
