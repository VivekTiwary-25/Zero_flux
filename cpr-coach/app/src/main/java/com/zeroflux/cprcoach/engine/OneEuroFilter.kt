package com.zeroflux.cprcoach.engine

import kotlin.math.PI
import kotlin.math.abs

/**
 * The 1 Euro filter: steady when a value barely moves, quick when it moves fast, so a joint on
 * the overlay neither jitters nor lags. Not thread safe.
 */
class OneEuroFilter(
    private val minCutoffHz: Double = 1.5,
    private val beta: Double = 0.02,
    private val derivativeCutoffHz: Double = 1.0,
) {
    private var lastMs: Long? = null
    private var lastValue = 0.0
    private var lastDerivative = 0.0

    fun filter(timeMs: Long, value: Double): Double {
        val previous = lastMs
        if (previous == null || timeMs <= previous || !value.isFinite()) {
            if (value.isFinite()) {
                lastMs = timeMs
                lastValue = value
                lastDerivative = 0.0
            }
            return lastValue
        }
        val dt = (timeMs - previous) / 1000.0
        val derivative = (value - lastValue) / dt
        lastDerivative += alpha(derivativeCutoffHz, dt) * (derivative - lastDerivative)
        val cutoff = minCutoffHz + beta * abs(lastDerivative)
        lastValue += alpha(cutoff, dt) * (value - lastValue)
        lastMs = timeMs
        return lastValue
    }

    fun reset() {
        lastMs = null
        lastValue = 0.0
        lastDerivative = 0.0
    }

    private fun alpha(cutoffHz: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoffHz)
        return 1.0 / (1.0 + tau / dt)
    }
}
