package com.zeroflux.cprcoach.engine

import kotlin.math.abs
import kotlin.math.sqrt

/** One pose reading. [wristSignal] is wrist height above the hip, in torso lengths. */
data class PoseSample(
    val timeMs: Long,
    val wristSignal: Double,
    val elbowDegrees: Double,
    val minVisibility: Float,
    /** How well the elbow itself is seen. Elbow feedback needs more than the arm being in frame. */
    val elbowVisibility: Float = minVisibility,
    /** False when the shoulders show the rescuer is not side-on, so a 2D elbow angle can't be trusted. */
    val sideOn: Boolean = true,
)

sealed interface BeatReading {
    /** Not enough recent data to say anything yet. */
    data object Warming : BeatReading

    /** Arms are visible but there is no steady compression beat. */
    data object NoBeat : BeatReading

    data class Beat(val perMinute: Double, val confidence: Double) : BeatReading
}

/**
 * Finds the compression rate from the wrist signal.
 *
 * Autocorrelation over a sliding window gives the rate. Timestamps are real frame times, so an uneven
 * camera frame rate does not shift the result. The most recent stretch must also match the beat, because
 * the window still holds old compressions for a few seconds after the rescuer stops.
 *
 * Thresholds here are starting values, to be tuned from recorded traces (see docs/MEASUREMENTS.md).
 */
class RateEstimator(
    private val windowMs: Long = 6_000,
    private val minDataMs: Long = 3_000,
    private val minCorrelation: Double = 0.35,
    private val minAmplitude: Double = 0.015,
    private val stillWindowMs: Long = 1_500,
) {
    private val times = ArrayDeque<Long>()
    private val values = ArrayDeque<Double>()

    fun add(timeMs: Long, signal: Double) {
        if (times.isNotEmpty() && timeMs <= times.last()) return
        times.addLast(timeMs)
        values.addLast(signal)
        while (times.isNotEmpty() && timeMs - times.first() > windowMs) {
            times.removeFirst()
            values.removeFirst()
        }
    }

    fun reset() {
        times.clear()
        values.clear()
    }

    fun read(): BeatReading {
        if (times.size < 2 || times.last() - times.first() < minDataMs) return BeatReading.Warming
        // Too few frames can alias into a believable but wrong rate.
        if (times.size * 1000 / (times.last() - times.first()) < MIN_FPS) return BeatReading.Warming
        val samples = detrend(resample())
        if (isStill(samples)) return BeatReading.NoBeat

        val correlation = autocorrelation(samples)
        val lag = pickLag(correlation) ?: return BeatReading.NoBeat
        val strength = correlation[lag]
        if (strength < minCorrelation) return BeatReading.NoBeat
        if (recentCorrelation(samples, lag) < MIN_RECENT_CORRELATION) return BeatReading.NoBeat

        return BeatReading.Beat(60.0 * SAMPLE_HZ / refineLag(correlation, lag), strength)
    }

    private fun resample(): DoubleArray {
        val start = times.first()
        val count = ((times.last() - start) * SAMPLE_HZ / 1000).toInt() + 1
        val out = DoubleArray(count)
        var j = 0
        for (i in 0 until count) {
            val t = start + i * 1000.0 / SAMPLE_HZ
            while (j < times.size - 2 && times[j + 1] < t) j++
            val t0 = times[j].toDouble()
            val t1 = times[j + 1].toDouble()
            val f = if (t1 > t0) ((t - t0) / (t1 - t0)).coerceIn(0.0, 1.0) else 0.0
            out[i] = values[j] + (values[j + 1] - values[j]) * f
        }
        return out
    }

    /** Removes slow drift (the holder's hand, the rescuer shifting) with a one-second moving average. */
    private fun detrend(x: DoubleArray): DoubleArray {
        val half = SAMPLE_HZ / 2
        val prefix = DoubleArray(x.size + 1)
        for (i in x.indices) prefix[i + 1] = prefix[i] + x[i]
        return DoubleArray(x.size) { i ->
            val a = (i - half).coerceAtLeast(0)
            val b = (i + half + 1).coerceAtMost(x.size)
            x[i] - (prefix[b] - prefix[a]) / (b - a)
        }
    }

    private fun isStill(x: DoubleArray): Boolean {
        val recent = (stillWindowMs * SAMPLE_HZ / 1000).toInt().coerceAtMost(x.size)
        var sum = 0.0
        for (i in x.size - recent until x.size) sum += x[i] * x[i]
        return sqrt(sum / recent) < minAmplitude
    }

    /**
     * Pool matches over nearby whole cycles, rather than only adjacent cycles.
     * Sparse frames can distort a single cycle; noise after a stop still has to match several cycles.
     * Every pair includes a recent sample, so older compressions cannot extend the pause window.
     */
    private fun recentCorrelation(x: DoubleArray, lag: Int): Double {
        val recent = (stillWindowMs * SAMPLE_HZ / 1000).toInt()
        val start = x.size - recent
        if (start - lag < 0) return 1.0
        var cross = 0.0
        var a = 0.0
        var b = 0.0
        for (i in start until x.size) a += x[i] * x[i]
        // Bound the reference to four cycles to limit accumulated whole-sample lag error.
        val maxOffset = minOf(start, 4 * lag)
        for (offset in lag..maxOffset step lag) {
            for (i in start until x.size) {
                cross += x[i] * x[i - offset]
                b += x[i - offset] * x[i - offset]
            }
        }
        val denominator = sqrt(a * (maxOffset / lag) * b)
        return if (denominator > 1e-9) cross / denominator else 0.0
    }

    /** Normalised autocorrelation, indexed by lag in samples. Lags outside the search range are 0. */
    private fun autocorrelation(x: DoubleArray): DoubleArray {
        val maxLag = minOf(MAX_LAG + 1, x.size - 1)
        val r = DoubleArray(maxLag + 1)
        for (lag in (MIN_LAG - 1)..maxLag) {
            if (lag < 0) continue
            var cross = 0.0
            var a = 0.0
            var b = 0.0
            for (i in 0 until x.size - lag) {
                cross += x[i] * x[i + lag]
                a += x[i] * x[i]
                b += x[i + lag] * x[i + lag]
            }
            val denominator = sqrt(a * b)
            r[lag] = if (denominator > 1e-9) cross / denominator else 0.0
        }
        return r
    }

    /** The shortest lag that is nearly as strong as the best one, since multiples of a period also score high. */
    private fun pickLag(r: DoubleArray): Int? {
        val peaks = (MIN_LAG..minOf(MAX_LAG, r.size - 2)).filter { r[it] > r[it - 1] && r[it] >= r[it + 1] }
        val best = peaks.maxOfOrNull { r[it] } ?: return null
        // A negative best means no beat at all. Scaling it would also raise the bar above every peak.
        if (best <= 0.0) return null
        return peaks.first { r[it] >= best * OCTAVE_PREFERENCE }
    }

    /** Parabolic interpolation: at 30 samples/s a whole-sample lag is only good to about 3%. */
    private fun refineLag(r: DoubleArray, lag: Int): Double {
        val a = r[lag - 1]
        val b = r[lag]
        val c = r[lag + 1]
        val denominator = a - 2 * b + c
        return if (abs(denominator) < 1e-9) lag.toDouble() else lag + 0.5 * (a - c) / denominator
    }

    private companion object {
        const val SAMPLE_HZ = 30
        const val MIN_RATE = 60
        const val MAX_RATE = 170
        const val MIN_LAG = SAMPLE_HZ * 60 / MAX_RATE
        const val MAX_LAG = SAMPLE_HZ * 60 / MIN_RATE
        const val OCTAVE_PREFERENCE = 0.6
        const val MIN_RECENT_CORRELATION = 0.5
        const val MIN_FPS = 6
    }
}
