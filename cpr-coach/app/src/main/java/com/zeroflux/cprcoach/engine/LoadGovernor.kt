package com.zeroflux.cprcoach.engine

/**
 * Chooses the minimum gap between pose frames from recent latency and battery-gated thermal status.
 * Pure Kotlin. Starts at the fastest of three steps, never below about 10 frames a second on a
 * 30 fps camera, because RateEstimator treats under 8 as no data. Steps down fast, up slowly.
 * Gaps leave margin below one, two and three camera frame intervals, yielding about 30, 15 and
 * 10 results a second before processing limits. A 100 ms gap risks waiting for a fourth frame.
 * A known battery temperature at or above 40 C enables thermal limits; CRITICAL or above also
 * enables them at any known temperature. Unknown temperature means latency-only control.
 * Not thread safe: call from one thread, or synchronise outside.
 */
class LoadGovernor(startStep: Int = 0) {
    var step: Int = startStep.coerceIn(0, GAPS_MS.lastIndex)
        private set
    val gapMs: Long get() = GAPS_MS[step]

    private val times = ArrayDeque<Long>()
    private val latencies = ArrayDeque<Long>()
    private var lastChangeMs = NEVER

    /**
     * Records one result and returns the (possibly new) minimum frame gap in ms.
     * Hot batteries impose at least the middle step, or the lowest at SEVERE and above.
     * With a known cooler battery, only CRITICAL and above impose the lowest step.
     * NaN temperature ignores thermal status entirely, including CRITICAL.
     */
    fun update(
        nowMs: Long,
        latencyMs: Long?,
        thermalStatus: Int,
        batteryTempC: Float = Float.NaN,
    ): Long {
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
        val thermalLimited = !batteryTempC.isNaN() &&
            (batteryTempC >= HOT_BATTERY_TEMP_C || thermalStatus >= THERMAL_CRITICAL)
        var target = step
        if (thermalLimited) {
            target = maxOf(step, if (thermalStatus >= THERMAL_SEVERE) GAPS_MS.lastIndex else 1)
        }
        if (enough && median > HIGH_LATENCY_MS && sinceChange >= DOWN_HOLD_MS) {
            target = maxOf(target, step + 1)
        }
        if (target > step) {
            move(target.coerceAtMost(GAPS_MS.lastIndex), nowMs)
        } else if (!thermalLimited && enough && median < LOW_LATENCY_MS &&
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
        val GAPS_MS = longArrayOf(30L, 60L, 90L)
        const val HOT_BATTERY_TEMP_C = 40f
        const val THERMAL_MODERATE = 2
        const val THERMAL_SEVERE = 3
        const val THERMAL_CRITICAL = 4
        const val HIGH_LATENCY_MS = 150L
        const val LOW_LATENCY_MS = 90L
        const val WINDOW_MS = 4_000L
        const val MAX_SAMPLES = 60
        const val MIN_SAMPLES = 8
        const val DOWN_HOLD_MS = 4_000L
        const val UP_HOLD_MS = 30_000L
    }
}
