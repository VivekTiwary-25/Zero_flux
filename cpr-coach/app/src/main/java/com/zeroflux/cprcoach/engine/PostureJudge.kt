package com.zeroflux.cprcoach.engine

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Judges the rescuer's position from one side-on 2D camera. Rule: if unsure, say less.
 * Order: (1) joints seen well enough, else null; (2) view side-on, else NOT_SIDE_ON or null;
 * (3) only then elbow angle and shoulder-over-wrist, each a median over about one second,
 * needing about a second sustained to fire and about a second of good to clear.
 * Pure Kotlin, one thread. Frames should come through [LandmarkGuard] first. Thresholds marked GUESS were not measured.
 */
class PostureJudge {
    private class Timed(val t: Long, val v: Double)

    private val elbowWin = ArrayDeque<Timed>()
    private val offsetWin = ArrayDeque<Timed>()
    private val sideWin = ArrayDeque<Timed>()
    private var lastTimeMs: Long? = null
    private var notSideSinceMs: Long? = null
    private val elbow = Issue()
    private val lean = Issue()

    /** Sustain and hysteresis state of one issue. */
    private class Issue {
        var active = false
        var sinceMs: Long? = null

        /** [enter] and [leave] are raw per-frame conditions; returns the sustained state. */
        fun step(t: Long, enter: Boolean, leave: Boolean): Boolean {
            val cond = if (active) leave else enter
            if (!cond) {
                sinceMs = null
            } else {
                val s = sinceMs ?: t.also { sinceMs = it }
                if (t - s >= SUSTAIN_MS) {
                    active = !active
                    sinceMs = null
                }
            }
            return active
        }

        fun reset() {
            active = false
            sinceMs = null
        }
    }

    fun reset() {
        elbowWin.clear()
        offsetWin.clear()
        sideWin.clear()
        lastTimeMs = null
        notSideSinceMs = null
        elbow.reset()
        lean.reset()
    }

    fun update(sample: PoseSample, arm: ArmLandmarks?, aspect: Float): PostureReading {
        val unknown = PostureReading(null, null, null, null)
        val t = sample.timeMs
        val last = lastTimeMs
        if (last != null && (t <= last || t - last > MAX_GAP_MS)) reset()

        // (1) enough visibility?
        val seen = arm != null && aspect.isFinite() && aspect > 0f &&
            sample.elbowDegrees.isFinite() &&
            sample.elbowVisibility >= MIN_ELBOW_VISIBILITY &&
            minOf(arm.shoulderVis, arm.wristVis, arm.hipVis) >= MIN_OTHER_VISIBILITY
        if (!seen || arm == null) return unknown
        val coords = floatArrayOf(arm.shoulderX, arm.shoulderY, arm.elbowX, arm.elbowY, arm.wristX, arm.wristY, arm.hipX)
        if (coords.any { !it.isFinite() }) return unknown
        lastTimeMs = t

        // (2) side-on?
        sideWin.addLast(Timed(t, if (sample.sideOn) 0.0 else 1.0))
        trim(sideWin, t, SIDE_WINDOW_MS)
        val sideSpan = t - sideWin.first().t
        val notShare = sideWin.sumOf { it.v } / sideWin.size
        val clearlyNot = sideSpan >= SIDE_MIN_SPAN_MS && notShare >= NOT_SIDE_ON_SHARE
        val clearlyOn = sideSpan >= SIDE_MIN_SPAN_MS && notShare <= SIDE_ON_SHARE
        if (clearlyNot) {
            val s = notSideSinceMs ?: t.also { notSideSinceMs = it }
            // The joints aren't trustworthy from this view; drop old medians so a fix starts clean.
            elbowWin.clear()
            offsetWin.clear()
            elbow.reset()
            lean.reset()
            return if (t - s >= NOT_SIDE_ON_MS) PostureReading(false, PostureIssue.NOT_SIDE_ON, null, null) else unknown
        }
        notSideSinceMs = null
        if (!clearlyOn) return unknown

        // (3) judge
        val sx = arm.shoulderX * aspect.toDouble()
        val ex = arm.elbowX * aspect.toDouble()
        val wx = arm.wristX * aspect.toDouble()
        val hx = arm.hipX * aspect.toDouble()
        val armLen = hypot(sx - ex, (arm.shoulderY - arm.elbowY).toDouble()) +
            hypot(wx - ex, (arm.wristY - arm.elbowY).toDouble())
        if (armLen < MIN_ARM_LENGTH) return unknown
        // Positive: shoulder is behind the hands (on the hips' side of the wrist). Negative: ahead of them.
        val dir = if (wx >= hx) 1.0 else -1.0
        val offset = (wx - sx) * dir / armLen

        elbowWin.addLast(Timed(t, sample.elbowDegrees.coerceIn(0.0, 180.0)))
        offsetWin.addLast(Timed(t, offset))
        trim(elbowWin, t, MEDIAN_WINDOW_MS)
        trim(offsetWin, t, MEDIAN_WINDOW_MS)
        if (t - elbowWin.first().t < MIN_MEDIAN_SPAN_MS) return unknown

        val elbowMed = median(elbowWin)
        val offMed = median(offsetWin)
        val bent = elbow.step(t, elbowMed < BENT_BELOW_DEGREES, elbowMed >= STRAIGHT_FROM_DEGREES)
        val leaning = lean.step(t, abs(offMed) > LEAN_ENTER, abs(offMed) <= LEAN_EXIT)
        return when {
            bent -> PostureReading(false, PostureIssue.ELBOWS_BENT, elbowMed, offMed)
            leaning -> PostureReading(false, PostureIssue.SHOULDERS_NOT_OVER_HANDS, elbowMed, offMed)
            elbowMed >= STRAIGHT_FROM_DEGREES && abs(offMed) <= LEAN_EXIT -> PostureReading(true, null, elbowMed, offMed)
            else -> PostureReading(null, null, elbowMed, offMed)
        }
    }

    private fun trim(w: ArrayDeque<Timed>, now: Long, span: Long) {
        while (w.size > 1 && now - w.first().t > span) w.removeFirst()
    }

    private fun median(w: ArrayDeque<Timed>): Double {
        val s = w.map { it.v }.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    companion object {
        /** Gap that restarts every window (the rescuer moved or tracking dropped). */
        const val MAX_GAP_MS = 500L

        /** Elbow joint must be really seen (as ElbowGate: recordings sit at 0.94-0.99, below 0.8 it is guessed). */
        const val MIN_ELBOW_VISIBILITY = 0.8f

        /** GUESS: shoulder, wrist and hip visibility for the lean measure. */
        const val MIN_OTHER_VISIBILITY = 0.6f
        const val MIN_ARM_LENGTH = 0.05

        /** Side-on is judged over this window. GUESS shares: the recordings have almost no non-side-on frames. */
        const val SIDE_WINDOW_MS = 1_000L
        const val SIDE_MIN_SPAN_MS = 700L
        const val NOT_SIDE_ON_SHARE = 0.8
        const val SIDE_ON_SHARE = 0.2

        /**
         * Clearly not side-on (which itself takes ~0.8 s of the window to show) must hold this long more
         * before NOT_SIDE_ON is returned, so it appears about 1.8 s after the turn. GUESS.
         */
        const val NOT_SIDE_ON_MS = 1_000L

        /** Median over at least one compression cycle (about 0.55 s at 110/min); about 1 s. */
        const val MEDIAN_WINDOW_MS = 1_000L
        const val MIN_MEDIAN_SPAN_MS = 600L

        /** Sustained time to fire and to clear. */
        const val SUSTAIN_MS = 1_000L

        /**
         * Measured on the LandmarkGuard-filtered frames of recordings 10-15; 14 and 15 are labelled from saved
         * camera pictures (not a person's labels). Straight pushing reads 1 s medians 165-178 (14, 15, 10, 11),
         * the first bent block of 15 124-144, the second only 144-166 (median 151): a mild bend. Recording 12 before
         * 15 s (team: straight, must stay quiet) dips to 144-150 in 5% of its windows, so BENT_BELOW cannot sit
         * above 145 without flagging it; the second bent block of 15 is therefore mostly not flagged. A lower
         * percentile or a 2-3 s window did not separate the two (12 early p25 145 vs 15 bent-2 p25 148).
         * Between the two thresholds the answer is null.
         */
        const val BENT_BELOW_DEGREES = 145.0
        const val STRAIGHT_FROM_DEGREES = 153.0

        /**
         * Measured on the same frames, 1 s median of |offset|: straight 15 0.01-0.25, 14 0.12-0.30 (99%), 10 0.14-0.24;
         * lean block of 15 (team's account, arms diagonal in the pictures) 0.29-0.35. 11 (0.06-0.39) and 12 before
         * 15 s (0.31-0.46) overlap the lean block: no threshold fires on 15 and stays quiet on them. Chosen to
         * stay quiet on the picture-labelled straights of 14 and 15; 12's early part will read as leaning.
         */
        const val LEAN_ENTER = 0.28
        const val LEAN_EXIT = 0.24
    }
}
