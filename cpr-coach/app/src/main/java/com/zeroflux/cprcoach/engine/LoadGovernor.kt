package com.zeroflux.cprcoach.engine

/**
 * Chooses the minimum gap between pose frames from recent latency and thermal status.
 * Pure Kotlin. Three steps only, never below about 10 frames a second, because RateEstimator
 * treats under 8 as no data. Steps down fast, up slowly.
 * The camera delivers a frame about every 33 ms, so a gap really waits for the next frame after it:
 * these gaps give about 15, 12 and 10 a second. A gap of 100 ms would really mean 133 ms, 7.5 a second.
 * Not thread safe: call from one thread, or synchronise outside.
 */
class LoadGovernor(startStep: Int = 0) {
    var step: Int = startStep.coerceIn(0, GAPS_MS.lastIndex)
        private set
    val gapMs: Long get() = GAPS_MS[step]

    private val times = ArrayDeque<Long>()
    private val latencies = ArrayDeque<Long>()
    private var lastChangeMs = NEVER

    /** Records one result. Returns the (possibly new) minimum frame gap in ms. */
    fun update(nowMs: Long, latencyMs: Long?, thermalStatus: Int): Long {
        if (latencyMs != null && latencyMs >= 0) {
            times.addLast(nowMs)
            latencies.addLast(latencyMs)
        }
        while (times.isNotEmpty() && (nowMs - times.first() > WINDOW_MS || times.size > MAX_SAMPLES)) {
            times.removeFirst()
            latencies.removeFirst()
        }
        if (lastChangeMs == NEVER) lastChangeMs = nowMs
        val median = median()
        val enough = latencies.size >= MIN_SAMPLES
        val sinceChange = nowMs - lastChangeMs
        var target = step
        if (thermalStatus >= THERMAL_SEVERE) {
            target = GAPS_MS.lastIndex
        } else if (thermalStatus >= THERMAL_MODERATE) {
            target = maxOf(step, 1)
        } else if (enough && median > HIGH_LATENCY_MS && sinceChange >= DOWN_HOLD_MS) {
            target = step + 1
        }
        if (target > step) {
            move(target.coerceAtMost(GAPS_MS.lastIndex), nowMs)
        } else if (thermalStatus < THERMAL_MODERATE && enough && median < LOW_LATENCY_MS &&
            sinceChange >= UP_HOLD_MS && step > 0
        ) {
            move(step - 1, nowMs)
        }
        return gapMs
    }

    private fun move(newStep: Int, nowMs: Long) {
        if (newStep == step) return
        step = newStep
        lastChangeMs = nowMs
        // Old latencies belong to the old rate; judge the new step on fresh results.
        times.clear()
        latencies.clear()
    }

    private fun median(): Long =
        if (latencies.isEmpty()) 0L else latencies.sorted()[latencies.size / 2]

    companion object {
        private const val NEVER = Long.MIN_VALUE
        val GAPS_MS = longArrayOf(60L, 75L, 90L)
        const val THERMAL_MODERATE = 2
        const val THERMAL_SEVERE = 3
        const val HIGH_LATENCY_MS = 150L
        const val LOW_LATENCY_MS = 90L
        const val WINDOW_MS = 4_000L
        const val MAX_SAMPLES = 60
        const val MIN_SAMPLES = 8
        const val DOWN_HOLD_MS = 4_000L
        const val UP_HOLD_MS = 30_000L
    }
}
