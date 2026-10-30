package com.zeroflux.cprcoach.engine

/** Keys match the clip files in assets/clips/<language>/<key>.wav */
enum class Phrase(val key: String) {
    KEEP_GOING("keep_going"),
    PUSH_FASTER("push_faster"),
    SLOW_DOWN("slow_down"),
    ELBOWS_STRAIGHT("elbows_straight"),
    SHOULDERS_OVER_HANDS("shoulders_over_hands"),
    GOOD("good"),
}

/**
 * Picks at most one spoken correction. A correction must hold steady before it plays, there is a
 * cooldown between phrases, and the same phrase never plays twice in a row.
 * While tracking is lost it stays silent, so the rescuer only hears the metronome.
 */
class CoachPolicy(
    private val steadyMs: Long = 3_000,
    private val postureSteadyMs: Long = 2_000,
    private val cooldownMs: Long = 6_000,
    private val praiseEveryMs: Long = 20_000,
) {
    private var candidate: Phrase? = null
    private var candidateSinceMs = 0L
    private var lastSpokenMs: Long? = null
    private var lastPhrase: Phrase? = null
    private var pausedSinceMs: Long? = null
    private val pausedReads = ArrayDeque<Pair<Long, Boolean>>()
    private var pendingRecovery = false
    private var pauseAcknowledged = false
    private var pushingSinceMs: Long? = null
    private var pushedSinceCue = false
    private var absentSinceMs: Long? = null
    private var repeatAllowed = false
    private var absentReads = 0

    /** Returns the phrase to play now, or null to stay quiet. */
    fun update(reading: LiveReading): Phrase? {
        val now = reading.timeMs
        trackPause(reading)

        val wanted = wantedPhrase(reading, now)
        if (wanted != candidate) {
            candidate = wanted
            candidateSinceMs = now
        }
        val phrase = candidate ?: return null
        // Recovering from a pause should be acknowledged quickly, everything else must be steady.
        val requiredMs = when (phrase) {
            Phrase.KEEP_GOING -> 0
            // The posture judge already waits about a second, so the voice follows the amber screen promptly.
            Phrase.ELBOWS_STRAIGHT, Phrase.SHOULDERS_OVER_HANDS -> postureSteadyMs
            else -> steadyMs
        }
        if (now - candidateSinceMs < requiredMs) return null
        lastSpokenMs?.let { if (now - it < cooldownMs) return null }

        lastSpokenMs = now
        lastPhrase = phrase
        absentSinceMs = null
        repeatAllowed = false
        absentReads = 0
        candidate = null
        if (phrase == Phrase.KEEP_GOING) {
            pendingRecovery = false
            pauseAcknowledged = true
            pushedSinceCue = false
        }
        return phrase
    }

    /**
     * A pause is PAUSED for MIN_PAUSE_MS without a break, and mostly still. Wrist noise (sitting up, resting hands)
     * makes `moving` flicker, so a few moving reads are fine; pushing without a locked rate is moving nearly always.
     */
    private fun trackPause(reading: LiveReading) {
        when (reading.tracking) {
            TrackingState.PAUSED -> {
                val now = reading.timeMs
                pausedReads.addLast(now to reading.moving)
                while (now - pausedReads.first().first > MIN_PAUSE_MS) pausedReads.removeFirst()
                val still = pausedReads.count { !it.second } * 2 >= pausedReads.size
                if (!still) {
                    // Mostly moving: the rescuer is pushing without a locked rate, so there is no pause to time.
                    pausedSinceMs = null
                    if (pushingSinceMs == null) pushingSinceMs = now
                    if (now - pushingSinceMs!! >= MIN_PUSH_MS) {
                        // Sustained pushing counts like BEATING: the next stop is a new pause.
                        pushedSinceCue = true
                        pauseAcknowledged = false
                    }
                } else {
                    pushingSinceMs = null
                    // The pause clock starts at the first still read, never while the hands are moving.
                    if (pausedSinceMs == null) pausedSinceMs = pausedReads.first { !it.second }.first
                }
                // Keep going is never said right after real movement: two moving reads in a row (one is flicker)
                // in the last second mean the rescuer is pushing, or just was.
                val stillNow = pausedReads.zipWithNext().none { (a, b) ->
                    a.second && b.second && now - b.first <= STILL_NOW_MS
                }
                pendingRecovery = !pauseAcknowledged && still && !reading.moving && stillNow &&
                    now - pausedSinceMs!! >= MIN_PAUSE_MS
            }
            TrackingState.BEATING -> {
                clearPause()
                pauseAcknowledged = false
                // Real pushing (moving hands under a locked rate) is what lets Keep going repeat.
                val now = reading.timeMs
                if (!reading.moving) pushingSinceMs = null
                else {
                    if (pushingSinceMs == null) pushingSinceMs = now
                    if (now - pushingSinceMs!! >= MIN_PUSH_MS) pushedSinceCue = true
                }
            }
            // We can't see the arms, so this time can't count as part of a pause.
            TrackingState.LOST, TrackingState.WARMING -> clearPause()
        }
        if (reading.tracking != TrackingState.BEATING && reading.tracking != TrackingState.PAUSED) pushingSinceMs = null
    }

    private fun clearPause() {
        pausedSinceMs = null
        pausedReads.clear()
        pendingRecovery = false
    }

    private fun wantedPhrase(reading: LiveReading, now: Long): Phrase? {
        if (reading.tracking == TrackingState.LOST || reading.tracking == TrackingState.WARMING) return null
        // Only real pushing since the last cue lets Keep going repeat; otherwise it never plays twice in a row.
        if (pendingRecovery) return Phrase.KEEP_GOING.takeIf { it != lastPhrase || pushedSinceCue }
        val beating = reading.tracking == TrackingState.BEATING
        // Bent or leaning pushing often has no locked rate, so posture is also coached while PAUSED and moving.
        if (!beating && !reading.moving) return null

        val rate = reading.ratePerMinute?.takeIf { beating }
        val wanted = ArrayList<Phrase>(3)
        if (rate != null && rate < LiveReading.TARGET_MIN) wanted.add(Phrase.PUSH_FASTER)
        if (rate != null && rate > LiveReading.TARGET_MAX) wanted.add(Phrase.SLOW_DOWN)
        // Posture names the issue; NOT_SIDE_ON stays silent in voice.
        val issue = reading.posture?.issue
        val elbowsBent = if (reading.posture != null) issue == PostureIssue.ELBOWS_BENT else reading.elbowsBent == true
        if (elbowsBent) wanted.add(Phrase.ELBOWS_STRAIGHT)
        if (issue == PostureIssue.SHOULDERS_NOT_OVER_HANDS) wanted.add(Phrase.SHOULDERS_OVER_HANDS)
        if (wanted.isEmpty() && beating && (lastSpokenMs?.let { now - it >= praiseEveryMs } ?: true)) wanted.add(Phrase.GOOD)
        // A phrase may repeat only after its condition has been gone for a while (it was fixed, then came back).
        val last = lastPhrase
        if (last == null || last in wanted) { absentSinceMs = null; absentReads = 0 }
        else {
            if (absentSinceMs == null) absentSinceMs = now
            absentReads++
        }
        if (absentReads >= CLEAR_READS && absentSinceMs?.let { now - it >= CLEAR_MS } == true) repeatAllowed = true
        return wanted.firstOrNull { it != last || (repeatAllowed && it != Phrase.GOOD) }
    }

    private companion object {
        const val MIN_PAUSE_MS = 2_000L
        const val STILL_NOW_MS = 1_000L
        const val MIN_PUSH_MS = 3_000L
        const val CLEAR_MS = 3_000L
        const val CLEAR_READS = 8
    }
}
