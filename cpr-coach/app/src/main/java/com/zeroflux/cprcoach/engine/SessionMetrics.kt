package com.zeroflux.cprcoach.engine

/**
 * What the scorecard shows. Everything is measured only while the arms were visible, so a value is
 * null when there was nothing real to report, and the UI must show a dash rather than a number.
 */
data class Scorecard(
    val sessionSeconds: Int,
    /** Seconds the arms were visible and a steady rate was measured. */
    val measuredSeconds: Int,
    val secondsInRange: Int,
    val averageRate: Int?,
    val elbowsStraightPercent: Int?,
    val pauses: Int,
)

class SessionMetrics(private val minPauseMs: Long = 2_000) {
    private var startMs: Long? = null
    private var lastMs: Long? = null
    private var measuredMs = 0L
    private var inRangeMs = 0L
    private var rateTimeSum = 0.0
    private var elbowSeenMs = 0L
    private var elbowStraightMs = 0L
    private var pauses = 0
    private var pausedMs = 0L
    private var pauseCounted = false

    fun add(reading: LiveReading) {
        val now = reading.timeMs
        val previous = lastMs
        if (startMs == null) startMs = now
        lastMs = now
        if (previous == null) return
        val dt = now - previous

        reading.ratePerMinute?.let { rate ->
            measuredMs += dt
            rateTimeSum += rate * dt.toDouble()
            if (reading.inRange == true) inRangeMs += dt
        }
        reading.elbowsBent?.let { bent ->
            elbowSeenMs += dt
            if (!bent) elbowStraightMs += dt
        }
        when (reading.tracking) {
            TrackingState.PAUSED -> {
                pausedMs += dt
                if (pausedMs >= minPauseMs && !pauseCounted) {
                    pauses++
                    pauseCounted = true
                }
            }
            TrackingState.BEATING -> {
                pausedMs = 0
                pauseCounted = false
            }
            // We can't see the arms, so separate hesitations must not add up across this gap.
            // pauseCounted stays set, so one real pause seen in two parts still counts once.
            TrackingState.LOST, TrackingState.WARMING -> pausedMs = 0
        }
    }

    fun scorecard(): Scorecard {
        val total = (lastMs ?: 0L) - (startMs ?: 0L)
        return Scorecard(
            sessionSeconds = seconds(total),
            measuredSeconds = seconds(measuredMs),
            secondsInRange = seconds(inRangeMs),
            averageRate = if (measuredMs > 0) Math.round(rateTimeSum / measuredMs).toInt() else null,
            elbowsStraightPercent = if (elbowSeenMs > 0) Math.round(100.0 * elbowStraightMs / elbowSeenMs).toInt() else null,
            pauses = pauses,
        )
    }

    private fun seconds(ms: Long) = Math.round(ms / 1000.0).toInt()
}
