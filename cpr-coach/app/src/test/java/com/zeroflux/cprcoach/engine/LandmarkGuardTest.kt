package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Synthetic arms, aspect 1 so x is already scaled. Facing left: hip right of shoulder, hands ahead (left). */
class LandmarkGuardTest {
    private val stepMs = 33L

    /** Torso is 0.283 long; all offsets below are in image units. */
    private fun frame(
        t: Long,
        wristX: Double = 0.40,
        wristY: Double = 0.75,
        elbowX: Double = 0.40,
        elbowY: Double = 0.52,
        shoulderX: Double = 0.40,
        nearRight: Boolean? = false,
        vis: Float = 0.95f,
    ): PoseFrame {
        val arm = ArmLandmarks(
            shoulderX.toFloat(), 0.30f, vis,
            elbowX.toFloat(), elbowY.toFloat(), vis,
            wristX.toFloat(), wristY.toFloat(), vis,
            0.60f, 0.50f, vis,
        )
        return PoseFrame(t, PoseSample(t, 0.5, 170.0, vis, vis, true), arm, 0L, 1f, nearRight)
    }

    /** Feeds [seconds] of steady compressions (wrist bobs 0.03 up and down at about 110/min). */
    private fun settle(guard: LandmarkGuard, seconds: Double, wristX: Double = 0.40, elbowX: Double = 0.40): Long {
        var t = 0L
        while (t <= seconds * 1000) {
            val bob = 0.03 * sin(2 * PI * 1.8 * t / 1000.0)
            val v = guard.check(frame(t, wristX = wristX, wristY = 0.75 + bob, elbowX = elbowX))
            assertTrue("clean pushing is accepted at ${t}ms, got $v", v.accepted)
            t += stepMs
        }
        return t
    }

    @Test
    fun `clean pushing is never rejected`() {
        val g = LandmarkGuard()
        settle(g, 10.0)
        assertEquals(0, g.rejects)
        assertTrue(g.judged > 250)
    }

    @Test
    fun `wrist flipping to a phantom under the elbow is rejected while the real hand is kept`() {
        val g = LandmarkGuard()
        // Bent arm: hand forward (x 0.30) of the elbow (x 0.40).
        var t = settle(g, 2.0, wristX = 0.30)
        var phantomsSeen = 0
        var realSeen = 0
        // Runs of up to 9 phantom frames (~300 ms), then real frames.
        for (run in 0 until 8) {
            for (i in 0 until 3 + run) {
                val v = g.check(frame(t, wristX = 0.40, wristY = 0.78))
                assertFalse("phantom at ${t}ms must be rejected, got $v", v.accepted)
                phantomsSeen++
                t += stepMs
            }
            for (i in 0 until 4) {
                val v = g.check(frame(t, wristX = 0.30, wristY = 0.75))
                assertTrue("real hand at ${t}ms must be accepted, got $v", v.accepted)
                realSeen++
                t += stepMs
            }
        }
        assertTrue(phantomsSeen > 40 && realSeen == 32)
    }

    @Test
    fun `a slow slide onto the phantom is caught by the one and a half second reference`() {
        val g = LandmarkGuard()
        var t = settle(g, 2.0, wristX = 0.30)
        // 0.02 of image (0.07 torso) per frame: each step is small, together they reach the phantom.
        var x = 0.30
        var rejected = false
        while (x < 0.40) {
            x += 0.02
            if (!g.check(frame(t, wristX = x)).accepted) rejected = true
            t += stepMs
        }
        assertTrue("the slide ends rejected", rejected)
    }

    @Test
    fun `rejected frames that agree for 350 ms become the new place`() {
        val g = LandmarkGuard()
        var t = settle(g, 2.0, wristX = 0.30)
        var firstRejectT = -1L
        var reanchored: Long? = null
        // The rescuer really shifted: wrist and elbow now 0.12 (0.42 torso) further back, and stay there.
        for (i in 0 until 40) {
            val v = g.check(frame(t, wristX = 0.42, elbowX = 0.46))
            if (!v.accepted && firstRejectT < 0) firstRejectT = t
            if (v == GuardVerdict.REANCHOR && reanchored == null) reanchored = t
            t += stepMs
        }
        assertTrue("rejected at first", firstRejectT >= 0)
        assertNotNull(reanchored)
        val at = reanchored!!
        assertTrue("re-anchored after 350 ms, was ${at - firstRejectT}", at - firstRejectT in 300..450)
        assertTrue("accepted afterwards", g.check(frame(t, wristX = 0.42, elbowX = 0.46)).accepted)
    }

    @Test
    fun `scattered rejects are held back until the force time`() {
        val g = LandmarkGuard()
        var t = settle(g, 2.0, wristX = 0.30)
        // Garbage that jumps around: no two agree. It stays out for 400 ms, then is let in so tracking is not lost.
        val first = t
        var firstAccepted: Long? = null
        for (i in 0 until 30) {
            val x = if (i % 2 == 0) 0.55 else 0.05
            if (g.check(frame(t, wristX = x, elbowX = x)).accepted && firstAccepted == null) firstAccepted = t
            t += stepMs
        }
        val at = firstAccepted!!
        assertTrue("held back at least 350 ms, was ${at - first}", at - first >= 350)
        assertTrue("let in by 500 ms, was ${at - first}", at - first <= 500)
    }

    @Test
    fun `switching the near arm resets the reference`() {
        val g = LandmarkGuard()
        var t = settle(g, 2.0)
        // The other arm sits far from the old one (0.4 torso sideways); without a reset this is a jump.
        val v = g.check(frame(t, wristX = 0.28, elbowX = 0.31, nearRight = true))
        assertEquals(GuardVerdict.SIDE_SWITCH, v)
        t += stepMs
        assertTrue(g.check(frame(t, wristX = 0.28, elbowX = 0.31, nearRight = true)).accepted)
    }

    @Test
    fun `wrist behind the shoulder or above it is rejected`() {
        val behind = LandmarkGuard().check(frame(0, wristX = 0.50, wristY = 0.60))
        assertEquals(GuardVerdict.CROSSED, behind)
        val above = LandmarkGuard().check(frame(0, wristX = 0.38, wristY = 0.20))
        assertEquals(GuardVerdict.CROSSED, above)
    }

    @Test
    fun `a forearm much longer than it has been is rejected`() {
        val g = LandmarkGuard()
        var t = 0L
        while (t < 2000) {
            assertTrue(g.check(frame(t, wristY = 0.67)).accepted)
            t += stepMs
        }
        assertEquals(GuardVerdict.BONE_LENGTH, g.check(frame(t, wristY = 0.80)))
    }

    @Test
    fun `poorly seen frames and frames without an arm pass through and leave the reference alone`() {
        val g = LandmarkGuard()
        var t = settle(g, 2.0, wristX = 0.30)
        assertTrue(g.check(frame(t, wristX = 0.90, vis = 0.3f)).accepted)
        t += stepMs
        assertTrue(g.check(PoseFrame(t, null, null, 0L)).accepted)
        t += stepMs
        // The reference is still the real hand, so the phantom is still rejected.
        assertFalse(g.check(frame(t, wristX = 0.40, wristY = 0.78)).accepted)
    }

    @Test
    fun `a long gap starts afresh`() {
        val g = LandmarkGuard()
        val t = settle(g, 2.0, wristX = 0.30)
        assertTrue(g.check(frame(t + 1500, wristX = 0.45, elbowX = 0.50)).accepted)
    }

    @Test
    fun `overlay keeps showing the last accepted arm`() {
        val g = LandmarkGuard()
        var t = settle(g, 2.0, wristX = 0.30)
        val good = frame(t, wristX = 0.30)
        assertEquals(good.arm, g.overlayFrame(good).arm)
        t += stepMs
        val phantom = frame(t, wristX = 0.40, wristY = 0.78)
        val shown = g.overlayFrame(phantom)
        assertEquals(good.arm, shown.arm)
        assertEquals(phantom.timeMs, shown.timeMs)
        // No person: passes through so the overlay can fade.
        assertEquals(null, g.overlayFrame(PoseFrame(t + stepMs, null, null, 0L)).arm)
    }

    @Test
    fun `the analyzer drops rejected frames entirely`() {
        val analyzer = LiveAnalyzer()
        var t = 0L
        while (t <= 3000) {
            analyzer.addFrame(frame(t, wristX = 0.30, wristY = 0.75 + 0.03 * sin(2 * PI * 1.8 * t / 1000.0)))
            t += stepMs
        }
        val before = analyzer.read(t)
        assertTrue(before.tracking != TrackingState.LOST)
        // A burst of phantoms: frames are dropped, so tracking is not fed and not lost within the timeout.
        val phantomUntil = t + 300
        while (t < phantomUntil) {
            analyzer.addFrame(frame(t, wristX = 0.40, wristY = 0.78))
            t += stepMs
        }
        assertTrue(analyzer.read(t).tracking != TrackingState.LOST)
    }
}
