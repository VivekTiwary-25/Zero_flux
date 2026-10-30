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
    /** Null unless the elbow reading is trustworthy. Stay silent on elbows when this is null. */
    val elbowsBent: Boolean?,
    /** The wrist moved over about the last half second, whether or not there is a rate yet. */
    val moving: Boolean = false,
    /** Null until the posture judge has something to say. */
    val posture: PostureReading? = null,
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
    private val elbows: ElbowGate = ElbowGate(),
    private val judge: PostureJudge = PostureJudge(),
    private val movingOnRms: Double = MOVING_ON_RMS,
    private val movingOffRms: Double = MOVING_OFF_RMS,
) {
    private var lastGoodMs: Long? = null
    private val elbowHistory = ArrayDeque<Pair<Long, Double>>()
    private val wristHistory = ArrayDeque<Pair<Long, Double>>()
    private val rateHistory = ArrayDeque<Double>()
    private var lastRatedFrameMs: Long? = null
    private var moving = false
    private var elbowVerdict: Boolean? = null
    private var posture: PostureReading? = null
    private var postureAtMs = 0L
    private var postureActive = false
    private var heldRate: Int? = null
    private var heldRateAtMs = 0L

    /** Call for every pose result. With arm landmarks the posture judge decides on elbows; without, the old gate does. */
    fun addFrame(frame: PoseFrame) {
        val sample = frame.sample
        val arm = frame.arm
        addFrame(sample, arm != null)
        if (sample == null || arm == null) return
        if (lastGoodMs != sample.timeMs) return // sample was rejected as unseen or non-finite
        val reading = judge.update(sample, arm, frame.aspect)
        posture = reading
        postureAtMs = sample.timeMs
        postureActive = true
        elbowVerdict = when {
            reading.issue == PostureIssue.ELBOWS_BENT -> true
            reading.good == true -> false
            else -> null
        }
    }

    /** Call for every camera frame the pose model ran on. Pass null when it found no person. */
    fun addFrame(sample: PoseSample?) = addFrame(sample, false)

    private fun addFrame(sample: PoseSample?, judged: Boolean) {
        if (sample == null || !(sample.minVisibility >= minVisibility)) return
        // A NaN or infinite reading counts as not visible, so it can't poison the windows.
        if (!sample.wristSignal.isFinite() || !sample.elbowDegrees.isFinite()) return
        val last = lastGoodMs
        // A long gap, or time going backwards, starts the windows afresh.
        if (last != null && (sample.timeMs - last > lostAfterMs || sample.timeMs < last)) clearSignals()
        lastGoodMs = sample.timeMs
        rate.add(sample.timeMs, sample.wristSignal)
        val degrees = sample.elbowDegrees.coerceIn(0.0, 180.0)
        elbowHistory.addLast(sample.timeMs to degrees)
        while (sample.timeMs - elbowHistory.first().first > ELBOW_WINDOW_MS) elbowHistory.removeFirst()
        if (!judged) elbowVerdict = elbows.update(sample.timeMs, degrees, sample.elbowVisibility, sample.sideOn)
        wristHistory.addLast(sample.timeMs to sample.wristSignal)
        while (sample.timeMs - wristHistory.first().first > MOVING_WINDOW_MS) wristHistory.removeFirst()
    }

    fun read(nowMs: Long): LiveReading {
        val last = lastGoodMs
        if (last == null || nowMs - last > lostAfterMs) {
            clearSignals()
            return LiveReading(nowMs, TrackingState.LOST, null, null, null)
        }
        if (elbowHistory.isEmpty()) return LiveReading(nowMs, TrackingState.LOST, null, null, null)
        val elbow = medianOf(elbowHistory.map { it.second })
        val isMoving = updateMoving(nowMs)
        return when (val beat = rate.read()) {
            BeatReading.Warming -> LiveReading(nowMs, TrackingState.WARMING, null, elbow, elbowVerdict, isMoving, currentPosture(nowMs))
            BeatReading.NoBeat -> {
                rateHistory.clear()
                val held = heldRate
                // A dropout in the middle of pushing: the rate was measured a moment ago, so keep it briefly.
                if (isMoving && held != null && nowMs - heldRateAtMs in 0..HOLD_RATE_MS) {
                    LiveReading(nowMs, TrackingState.BEATING, held, elbow, elbowVerdict, true, currentPosture(nowMs))
                } else {
                    heldRate = null
                    LiveReading(nowMs, TrackingState.PAUSED, null, elbow, elbowVerdict, isMoving, currentPosture(nowMs))
                }
            }
            is BeatReading.Beat -> {
                if (lastRatedFrameMs != last) {
                    lastRatedFrameMs = last
                    rateHistory.addLast(beat.perMinute)
                    if (rateHistory.size > RATE_SMOOTHING) rateHistory.removeFirst()
                }
                val smoothed = if (rateHistory.isEmpty()) beat.perMinute else medianOf(rateHistory.toList())
                val shown = Math.round(smoothed).toInt()
                heldRate = shown
                heldRateAtMs = nowMs
                LiveReading(nowMs, TrackingState.BEATING, shown, elbow, elbowVerdict, isMoving, currentPosture(nowMs))
            }
        }
    }

    /**
     * Wrist movement over the last half second: RMS around a straight line (so drift does not count),
     * with hysteresis so it does not flicker. With too few frames it keeps its last answer.
     */
    private fun updateMoving(nowMs: Long): Boolean {
        val recent = wristHistory.filter { it.first > nowMs - MOVING_WINDOW_MS }
        if (recent.size < MOVING_MIN_FRAMES) return moving
        val n = recent.size
        val t0 = recent.first().first
        val xs = DoubleArray(n) { (recent[it].first - t0).toDouble() }
        val ys = DoubleArray(n) { recent[it].second }
        val mx = xs.average()
        val my = ys.average()
        var sxx = 0.0
        var sxy = 0.0
        for (i in 0 until n) {
            sxx += (xs[i] - mx) * (xs[i] - mx)
            sxy += (xs[i] - mx) * (ys[i] - my)
        }
        val slope = if (sxx > 1e-9) sxy / sxx else 0.0
        var sum = 0.0
        for (i in 0 until n) {
            val r = ys[i] - (my + slope * (xs[i] - mx))
            sum += r * r
        }
        val rms = Math.sqrt(sum / n)
        moving = if (moving) rms > movingOffRms else rms > movingOnRms
        return moving
    }

    /** Posture is dropped when the judge has not been fed for a moment, so a stale verdict never lingers. */
    private fun currentPosture(nowMs: Long): PostureReading? =
        if (postureActive && nowMs - postureAtMs in 0..POSTURE_STALE_MS) posture else null

    private fun clearSignals() {
        rate.reset()
        elbows.reset()
        judge.reset()
        posture = null
        postureActive = false
        elbowHistory.clear()
        wristHistory.clear()
        rateHistory.clear()
        elbowVerdict = null
        moving = false
        heldRate = null
    }

    private fun medianOf(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    companion object {
        private const val ELBOW_WINDOW_MS = 1_000L
        private const val RATE_SMOOTHING = 5

        /** How long a rate measured a moment ago may keep showing across a dropout, while still moving. */
        /** Posture older than this is not shown (the judge is only fed frames with the arm well seen). */
        const val POSTURE_STALE_MS = 500L
        const val HOLD_RATE_MS = 2_000L
        const val MOVING_WINDOW_MS = 500L
        const val MOVING_MIN_FRAMES = 3

        /** Wrist RMS, in torso lengths. Set from the recordings: see the tests. */
        const val MOVING_ON_RMS = 0.04
        const val MOVING_OFF_RMS = 0.025
    }
}
