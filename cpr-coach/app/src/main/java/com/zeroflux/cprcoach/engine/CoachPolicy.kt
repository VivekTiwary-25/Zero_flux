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
        candidate = null
        if (phrase == Phrase.KEEP_GOING) {
            pendingRecovery = false
            pauseAcknowledged = true
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
                if (pausedSinceMs == null) pausedSinceMs = now
                pausedReads.addLast(now to reading.moving)
                while (now - pausedReads.first().first > MIN_PAUSE_MS) pausedReads.removeFirst()
                val still = pausedReads.count { !it.second } * 2 >= pausedReads.size
                pendingRecovery = !pauseAcknowledged && now - pausedSinceMs!! >= MIN_PAUSE_MS && still
            }
            TrackingState.BEATING -> {
                clearPause()
                pauseAcknowledged = false
            }
            // We can't see the arms, so this time can't count as part of a pause.
            TrackingState.LOST, TrackingState.WARMING -> clearPause()
        }
    }

    private fun clearPause() {
        pausedSinceMs = null
        pausedReads.clear()
        pendingRecovery = false
    }

    private fun wantedPhrase(reading: LiveReading, now: Long): Phrase? {
        if (reading.tracking == TrackingState.LOST || reading.tracking == TrackingState.WARMING) return null
        if (pendingRecovery) return Phrase.KEEP_GOING.takeIf { it != lastPhrase }
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
        return wanted.firstOrNull { it != lastPhrase }
    }

    private companion object {
        const val MIN_PAUSE_MS = 2_000L
    }
}
