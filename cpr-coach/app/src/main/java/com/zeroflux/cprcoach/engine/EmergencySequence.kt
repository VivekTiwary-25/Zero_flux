package com.zeroflux.cprcoach.engine

/**
 * Clip keys for emergency mode. The caller passes time in, so nothing here reads a clock.
 *
 * [start] returns em_call, then em_hands, then em_push, and begins the session.
 * [tick] returns clips that are due and not yet spoken, in the order they became due:
 * em_aed once at 30 seconds, em_continue at 60 seconds, and em_swap every 2 minutes.
 * A tick that crosses several swap marks speaks only the latest one.
 */
class EmergencySequence {
    private var startMs: Long? = null
    private var aedSpoken = false
    private var continueSpoken = false
    /** Number of 2-minute marks already acknowledged. 0 until the first swap is due. */
    private var lastSwapIndex = 0L

    fun start(nowMs: Long): List<String> {
        startMs = nowMs
        aedSpoken = false
        continueSpoken = false
        lastSwapIndex = 0L
        return OPENING
    }

    fun tick(nowMs: Long): List<String> {
        val started = startMs ?: return emptyList()
        val elapsed = nowMs - started
        if (elapsed < 0L) return emptyList()

        val speakAed = !aedSpoken && elapsed >= AED_AT_MS
        val speakContinue = !continueSpoken && elapsed >= CONTINUE_AT_MS
        val swapIndex = elapsed / SWAP_EVERY_MS
        val speakSwap = swapIndex > lastSwapIndex
        if (!speakAed && !speakContinue && !speakSwap) return emptyList()

        if (speakAed) aedSpoken = true
        if (speakContinue) continueSpoken = true
        if (speakSwap) lastSwapIndex = swapIndex

        val due = ArrayList<String>(3)
        if (speakAed) due.add(AED)
        if (speakContinue) due.add(CONTINUE)
        if (speakSwap) due.add(SWAP)
        return due
    }

    private companion object {
        /** One AED reminder, at 30 seconds. */
        const val AED_AT_MS = 30_000L

        /** Keep-going prompt, at 60 seconds. */
        const val CONTINUE_AT_MS = 60_000L

        /** Rescuer swap every 2 minutes after start. */
        const val SWAP_EVERY_MS = 2L * 60L * 1_000L

        const val AED = "em_aed"
        const val CONTINUE = "em_continue"
        const val SWAP = "em_swap"

        val OPENING = listOf("em_call", "em_hands", "em_push")
    }
}
