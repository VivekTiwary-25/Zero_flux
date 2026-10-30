package com.zeroflux.cprcoach.engine

enum class TrackingState {
    /** Arms not clearly visible. Never show a number in this state. */
    LOST,

    /** Arms visible, but not enough data for a rate yet. */
    WARMING,

    /** Arms visible, no steady beat. */
    PAUSED,

    /** Arms visible, steady beat, [LiveReading.ratePerMinute] is real. */
    BEATING,
}

data class LiveReading(
    val timeMs: Long,
    val tracking: TrackingState,
    /** Only non-null while [tracking] is BEATING. */
    val ratePerMinute: Int?,
    /** Null when the arms can't be seen. */
    val elbowDegrees: Double?,
    val elbowsBent: Boolean?,
) {
    val inRange: Boolean? get() = ratePerMinute?.let { it in TARGET_MIN..TARGET_MAX }

    companion object {
        const val TARGET_MIN = 100
        const val TARGET_MAX = 120
    }
}

/** Turns the stream of pose samples into what the session currently knows, and nothing more. */
class LiveAnalyzer(
    private val rate: RateEstimator = RateEstimator(),
    private val minVisibility: Float = 0.5f,
    private val lostAfterMs: Long = 500,
    private val elbowBentBelow: Double = 150.0,
    private val elbowHysteresis: Double = 5.0,
) {
    private var lastGoodMs: Long? = null
    private val elbowHistory = ArrayDeque<Pair<Long, Double>>()
    private val rateHistory = ArrayDeque<Double>()
    private var elbowsBent = false
    private var lastRatedFrameMs: Long? = null

    /** Call for every camera frame the pose model ran on. Pass null when it found no person. */
    fun addFrame(sample: PoseSample?) {
        if (sample == null || sample.minVisibility < minVisibility) return
        val last = lastGoodMs
        if (last != null && sample.timeMs - last > lostAfterMs) clearSignals()
        lastGoodMs = sample.timeMs
        rate.add(sample.timeMs, sample.wristSignal)
        elbowHistory.addLast(sample.timeMs to sample.elbowDegrees)
        while (sample.timeMs - elbowHistory.first().first > ELBOW_WINDOW_MS) elbowHistory.removeFirst()
    }

    fun read(nowMs: Long): LiveReading {
        val last = lastGoodMs
        if (last == null || nowMs - last > lostAfterMs) {
            clearSignals()
            return LiveReading(nowMs, TrackingState.LOST, null, null, null)
        }
        val elbow = medianOf(elbowHistory.map { it.second })
        elbowsBent = if (elbowsBent) {
            elbow < elbowBentBelow + elbowHysteresis
        } else {
            elbow < elbowBentBelow - elbowHysteresis
        }
        return when (val beat = rate.read()) {
            BeatReading.Warming -> LiveReading(nowMs, TrackingState.WARMING, null, elbow, elbowsBent)
            BeatReading.NoBeat -> {
                rateHistory.clear()
                LiveReading(nowMs, TrackingState.PAUSED, null, elbow, elbowsBent)
            }
            is BeatReading.Beat -> {
                if (lastRatedFrameMs != last) {
                    lastRatedFrameMs = last
                    rateHistory.addLast(beat.perMinute)
                    if (rateHistory.size > RATE_SMOOTHING) rateHistory.removeFirst()
                }
                val smoothed = medianOf(rateHistory.toList())
                LiveReading(nowMs, TrackingState.BEATING, Math.round(smoothed).toInt(), elbow, elbowsBent)
            }
        }
    }

    private fun clearSignals() {
        rate.reset()
        elbowHistory.clear()
        rateHistory.clear()
        elbowsBent = false
    }

    private fun medianOf(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private companion object {
        const val ELBOW_WINDOW_MS = 1_000L
        const val RATE_SMOOTHING = 5
    }
}
