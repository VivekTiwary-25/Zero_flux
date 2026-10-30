package com.zeroflux.cprcoach.engine

/** The one instruction the Emergency screen shows. Keys are in assets/strings/<language>.json. */
enum class EmergencyWord(val key: String) {
    KEEP_PUSHING("em_word_keep_pushing"),
    FASTER("em_word_faster"),
    SLOWER("em_word_slower"),
    STRAIGHTEN("em_word_straighten"),
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
class EmergencyCoach(private val startMs: Long, private val holdMs: Long = 2_000) {
    private val policy = CoachPolicy()
    private val metrics = SessionMetrics()
    private var armsSeen = false
    private var candidate = EmergencyWord.KEEP_PUSHING
    private var candidateSinceMs = startMs

    var word = EmergencyWord.KEEP_PUSHING
        private set

    /** True until the camera has seen the arms, so the setup hint can stay up. */
    val needsSetup: Boolean get() = !armsSeen

    fun update(reading: LiveReading) {
        metrics.add(reading)
        if (reading.tracking == TrackingState.PAUSED || reading.tracking == TrackingState.BEATING) armsSeen = true
        val wanted = wordFor(reading)
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
        if (reading.tracking != TrackingState.BEATING || rate == null) return EmergencyWord.KEEP_PUSHING
        return when {
            rate < LiveReading.TARGET_MIN -> EmergencyWord.FASTER
            rate > LiveReading.TARGET_MAX -> EmergencyWord.SLOWER
            reading.elbowsBent == true -> EmergencyWord.STRAIGHTEN
            else -> EmergencyWord.GOOD
        }
    }

    private companion object {
        const val MIN_MEASURED_SECONDS = 10
    }
}
