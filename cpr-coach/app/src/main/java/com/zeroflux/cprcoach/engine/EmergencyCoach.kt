package com.zeroflux.cprcoach.engine

/** The one instruction the Emergency screen shows. Keys are in assets/strings/<language>.json. */
enum class EmergencyWord(val key: String) {
    KEEP_PUSHING("em_word_keep_pushing"),
    FASTER("em_word_faster"),
    SLOWER("em_word_slower"),
    STRAIGHTEN("em_word_straighten"),
    STRAIGHTEN_ARMS("posture_straighten_arms"),
    SHOULDERS_OVER_HANDS("posture_shoulders_over_hands"),
    GOOD("em_word_good"),
}

/** For the ambulance crew. A null value was never measured, and the UI must show a dash. */
data class HandoverSummary(
    val totalSeconds: Int,
    /** Seconds with a real rate, which can be far less than [totalSeconds]. */
    val measuredSeconds: Int,
    val averageRate: Int?,
    val pauses: Int?,
)

/**
 * Turns camera readings into one word, spoken corrections and the handover summary.
 * Emergency coaching never depends on this: with no readings the word stays on keep pushing.
 * Call [update] about four times a second with one clock. All calls must come from one thread.
 */
class EmergencyCoach(private val startMs: Long, private val holdMs: Long = 1_000) {
    private val policy = CoachPolicy()
    private val metrics = SessionMetrics()
    private var armsSeen = false
    private var lostSinceMs: Long? = null
    private var lastUpdateMs = startMs
    private var lastBeatingMs: Long? = null
    private var beatingSinceMs: Long? = null
    private var candidate = EmergencyWord.KEEP_PUSHING
    private var candidateSinceMs = startMs

    var word = EmergencyWord.KEEP_PUSHING
        private set

    /** True until the camera has seen the arms, and again once they have been lost for over 3 s. */
    val needsSetup: Boolean
        get() {
            if (!armsSeen) return true
            val lostSince = lostSinceMs ?: return false
            return lastUpdateMs - lostSince > SETUP_AFTER_LOST_MS
        }

    /** True while the phone is clearly not side-on: show the setup hint, not a word. */
    var turnPhoneHint = false
        private set

    fun update(reading: LiveReading) {
        turnPhoneHint = reading.posture?.issue == PostureIssue.NOT_SIDE_ON
        metrics.add(reading)
        lastUpdateMs = reading.timeMs
        if (reading.tracking == TrackingState.LOST) {
            if (lostSinceMs == null) lostSinceMs = reading.timeMs
        } else {
            armsSeen = true
            lostSinceMs = null
        }
        val wanted = wordFor(reading)
        if (reading.tracking == TrackingState.BEATING && reading.ratePerMinute != null) {
            if (beatingSinceMs == null) beatingSinceMs = reading.timeMs
            lastBeatingMs = reading.timeMs
        } else if (lastBeatingMs?.let { reading.timeMs - it > KEEP_WORD_MS } != false) {
            beatingSinceMs = null
        }
        if (wanted != candidate) {
            candidate = wanted
            candidateSinceMs = reading.timeMs
        }
        // An instruction must not outlive the reading behind it, but a new one has to hold or it flickers.
        if (wanted == EmergencyWord.KEEP_PUSHING || reading.timeMs - candidateSinceMs >= holdMs) word = wanted
    }

    /** Ask only when no voice clip is playing or waiting, so a correction is never stale when heard. */
    fun nextCorrection(reading: LiveReading): Phrase? = policy.update(reading)

    fun summary(nowMs: Long): HandoverSummary {
        val card = metrics.scorecard()
        // A moment of beat can come from a passing shape. A crew must not get a rate from that.
        val enough = card.measuredSeconds >= MIN_MEASURED_SECONDS
        return HandoverSummary(
            totalSeconds = Math.round((nowMs - startMs).coerceAtLeast(0L) / 1000.0).toInt(),
            measuredSeconds = card.measuredSeconds,
            averageRate = if (enough) card.averageRate else null,
            pauses = if (enough) card.pauses else null,
        )
    }

    private fun wordFor(reading: LiveReading): EmergencyWord {
        val rate = reading.ratePerMinute
        // Bent or leaning pushing often has no locked rate, so posture also counts while PAUSED and moving.
        // A locked rate that is off target still ranks first, then posture, then good.
        val beating = reading.tracking == TrackingState.BEATING && rate != null
        val pushing = beating || (reading.tracking == TrackingState.PAUSED && reading.moving)
        if (pushing && (rate == null || rate in LiveReading.TARGET_MIN..LiveReading.TARGET_MAX)) {
            postureWord(reading)?.let { return it }
        }
        if (reading.tracking != TrackingState.BEATING || rate == null) {
            // Still pushing but the rate has dropped out for a moment: keep the word rather than flash "keep pushing".
            val beatingAt = lastBeatingMs
            val since = beatingSinceMs
            // Only a beat that held for a while is kept: a phantom blip must not linger on screen.
            val recently = beatingAt != null && since != null && reading.timeMs - beatingAt in 0..KEEP_WORD_MS &&
                beatingAt - since >= ESTABLISHED_BEAT_MS
            // A held posture word is not kept here: it must end when the judge's verdict does.
            val keep = word != EmergencyWord.STRAIGHTEN_ARMS && word != EmergencyWord.SHOULDERS_OVER_HANDS && word != EmergencyWord.STRAIGHTEN
            return if (reading.moving && reading.tracking != TrackingState.LOST && recently && keep) word else EmergencyWord.KEEP_PUSHING
        }
        return when {
            rate < LiveReading.TARGET_MIN -> EmergencyWord.FASTER
            rate > LiveReading.TARGET_MAX -> EmergencyWord.SLOWER
            else -> EmergencyWord.GOOD
        }
    }

    private fun postureWord(reading: LiveReading): EmergencyWord? = when {
        reading.posture?.issue == PostureIssue.ELBOWS_BENT -> EmergencyWord.STRAIGHTEN_ARMS
        reading.posture?.issue == PostureIssue.SHOULDERS_NOT_OVER_HANDS -> EmergencyWord.SHOULDERS_OVER_HANDS
        reading.posture == null && reading.elbowsBent == true -> EmergencyWord.STRAIGHTEN
        else -> null
    }

    private companion object {
        const val MIN_MEASURED_SECONDS = 10
        const val KEEP_WORD_MS = 3_000L
        const val ESTABLISHED_BEAT_MS = 3_000L
        const val SETUP_AFTER_LOST_MS = 3_000L
    }
}
