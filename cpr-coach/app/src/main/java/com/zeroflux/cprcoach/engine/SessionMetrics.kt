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

class SessionMetrics(private val minPauseMs: Long = 2_000, private val minPushMs: Long = 3_000) {
    private var lastMs: Long? = null
    private var elapsedMs = 0L
    private var measuredMs = 0L
    private var inRangeMs = 0L
    private var rateTimeSum = 0.0
    private var elbowSeenMs = 0L
    private var elbowStraightMs = 0L
    private var pauses = 0
    private var pausedSinceMs: Long? = null
    private var pushingSinceMs: Long? = null
    private val pausedReads = ArrayDeque<Pair<Long, Boolean>>()
    private var pauseCounted = false
    private var hasPushed = false
    // A stop counted before any compression; taken back once pushing starts, because it was only waiting to begin.
    private var leadingPause = false

    fun add(reading: LiveReading) {
        val now = reading.timeMs
        val previous = lastMs
        lastMs = now
        if (previous == null) return
        // Time going backwards must never count as negative time.
        val dt = (now - previous).coerceAtLeast(0L)
        elapsedMs += dt

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
            TrackingState.PAUSED -> trackPause(reading, now)
            TrackingState.BEATING -> {
                clearPause()
                pauseCounted = false
                startedPushing()
                pushingSinceMs = null
            }
            // We can't see the arms, so separate hesitations must not add up across this gap.
            // pauseCounted stays set, so one real pause seen in two parts still counts once.
            TrackingState.LOST, TrackingState.WARMING -> { clearPause(); pushingSinceMs = null }
        }
    }

    /**
     * Same idea as the coach: a pause is PAUSED for minPauseMs without a break and mostly still. Wrist noise makes
     * `moving` flicker, so a few moving reads are fine; PAUSED while moving most of the time is pushing without a
     * locked rate, which is never a pause. One pause per stop: pushing again (or BEATING) re-arms the count.
     */
    private fun trackPause(reading: LiveReading, now: Long) {
        pausedReads.addLast(now to reading.moving)
        while (now - pausedReads.first().first > minPauseMs) pausedReads.removeFirst()
        val still = pausedReads.count { !it.second } * 2 >= pausedReads.size
        if (!still) {
            pausedSinceMs = null
            if (pushingSinceMs == null) pushingSinceMs = now
            if (now - pushingSinceMs!! >= minPushMs) {
                pauseCounted = false
                startedPushing()
            }
            return
        }
        pushingSinceMs = null
        // The pause clock starts at the first still read, never while the hands are moving.
        if (pausedSinceMs == null) pausedSinceMs = pausedReads.first { !it.second }.first
        if (!reading.moving && !pauseCounted && now - pausedSinceMs!! >= minPauseMs) {
            pauses++
            pauseCounted = true
            if (!hasPushed) leadingPause = true
        }
    }

    private fun startedPushing() {
        if (leadingPause) pauses--
        leadingPause = false
        hasPushed = true
    }

    private fun clearPause() {
        pausedSinceMs = null
        pausedReads.clear()
    }

    /** Paused time counts for nothing. The next reading starts a fresh interval. */
    fun pause() {
        lastMs = null
    }

    fun scorecard(): Scorecard {
        return Scorecard(
            sessionSeconds = seconds(elapsedMs),
            measuredSeconds = seconds(measuredMs),
            secondsInRange = seconds(inRangeMs),
            averageRate = if (measuredMs > 0) Math.round(rateTimeSum / measuredMs).toInt() else null,
            elbowsStraightPercent = if (elbowSeenMs > 0) Math.round(100.0 * elbowStraightMs / elbowSeenMs).toInt() else null,
            pauses = pauses,
        )
    }

    private fun seconds(ms: Long) = Math.round(ms / 1000.0).toInt()
}
