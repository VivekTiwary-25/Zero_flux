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
 * cooldown between phrases, and the same phrase never plays twice in a row unless repeat praise is enabled.
 * While tracking is lost it stays silent, so the rescuer only hears the metronome.
 */
class CoachPolicy(
    private val steadyMs: Long = 3_000,
    private val postureSteadyMs: Long = 2_000,
    private val cooldownMs: Long = 6_000,
    private val praiseEveryMs: Long = 20_000,
    private val elbowSteadyMs: Long = 1_000,
    private val canSpeak: () -> Boolean = { true },
    private val praiseOnCorrection: Boolean = false,
    private val repeatPraise: Boolean = false,
    private val rateRelockWithinSteady: Boolean = false,
) {
    // When each currently wanted phrase started to be wanted without a break; a phrase that drops out loses its time.
    private val wantedSinceMs = java.util.EnumMap<Phrase, Long>(Phrase::class.java)
    private var lastSpokenMs: Long? = null
    private var lastPhrase: Phrase? = null
    private var pausedSinceMs: Long? = null
    private val pausedReads = ArrayDeque<Pair<Long, Boolean>>()
    private var pendingRecovery = false
    private var pauseAcknowledged = false
    // A pause already confirmed (2 s still) survives a short LOST gap, so Keep going isn't lost while waiting for cooldown.
    private var pauseConfirmed = false
    private var lastPausedReadMs = 0L
    private var pushingSinceMs: Long? = null
    private var pushedSinceCue = false
    private var absentSinceMs: Long? = null
    private var repeatAllowed = false
    private var absentReads = 0

    /** Returns the phrase to play now, or null to stay quiet. Still observes reads while speech is busy. */
    fun update(reading: LiveReading): Phrase? {
        val now = reading.timeMs
        trackPause(reading)

        val wanted = wantedPhrases(reading, now)
        wantedSinceMs.keys.retainAll(wanted.toSet())
        for (p in wanted) wantedSinceMs.putIfAbsent(p, now)
        // The highest-priority phrase that has itself been wanted long enough. A higher one still waiting holds back
        // a lower one only if it has been wanted at least as long, so a flicker in a rate phrase can't starve a posture one.
        var pendingSince = Long.MAX_VALUE
        var phrase: Phrase? = null
        for (p in wanted) {
            val since = wantedSinceMs.getValue(p)
            if (now - since >= requiredMs(p) && since < pendingSince &&
                (p != Phrase.PUSH_FASTER && p != Phrase.SLOW_DOWN || rateRelocked(now))) {
                phrase = p
                break
            }
            pendingSince = minOf(pendingSince, since)
        }
        if (phrase == null) return null
        lastSpokenMs?.let { if (now - it < cooldownMs) return null }
        if (!canSpeak()) return null

        lastSpokenMs = now
        lastPhrase = phrase
        absentSinceMs = null
        repeatAllowed = false
        absentReads = 0
        wantedSinceMs.clear()
        if (phrase == Phrase.KEEP_GOING) {
            pendingRecovery = false
            pauseConfirmed = false
            pauseAcknowledged = true
            pushedSinceCue = false
        }
        return phrase
    }

    // Recovery and enabled praise can be immediate; corrections must still hold steady.
    private fun requiredMs(phrase: Phrase) = when (phrase) {
        Phrase.KEEP_GOING -> 0
        // Elbows are already sustained by the judge; keep the shoulder voice timing unchanged.
        Phrase.ELBOWS_STRAIGHT -> elbowSteadyMs
        Phrase.SHOULDERS_OVER_HANDS -> postureSteadyMs
        Phrase.GOOD -> if (praiseFollowsCorrection() || (repeatPraise && lastSpokenMs != null)) 0 else steadyMs
        else -> steadyMs
    }

    private fun praiseFollowsCorrection() = praiseOnCorrection && when (lastPhrase) {
        Phrase.PUSH_FASTER, Phrase.SLOW_DOWN, Phrase.ELBOWS_STRAIGHT, Phrase.SHOULDERS_OVER_HANDS -> true
        else -> false
    }

    private fun rateRelocked(now: Long) =
        lastPausedReadMs == 0L || now - lastPausedReadMs >= STILL_NOW_MS

    /**
     * A pause is PAUSED for MIN_PAUSE_MS without a break, and mostly still. Wrist noise (sitting up, resting hands)
     * makes `moving` flicker, so a few moving reads are fine; pushing without a locked rate is moving nearly always.
     */
    private fun trackPause(reading: LiveReading) {
        when (reading.tracking) {
            TrackingState.PAUSED -> {
                val now = reading.timeMs
                if (now - lastPausedReadMs > MIN_PAUSE_MS) pauseConfirmed = false
                lastPausedReadMs = now
                // Credit a still beat tail only after a full second. A brief detector dropout
                // before PAUSED must not bring a short reposition's cue forward.
                if (pausedReads.isEmpty() && !pauseConfirmed &&
                    pausedSinceMs?.let { now - it < STILL_NOW_MS } == true) pausedSinceMs = null
                pausedReads.addLast(now to reading.moving)
                while (now - pausedReads.first().first > MIN_PAUSE_MS) pausedReads.removeFirst()
                // A sustained moving run ends the old stop, even while the window is mostly still.
                if (reading.moving) {
                    var movingSince = now
                    for (i in pausedReads.lastIndex downTo 0) {
                        if (!pausedReads[i].second) break
                        movingSince = pausedReads[i].first
                    }
                    if (now - movingSince >= BREAK_PAUSE_MS) {
                        pausedSinceMs = null
                        pauseConfirmed = false
                        while (pausedReads.first().first < movingSince) pausedReads.removeFirst()
                    }
                }
                val still = pausedReads.count { !it.second } * 2 >= pausedReads.size
                if (!still) {
                    // Mostly moving: the rescuer is pushing without a locked rate, so there is no pause to time.
                    pausedSinceMs = null
                    pauseConfirmed = false
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
                    (pauseConfirmed || now - pausedSinceMs!! >= MIN_PAUSE_MS)
                if (pendingRecovery) pauseConfirmed = true
            }
            TrackingState.BEATING -> {
                pendingRecovery = false
                pausedReads.clear()
                pauseConfirmed = false
                pauseAcknowledged = false
                // The beat can linger after the hands stop. Time that stillness without
                // changing tracking or the rate, so an ordinary stop is coached promptly.
                if (reading.moving) pausedSinceMs = null
                else if (pausedSinceMs == null) pausedSinceMs = reading.timeMs
                // A locked beat bridges movement-detector dropouts, but still needs moving hands
                // at both ends before it can re-arm a second Keep going.
                val now = reading.timeMs
                if (reading.moving) {
                    if (pushingSinceMs == null) pushingSinceMs = now
                    if (now - pushingSinceMs!! >= MIN_PUSH_MS) pushedSinceCue = true
                }
            }
            // A confirmed stop survives a short visibility gap, not a new moving read.
            TrackingState.LOST, TrackingState.WARMING -> {
                val recentStop = pauseConfirmed && reading.timeMs - lastPausedReadMs in 0..STILL_NOW_MS
                pendingRecovery = recentStop && !reading.moving && !pauseAcknowledged &&
                    reading.tracking == TrackingState.WARMING
                if (!recentStop || reading.moving) {
                    clearPause()
                    pauseConfirmed = false
                }
            }
        }
        if (reading.tracking != TrackingState.BEATING && reading.tracking != TrackingState.PAUSED) pushingSinceMs = null
    }

    private fun clearPause() {
        pausedSinceMs = null
        pausedReads.clear()
        pendingRecovery = false
    }

    /** Phrases wanted right now, highest priority first. */
    private fun wantedPhrases(reading: LiveReading, now: Long): List<Phrase> {
        if (reading.tracking == TrackingState.LOST) return emptyList()
        // Give short repositions time to reveal resumed movement before speaking.
        if (pendingRecovery && pausedSinceMs?.let { now - it >= MIN_RECOVERY_MS } == true) {
            return if (lastPhrase != Phrase.KEEP_GOING || pushedSinceCue) listOf(Phrase.KEEP_GOING) else emptyList()
        }
        if (reading.tracking == TrackingState.WARMING) return emptyList()
        val beating = reading.tracking == TrackingState.BEATING
        // Bent or leaning pushing often has no locked rate, so posture is also coached while PAUSED and moving.
        if (!beating && !reading.moving) return emptyList()

        // Emergency can count the relock guard inside steadiness, but emission still waits for the guard.
        val rate = reading.ratePerMinute?.takeIf {
            beating && (rateRelockWithinSteady || rateRelocked(now))
        }
        val wanted = ArrayList<Phrase>(3)
        if (rate != null && rate < LiveReading.TARGET_MIN) wanted.add(Phrase.PUSH_FASTER)
        if (rate != null && rate > LiveReading.TARGET_MAX) wanted.add(Phrase.SLOW_DOWN)
        // Posture names the issue; NOT_SIDE_ON stays silent in voice.
        val issue = reading.posture?.issue
        val elbowsBent = if (reading.posture != null) issue == PostureIssue.ELBOWS_BENT else reading.elbowsBent == true
        if (elbowsBent) wanted.add(Phrase.ELBOWS_STRAIGHT)
        if (issue == PostureIssue.SHOULDERS_NOT_OVER_HANDS) wanted.add(Phrase.SHOULDERS_OVER_HANDS)
        if (wanted.isEmpty() && beating && reading.inRange == true &&
            (praiseFollowsCorrection() || (lastSpokenMs?.let { now - it >= praiseEveryMs } ?: true))) wanted.add(Phrase.GOOD)
        // A phrase may repeat only after its condition has been gone for a while (it was fixed, then came back).
        val last = lastPhrase
        if (last == null || last in wanted) { absentSinceMs = null; absentReads = 0 }
        else {
            if (absentSinceMs == null) absentSinceMs = now
            absentReads++
        }
        if (absentReads >= CLEAR_READS && absentSinceMs?.let { now - it >= CLEAR_MS } == true) repeatAllowed = true
        return wanted.filter { it != last || (repeatAllowed && it != Phrase.GOOD) || (repeatPraise && it == Phrase.GOOD) }
    }

    private companion object {
        const val MIN_PAUSE_MS = 2_000L
        const val MIN_RECOVERY_MS = 2_500L
        const val BREAK_PAUSE_MS = 750L
        const val STILL_NOW_MS = 1_000L
        const val MIN_PUSH_MS = 3_000L
        const val CLEAR_MS = 3_000L
        const val CLEAR_READS = 8
    }
}
