package com.zeroflux.cprcoach.engine

/** Joint order used by [OverlayPose.Out]. */
object OverlayJoint {
    const val SHOULDER = 0
    const val ELBOW = 1
    const val WRIST = 2
    const val HIP = 3
    const val COUNT = 4
}

/**
 * Smoothed near-arm positions for the camera overlay. The pose thread calls [push] 20-24 times a
 * second; the UI calls [at] every display frame (60 Hz) and gets positions nudged a little past
 * the last sample along the recent motion, so the skeleton glides instead of stepping.
 * Thread safe. Coordinates are normalised image coordinates.
 */
class OverlayPose(
    private val minVisibility: Float = MIN_VISIBILITY,
) {
    /** Reused by the caller to avoid allocating every display frame. */
    class Out {
        val x = FloatArray(OverlayJoint.COUNT)
        val y = FloatArray(OverlayJoint.COUNT)

        /** 0 hidden .. 1 fully shown. */
        var alpha = 0f
    }

    private val lock = Any()
    private val filters = Array(OverlayJoint.COUNT * 2) { OneEuroFilter(MIN_CUTOFF_HZ, BETA, DERIVATIVE_CUTOFF_HZ) }
    private val last = FloatArray(OverlayJoint.COUNT * 2)
    private val prev = FloatArray(OverlayJoint.COUNT * 2)
    private val scratch = FloatArray(OverlayJoint.COUNT * 2)
    private var lastFrameMs = 0L
    private var prevFrameMs = 0L
    private var samples = 0
    private var lastArrivalMs = 0L
    private var present = false
    private var fadeStartMs = 0L

    /** Width over height of the image the landmarks are normalised to; set by [push]. */
    @Volatile
    var aspect = 4f / 3f
        private set

    /**
     * [arrivalMs] is the clock the UI passes to [at] (SystemClock.uptimeMillis()); the frame's own
     * time is only used for velocity and filtering.
     */
    fun push(frame: PoseFrame, arrivalMs: Long = frame.timeMs) {
        val arm = frame.arm
        if (frame.aspect.isFinite() && frame.aspect > 0f) aspect = frame.aspect
        synchronized(lock) {
            if (arm == null || !fill(arm)) {
                if (present) {
                    present = false
                    fadeStartMs = arrivalMs
                }
                return
            }
            // After a gap the old track is meaningless: start afresh instead of gliding from it.
            if (samples == 0 || (!present && arrivalMs - lastArrivalMs > RESTART_GAP_MS)) {
                for (f in filters) f.reset()
                samples = 0
            }
            val t = frame.timeMs
            if (samples > 0 && t <= lastFrameMs) return
            for (i in scratch.indices) {
                val old = last[i]
                last[i] = filters[i].filter(t, scratch[i].toDouble()).toFloat()
                prev[i] = if (samples == 0) last[i] else old
            }
            prevFrameMs = if (samples == 0) t else lastFrameMs
            lastFrameMs = t
            samples++
            lastArrivalMs = arrivalMs
            present = true
        }
    }

    private fun fill(arm: ArmLandmarks): Boolean {
        if (!ok(arm.shoulderX, arm.shoulderY, arm.shoulderVis, 0)) return false
        if (!ok(arm.elbowX, arm.elbowY, arm.elbowVis, 1)) return false
        if (!ok(arm.wristX, arm.wristY, arm.wristVis, 2)) return false
        return ok(arm.hipX, arm.hipY, arm.hipVis, 3)
    }

    private fun ok(x: Float, y: Float, vis: Float, joint: Int): Boolean {
        if (!x.isFinite() || !y.isFinite() || !(vis >= minVisibility)) return false
        scratch[joint * 2] = x
        scratch[joint * 2 + 1] = y
        return true
    }

    /** True while there may be something to draw at [nowMs]. */
    fun isVisible(nowMs: Long): Boolean = synchronized(lock) { samples > 0 && alphaAt(nowMs) > 0f }

    /** Fills [out]; returns false (and leaves it untouched) when nothing should be drawn. */
    fun at(nowMs: Long, out: Out): Boolean = synchronized(lock) {
        if (samples == 0) return false
        val alpha = alphaAt(nowMs)
        if (alpha <= 0f) return false
        val interval = (lastFrameMs - prevFrameMs).coerceAtLeast(MIN_INTERVAL_MS)
        val ahead = (nowMs - lastArrivalMs).coerceIn(0L, MAX_AHEAD_MS)
        // Recent velocity applied for at most MAX_AHEAD_MS past the last sample.
        val k = if (samples < 2) 0f else ahead.toFloat() / interval
        for (j in 0 until OverlayJoint.COUNT) {
            val xi = j * 2
            val yi = xi + 1
            out.x[j] = last[xi] + (last[xi] - prev[xi]) * k
            out.y[j] = last[yi] + (last[yi] - prev[yi]) * k
        }
        out.alpha = alpha
        true
    }

    fun at(nowMs: Long): Out? = Out().takeIf { at(nowMs, it) }

    private fun alphaAt(nowMs: Long): Float {
        val start = if (present) lastArrivalMs + STALE_MS else fadeStartMs
        val elapsed = nowMs - start
        if (elapsed <= 0L) return 1f
        return (1f - elapsed.toFloat() / FADE_MS).coerceIn(0f, 1f)
    }

    companion object {
        const val MIN_VISIBILITY = 0.3f
        const val MAX_AHEAD_MS = 80L
        const val FADE_MS = 300L

        /** Without new frames for this long the skeleton starts to fade rather than freeze. */
        const val STALE_MS = 400L
        private const val RESTART_GAP_MS = 500L
        private const val MIN_INTERVAL_MS = 15L

        // Normalised coordinates move about 1 unit/s at most, so beta is large against the default.
        private const val MIN_CUTOFF_HZ = 2.5
        private const val BETA = 12.0
        private const val DERIVATIVE_CUTOFF_HZ = 3.0
    }
}

/** Landmark to view pixel mapping for a FILL_CENTER preview of an image of aspect width/height. */
object OverlayMapping {
    private fun shownWidth(viewW: Float, viewH: Float, aspect: Float) =
        if (viewW / viewH > aspect) viewW else viewH * aspect

    private fun shownHeight(viewW: Float, viewH: Float, aspect: Float) =
        if (viewW / viewH > aspect) viewW / aspect else viewH

    fun mapX(nx: Float, viewW: Float, viewH: Float, aspect: Float): Float {
        val shown = shownWidth(viewW, viewH, aspect)
        return (viewW - shown) / 2f + nx * shown
    }

    fun mapY(ny: Float, viewW: Float, viewH: Float, aspect: Float): Float {
        val shown = shownHeight(viewW, viewH, aspect)
        return (viewH - shown) / 2f + ny * shown
    }

    fun valid(viewW: Float, viewH: Float, aspect: Float) =
        viewW > 0f && viewH > 0f && aspect.isFinite() && aspect > 0f
}
