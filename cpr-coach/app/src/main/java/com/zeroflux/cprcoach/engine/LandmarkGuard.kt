package com.zeroflux.cprcoach.engine

import kotlin.math.abs
import kotlin.math.hypot

/** What [LandmarkGuard] decided about one frame. Everything that is [accepted] may be used. */
enum class GuardVerdict(val accepted: Boolean) {
    /** Plausible, or nothing to check (no arm, or joints too poorly seen to judge). */
    ACCEPT(true),

    /** The arm side changed: the reference was dropped and this frame starts a new one. */
    SIDE_SWITCH(true),

    /** Rejected frames agreed with each other for long enough: the rescuer really moved. */
    REANCHOR(true),

    /** Wrist or elbow moved too far sideways since the last accepted frame (MediaPipe's wrist flips). */
    JUMP(false),

    /** Wrist or elbow is far from where it has been for the last ~1.5 s. */
    DRIFT(false),

    /** Upper arm or forearm length is far from its slow reference. */
    BONE_LENGTH(false),

    /** Wrist or elbow behind the shoulder, or the wrist above the shoulder. */
    CROSSED(false),
}

/**
 * Decides per frame whether the arm landmarks are plausible. MediaPipe's wrist sometimes flips
 * frame to frame between the real hand and a phantom straight below the elbow, so a bent arm reads
 * straight. Positions are taken relative to the hip in torso lengths (x scaled by the image aspect),
 * which cancels hand-held camera shake and distance, and compared with the last ACCEPTED frame, so a
 * run of phantom frames stays rejected. Rejected frames carry no information: drop them entirely.
 * If frames are rejected for [REANCHOR_MS] and agree with each other (or for [FORCE_MS] whatever they do), the new place is accepted.
 * Pure Kotlin, one thread. Numbers measured on recordings 1-15 (see LandmarkGuardTest, PostureReplayTest).
 */
class LandmarkGuard {
    private class Rel(val t: Long, val wx: Double, val wy: Double, val ex: Double)
    private class Bones(val t: Long, val upper: Double, val fore: Double)

    private var ref: Rel? = null
    private val slow = ArrayDeque<Rel>()
    private val bones = ArrayDeque<Bones>()
    private val rejected = ArrayList<Rel>()

    /** Rejected frames of the last [MAJORITY_MS], kept across accepted frames, and the accepted ones' times. */
    private val pool = ArrayList<Rel>()
    private val acceptedTimes = ArrayDeque<Long>()
    private var facing = 0
    private var nearRight: Boolean? = null
    private var lastArm: ArmLandmarks? = null

    /** Frames judged (not counting pass-through), and how many were rejected, for the replay report. */
    var judged = 0
        private set
    var rejects = 0
        private set

    fun reset() {
        ref = null
        slow.clear()
        bones.clear()
        rejected.clear()
        pool.clear()
        acceptedTimes.clear()
        facing = 0
        lastArm = null
    }

    /** For the camera overlay: the frame itself when accepted, else the same frame showing the last accepted arm. */
    fun overlayFrame(frame: PoseFrame): PoseFrame {
        val arm = frame.arm
        if (check(frame).accepted || arm == null) {
            lastArm = arm
            return frame
        }
        // No held arm (startup, or after a frame with no person): show none rather than the rejected one.
        return frame.copy(arm = lastArm)
    }

    fun check(frame: PoseFrame): GuardVerdict {
        val arm = frame.arm ?: return GuardVerdict.ACCEPT
        val aspect = frame.aspect.toDouble()
        if (!(aspect > 0.0) || !aspect.isFinite()) return GuardVerdict.ACCEPT
        // Poorly seen or non-finite joints are dropped downstream anyway; they must not move the reference.
        if (minOf(arm.shoulderVis, arm.elbowVis, arm.wristVis, arm.hipVis) < MIN_VISIBILITY) return GuardVerdict.ACCEPT
        val coords = floatArrayOf(arm.shoulderX, arm.shoulderY, arm.elbowX, arm.elbowY, arm.wristX, arm.wristY, arm.hipX, arm.hipY)
        if (coords.any { !it.isFinite() }) return GuardVerdict.ACCEPT
        val t = frame.timeMs

        var verdict = GuardVerdict.ACCEPT
        val side = frame.nearRight
        val lastRef = ref
        if ((side != null && nearRight != null && side != nearRight) ||
            (lastRef != null && (t <= lastRef.t || t - lastRef.t > MAX_GAP_MS))
        ) {
            if (side != null && nearRight != null && side != nearRight) verdict = GuardVerdict.SIDE_SWITCH
            reset()
        }
        if (side != null) nearRight = side

        val hx = arm.hipX * aspect
        val hy = arm.hipY.toDouble()
        val sx = arm.shoulderX * aspect
        val sy = arm.shoulderY.toDouble()
        val torso = hypot(sx - hx, sy - hy)
        if (torso < MIN_TORSO) return verdict
        val gap = (sx - hx) / torso
        if (abs(gap) > FACING_GAP) facing = if (gap > 0) 1 else -1

        val wx = (arm.wristX * aspect - hx) / torso
        val wy = (arm.wristY - hy) / torso
        val ex = (arm.elbowX * aspect - hx) / torso
        val ey = (arm.elbowY - hy) / torso
        val shoulderX = (sx - hx) / torso
        val shoulderY = (sy - hy) / torso
        val upper = hypot(shoulderX - ex, shoulderY - ey)
        val fore = hypot(wx - ex, wy - ey)
        val now = Rel(t, wx, wy, ex)

        judged++
        val reason = why(now, shoulderX, shoulderY, upper, fore)
        if (reason == null) {
            accept(now, upper, fore)
            return verdict
        }

        rejects++
        rejected.add(now)
        pool.add(now)
        pool.removeAll { t - it.t > MAJORITY_MS }
        while (acceptedTimes.isNotEmpty() && t - acceptedTimes.first() > MAJORITY_MS) acceptedTimes.removeFirst()
        // Time is counted from the last accepted frame, which is what the tracking timeout counts from.
        val blind = t - (ref?.t ?: rejected.first().t)
        if (blind >= REANCHOR_MS) {
            val mx = rejected.sumOf { it.wx } / rejected.size
            val my = rejected.sumOf { it.wy } / rejected.size
            val agree = rejected.size >= REANCHOR_MIN_FRAMES && rejected.all { abs(it.wx - mx) < REANCHOR_SPREAD && abs(it.wy - my) < 2 * REANCHOR_SPREAD }
            if (agree || blind >= FORCE_MS) return reanchor(now, upper, fore)
            rejected.removeAll { t - it.t > FORCE_MS }
        }
        // Sparse accepted frames (a ghost every few frames) keep resetting the timer above; but if most of the
        // last two seconds was rejected and those frames agree, they are the real arm.
        if (pool.size >= REANCHOR_MIN_FRAMES && pool.size >= MAJORITY_RATIO * acceptedTimes.size && t - pool.first().t >= MAJORITY_MS * 3 / 4) {
            val mx = pool.sumOf { it.wx } / pool.size
            val my = pool.sumOf { it.wy } / pool.size
            if (pool.all { abs(it.wx - mx) < REANCHOR_SPREAD && abs(it.wy - my) < 2 * REANCHOR_SPREAD }) {
                return reanchor(now, upper, fore)
            }
        }
        return reason
    }

    private fun reanchor(now: Rel, upper: Double, fore: Double): GuardVerdict {
        rejects-- // the frame is accepted after all
        slow.clear()
        bones.clear()
        pool.clear()
        acceptedTimes.clear()
        accept(now, upper, fore)
        return GuardVerdict.REANCHOR
    }

    private fun why(now: Rel, shoulderX: Double, shoulderY: Double, upper: Double, fore: Double): GuardVerdict? {
        val r = ref
        if (r != null) {
            val limit = minOf(JUMP_X_MAX, JUMP_X + JUMP_X_PER_S * minOf(now.t - r.t, JUMP_DT_CAP_MS) / 1000.0)
            if (abs(now.wx - r.wx) > limit || abs(now.ex - r.ex) > limit * ELBOW_JUMP_SHARE || abs(now.wy - r.wy) > JUMP_Y) {
                return GuardVerdict.JUMP
            }
        }
        if (slow.size >= MIN_REFERENCE) {
            val w = median(slow.map { it.wx })
            val e = median(slow.map { it.ex })
            if (abs(now.wx - w) > DRIFT_X || abs(now.ex - e) > DRIFT_X) return GuardVerdict.DRIFT
        }
        if (bones.size >= MIN_REFERENCE) {
            val u = upper / median(bones.map { it.upper })
            val f = fore / median(bones.map { it.fore })
            if (u !in BONE_MIN..BONE_MAX || f !in BONE_MIN..BONE_MAX) return GuardVerdict.BONE_LENGTH
        }
        // Facing is the direction from hip to shoulder; "ahead" of the shoulder is that way.
        if (facing != 0) {
            if ((now.wx - shoulderX) * facing < -BEHIND_WRIST || (now.ex - shoulderX) * facing < -BEHIND_ELBOW) {
                return GuardVerdict.CROSSED
            }
        }
        if (now.wy < shoulderY - ABOVE_SHOULDER) return GuardVerdict.CROSSED
        return null
    }

    private fun accept(now: Rel, upper: Double, fore: Double) {
        ref = now
        rejected.clear()
        acceptedTimes.addLast(now.t)
        slow.addLast(now)
        while (now.t - slow.first().t > REFERENCE_MS) slow.removeFirst()
        bones.addLast(Bones(now.t, upper, fore))
        while (now.t - bones.first().t > REFERENCE_MS) bones.removeFirst()
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    companion object {
        /** Same bar as LiveAnalyzer's minVisibility: below it the frame is dropped anyway. */
        const val MIN_VISIBILITY = 0.5f
        const val MIN_TORSO = 0.05

        /** A gap this long between accepted frames restarts the reference (LiveAnalyzer loses tracking at 500 ms). */
        const val MAX_GAP_MS = 1_000L

        /**
         * Sideways move of wrist since the last accepted frame, torso lengths. Clean pushing p95 0.06-0.11, p99 up to
         * 0.29; the wrist flip moves 0.3-0.6. The allowance grows a little with the gap, capped. Elbow gets a share.
         */
        const val JUMP_X = 0.25
        const val JUMP_X_PER_S = 0.05
        const val JUMP_X_MAX = 0.4
        const val JUMP_DT_CAP_MS = 300L
        const val ELBOW_JUMP_SHARE = 0.7

        /** Vertical movement is normal compression; only a very large one is rejected. */
        const val JUMP_Y = 0.6

        /**
         * Wrist or elbow this far (torso lengths) from their median over [REFERENCE_MS]. This is what stops the
         * reference sliding from the real hand onto the phantom through steps that each look small.
         */
        const val DRIFT_X = 0.3
        const val REFERENCE_MS = 1_500L
        const val MIN_REFERENCE = 5

        /** Bone length against its median over [REFERENCE_MS]. */
        const val BONE_MIN = 0.6
        const val BONE_MAX = 1.5

        /** Facing direction only changes when shoulder and hip are this far apart sideways (torso lengths). */
        const val FACING_GAP = 0.3
        const val BEHIND_WRIST = 0.2

        /** Elbows tuck behind the shoulder in real arms (recording 3), so only a far one counts. */
        const val BEHIND_ELBOW = 0.5
        const val ABOVE_SHOULDER = 0.15

        /** Rejected for this long (below LiveAnalyzer's 500 ms) and agreeing: the rescuer really moved. */
        const val REANCHOR_MS = 350L
        const val REANCHOR_MIN_FRAMES = 4
        const val REANCHOR_SPREAD = 0.3

        /**
         * Rejected this long, agreeing or not: accept anyway. Tracking would otherwise go LOST at 500 ms, and
         * the worst case must be no worse than having no guard. Phantom runs last up to ~300 ms, so they stay out.
         */
        const val FORCE_MS = 400L

        /**
         * Majority rule: within this window, agreeing rejected frames outnumbering accepted ones [MAJORITY_RATIO] to 1
         * and spanning most of it re-anchor. Phantom bursts (<= 300 ms, real frames between) never get there.
         */
        const val MAJORITY_MS = 2_000L
        const val MAJORITY_RATIO = 3
    }
}
