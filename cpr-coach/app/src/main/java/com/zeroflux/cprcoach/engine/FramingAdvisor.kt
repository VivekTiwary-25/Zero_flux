package com.zeroflux.cprcoach.engine

import kotlin.math.sqrt

private const val MIN_HINT_MS = 500L
private const val EDGE_MARGIN = 0.03f
private const val MAX_TORSO_DISTANCE = 0.6f

data class ArmLandmarks(
    val shoulderX: Float,
    val shoulderY: Float,
    val shoulderVis: Float,
    val elbowX: Float,
    val elbowY: Float,
    val elbowVis: Float,
    val wristX: Float,
    val wristY: Float,
    val wristVis: Float,
    val hipX: Float,
    val hipY: Float,
    val hipVis: Float,
)

data class FramingAdvice(
    val ready: Boolean,
    val hintKey: String?,
)

class FramingAdvisor(
    private val minVisibility: Float = 0.5f,
    private val holdMs: Long = 1_000,
) {
    private var currentHint: String? = null
    private var hintDisplayedSinceMs: Long = 0L
    private var holdStartedMs: Long? = null

    fun update(nearArm: ArmLandmarks?, timeMs: Long): FramingAdvice {
        val rawHint = evaluateHint(nearArm)

        if (rawHint != null) {
            holdStartedMs = null
            if (currentHint == null) {
                currentHint = rawHint
                hintDisplayedSinceMs = timeMs
            } else if (rawHint != currentHint && (timeMs - hintDisplayedSinceMs >= MIN_HINT_MS)) {
                currentHint = rawHint
                hintDisplayedSinceMs = timeMs
            }
            return FramingAdvice(ready = false, hintKey = currentHint)
        }

        // No hint applies to the current frame.
        if (currentHint != null && (timeMs - hintDisplayedSinceMs >= MIN_HINT_MS)) {
            currentHint = null
        }

        if (currentHint == null) {
            if (holdStartedMs == null) {
                holdStartedMs = timeMs
            }
            val ready = (timeMs - holdStartedMs!!) >= holdMs
            return FramingAdvice(ready = ready, hintKey = null)
        } else {
            // Anti-flicker: hint must stay displayed for at least 500 ms.
            holdStartedMs = null
            return FramingAdvice(ready = false, hintKey = currentHint)
        }
    }

    private fun evaluateHint(arm: ArmLandmarks?): String? {
        if (arm == null) return "frame_no_person"

        if (arm.wristVis < minVisibility || isNearEdge(arm.wristX, arm.wristY)) {
            return "frame_arm_out"
        }

        if (arm.hipVis < minVisibility || isNearEdge(arm.hipX, arm.hipY)) {
            return "frame_hip_out"
        }

        if (arm.shoulderVis < minVisibility || arm.elbowVis < minVisibility) {
            return "frame_side_on"
        }

        val dx = arm.shoulderX - arm.hipX
        val dy = arm.shoulderY - arm.hipY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > MAX_TORSO_DISTANCE) {
            return "frame_move_back"
        }

        return null
    }

    private fun isNearEdge(x: Float, y: Float): Boolean {
        return x <= EDGE_MARGIN || x >= (1f - EDGE_MARGIN) || y <= EDGE_MARGIN || y >= (1f - EDGE_MARGIN)
    }
}
