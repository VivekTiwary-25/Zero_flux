package com.zeroflux.cprcoach.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** One detected compression bottom. [timeMs] is the time of the lowest sample, not of detection. */
data class PushEvent(val timeMs: Long)

/**
 * Finds each compression bottom in the wrist signal (wrist height above the hip, so a push is a dip).
 * Pure Kotlin, one thread, no allocation except for an event. It is for display only; the rate maths
 * lives elsewhere and does not depend on it.
 *
 * Rules: light smoothing; a dip counts when the signal then rises by a prominence that is relative to the
 * recent push size (never below an absolute floor, so small motion gives nothing); pushes closer than
 * [REFRACTORY_MS] are merged; a gap in the data forgets everything. An event is reported a little after
 * the bottom, once the rise confirms it.
 */
class PushDetector {
    private var started = false
    private var lastTimeMs = 0L
    private var smooth = 0.0
    private var descending = true
    private var extreme = 0.0
    private var extremeTime = 0L
    private var lastBottom = 0.0
    private var amplitude = 0.0
    private var lastEventMs = Long.MIN_VALUE
    private var candidateMs = Long.MIN_VALUE
    private var refInterval = 0.0
    private var chain = 0

    fun reset() {
        started = false
        amplitude = 0.0
        lastEventMs = Long.MIN_VALUE
        forgetRhythm()
    }

    private fun forgetRhythm() {
        candidateMs = Long.MIN_VALUE
        refInterval = 0.0
        chain = 0
    }

    /** Random wobble is not a rhythm: show a push only once the last few bottoms are evenly spaced. */
    private fun rhythmic(bottomMs: Long): Boolean {
        if (candidateMs == Long.MIN_VALUE) {
            candidateMs = bottomMs
            return false
        }
        val interval = (bottomMs - candidateMs).toDouble()
        candidateMs = bottomMs
        if (interval > MAX_INTERVAL_MS) {
            refInterval = 0.0
            chain = 0
            return false
        }
        if (refInterval == 0.0) {
            refInterval = interval
            chain = 1
            return false
        }
        if (abs(interval - refInterval) <= 0.4 * refInterval) {
            chain++
            refInterval = 0.7 * refInterval + 0.3 * interval
        } else if (abs(interval - 2 * refInterval) <= 0.5 * refInterval) {
            chain++ // one push missed by the camera
        } else {
            refInterval = interval
            chain = 1
            return false
        }
        return chain >= 2
    }

    /** Missing pose: the next sample starts afresh, so nothing is bridged across the gap. */
    fun gap() = reset()

    /** Returns the push confirmed by this sample, if any. Bad input is ignored and never throws. */
    fun update(timeMs: Long, signal: Double): PushEvent? {
        if (!signal.isFinite()) return null
        if (started) {
            if (timeMs <= lastTimeMs) return null
            if (timeMs - lastTimeMs > GAP_MS) reset()
        }
        if (!started) {
            started = true
            lastTimeMs = timeMs
            smooth = signal
            descending = true
            extreme = signal
            extremeTime = timeMs
            return null
        }
        val dt = (timeMs - lastTimeMs).toDouble()
        lastTimeMs = timeMs
        smooth += (1.0 - exp(-dt / SMOOTH_TAU_MS)) * (signal - smooth)
        // A one-off big movement must not hide the pushes that follow it.
        amplitude *= exp(-dt / AMPLITUDE_DECAY_MS)
        val prominence = max(MIN_PROMINENCE, RELATIVE_PROMINENCE * amplitude)
        if (descending) {
            if (smooth <= extreme) {
                extreme = smooth
                extremeTime = timeMs
                return null
            }
            if (smooth - extreme < prominence) return null
            // Confirmed bottom at extremeTime. Now track the top of this rise.
            val bottomTime = extremeTime
            lastBottom = extreme
            descending = false
            extreme = smooth
            extremeTime = timeMs
            if (lastEventMs != Long.MIN_VALUE && bottomTime - lastEventMs < REFRACTORY_MS) return null
            lastEventMs = bottomTime
            return if (rhythmic(bottomTime)) PushEvent(bottomTime) else null
        }
        if (smooth >= extreme) {
            extreme = smooth
            extremeTime = timeMs
            return null
        }
        if (extreme - smooth < prominence) return null
        // Top confirmed: learn this push's size, then look for the next bottom.
        val size = extreme - lastBottom
        amplitude = if (amplitude == 0.0) size else amplitude * 0.7 + minOf(size, 1.5 * amplitude) * 0.3
        descending = true
        extreme = smooth
        extremeTime = timeMs
        return null
    }

    companion object {
        const val REFRACTORY_MS = 300L
        const val GAP_MS = 400L
        const val MAX_INTERVAL_MS = 1500.0
        const val AMPLITUDE_DECAY_MS = 3000.0
        const val SMOOTH_TAU_MS = 30.0

        /** Torso lengths. Below this swing in the wrist nothing is a push. */
        const val MIN_PROMINENCE = 0.09
        const val RELATIVE_PROMINENCE = 0.4
    }
}
