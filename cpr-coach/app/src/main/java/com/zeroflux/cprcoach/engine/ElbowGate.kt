package com.zeroflux.cprcoach.engine

/**
 * Decides when an elbow reading is trustworthy enough to speak about. One side-on 2D camera
 * cannot always see the true elbow angle, so this stays silent (null) unless the reading has
 * been well seen, side-on and steady for about 1.5 s. A false "straighten arms" is worse than none.
 */
class ElbowGate {
    private val times = ArrayList<Long>()
    private val angles = ArrayList<Double>()
    private var bent = false

    /** Returns true when confidently bent, false when confidently straight, null when not confident. */
    fun update(timeMs: Long, degrees: Double, elbowVisibility: Float, sideOn: Boolean): Boolean? {
        val bad = !degrees.isFinite() || !elbowVisibility.isFinite() || elbowVisibility < MIN_VISIBILITY || !sideOn
        if (bad) {
            reset()
            return null
        }
        if (times.isNotEmpty()) {
            val last = times[times.size - 1]
            if (timeMs <= last || timeMs - last > MAX_GAP_MS) reset()
        }
        times.add(timeMs)
        angles.add(degrees)
        var drop = 0
        while (drop < times.size - 1 && timeMs - times[drop] > WINDOW_MS) drop++
        if (drop > 0) {
            times.subList(0, drop).clear()
            angles.subList(0, drop).clear()
        }
        if (times.size < MIN_SAMPLES || timeMs - times[0] < MIN_SPAN_MS) return null

        val sorted = angles.sorted()
        val median = quantile(sorted, 0.5)
        if (quantile(sorted, 0.75) - quantile(sorted, 0.25) > MAX_IQR_DEGREES) return null
        bent = when {
            median < BENT_BELOW_DEGREES -> true
            bent && median < BENT_EXIT_DEGREES -> true
            else -> false
        }
        return when {
            bent -> true
            median >= STRAIGHT_FROM_DEGREES -> false
            else -> null
        }
    }

    fun reset() {
        times.clear()
        angles.clear()
        bent = false
    }

    private fun quantile(sorted: List<Double>, q: Double): Double {
        val pos = q * (sorted.size - 1)
        val lo = pos.toInt()
        val hi = minOf(lo + 1, sorted.size - 1)
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }

    companion object {
        const val WINDOW_MS = 1_500L
        /** Window must really span about 1.5 s, allowing for frame jitter. */
        const val MIN_SPAN_MS = 1_400L
        const val MAX_GAP_MS = 400L
        const val MIN_SAMPLES = 8
        /** Recordings: the good sessions sit at 0.94-0.99 median; below 0.8 the joint is guessed. */
        const val MIN_VISIBILITY = 0.8f
        /** Real compressions swing the angle by 10-20 degrees IQR; wider than this is noise or tracking slip. */
        const val MAX_IQR_DEGREES = 20.0
        const val BENT_BELOW_DEGREES = 100.0
        /** Hysteresis: once bent, stay bent until the median clearly recovers. */
        const val BENT_EXIT_DEGREES = 115.0
        /** Recordings with mostly straight arms still had medians of 141-155; require this much. */
        const val STRAIGHT_FROM_DEGREES = 135.0
    }
}
