package com.zeroflux.cprcoach.engine

/** Keys match the clip files in assets/clips/<language>/<key>.wav */
enum class Phrase(val key: String) {
    KEEP_GOING("keep_going"),
    PUSH_FASTER("push_faster"),
    SLOW_DOWN("slow_down"),
    ELBOWS_STRAIGHT("elbows_straight"),
    GOOD("good"),
}

/**
 * Picks at most one spoken correction. A correction must hold steady before it plays, there is a
 * cooldown between phrases, and the same phrase never plays twice in a row.
 * While tracking is lost it stays silent, so the rescuer only hears the metronome.
 */
class CoachPolicy(
    private val steadyMs: Long = 3_000,
    private val cooldownMs: Long = 6_000,
    private val praiseEveryMs: Long = 20_000,
) {
    private var candidate: Phrase? = null
    private var candidateSinceMs = 0L
    private var lastSpokenMs: Long? = null
    private var lastPhrase: Phrase? = null
    private var pausedSinceMs: Long? = null
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
        val requiredMs = if (phrase == Phrase.KEEP_GOING) 0 else steadyMs
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

    private fun trackPause(reading: LiveReading) {
        when (reading.tracking) {
            TrackingState.PAUSED -> {
                if (pausedSinceMs == null) pausedSinceMs = reading.timeMs
                if (reading.timeMs - pausedSinceMs!! >= MIN_PAUSE_MS && !pauseAcknowledged) pendingRecovery = true
            }
            TrackingState.BEATING -> {
                pausedSinceMs = null
                pendingRecovery = false
                pauseAcknowledged = false
            }
            // We can't see the arms, so this time can't count as part of a pause.
            TrackingState.LOST, TrackingState.WARMING -> pausedSinceMs = null
        }
    }

    private fun wantedPhrase(reading: LiveReading, now: Long): Phrase? {
        if (reading.tracking == TrackingState.LOST || reading.tracking == TrackingState.WARMING) return null
        if (pendingRecovery) return Phrase.KEEP_GOING.takeIf { it != lastPhrase }
        if (reading.tracking != TrackingState.BEATING) return null

        val rate = reading.ratePerMinute ?: return null
        val wanted = ArrayList<Phrase>(3)
        if (rate < LiveReading.TARGET_MIN) wanted.add(Phrase.PUSH_FASTER)
        if (rate > LiveReading.TARGET_MAX) wanted.add(Phrase.SLOW_DOWN)
        if (reading.elbowsBent == true) wanted.add(Phrase.ELBOWS_STRAIGHT)
        if (wanted.isEmpty() && (lastSpokenMs?.let { now - it >= praiseEveryMs } ?: true)) wanted.add(Phrase.GOOD)
        return wanted.firstOrNull { it != lastPhrase }
    }

    private companion object {
        const val MIN_PAUSE_MS = 2_000L
    }
}
