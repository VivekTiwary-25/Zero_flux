package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingAdvisorTest {

    private fun goodArm(
        shoulderX: Float = 0.5f,
        shoulderY: Float = 0.25f,
        shoulderVis: Float = 0.9f,
        elbowX: Float = 0.5f,
        elbowY: Float = 0.45f,
        elbowVis: Float = 0.9f,
        wristX: Float = 0.5f,
        wristY: Float = 0.65f,
        wristVis: Float = 0.9f,
        hipX: Float = 0.5f,
        hipY: Float = 0.65f,
        hipVis: Float = 0.9f,
    ) = ArmLandmarks(
        shoulderX = shoulderX,
        shoulderY = shoulderY,
        shoulderVis = shoulderVis,
        elbowX = elbowX,
        elbowY = elbowY,
        elbowVis = elbowVis,
        wristX = wristX,
        wristY = wristY,
        wristVis = wristVis,
        hipX = hipX,
        hipY = hipY,
        hipVis = hipVis,
    )

    // --- 1. Tests for each hint ---

    @Test
    fun `nearArm null produces frame_no_person`() {
        val advisor = FramingAdvisor()
        val advice = advisor.update(null, 0)
        assertEquals("frame_no_person", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `wrist low visibility produces frame_arm_out`() {
        val advisor = FramingAdvisor(minVisibility = 0.5f)
        val advice = advisor.update(goodArm(wristVis = 0.49f), 0)
        assertEquals("frame_arm_out", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `wrist near left edge produces frame_arm_out`() {
        val advisor = FramingAdvisor()
        val advice = advisor.update(goodArm(wristX = 0.03f), 0)
        assertEquals("frame_arm_out", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `wrist near right edge produces frame_arm_out`() {
        val advisor = FramingAdvisor()
        val advice = advisor.update(goodArm(wristX = 0.97f), 0)
        assertEquals("frame_arm_out", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `wrist near top edge produces frame_arm_out`() {
        val advisor = FramingAdvisor()
        val advice = advisor.update(goodArm(wristY = 0.02f), 0)
        assertEquals("frame_arm_out", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `wrist near bottom edge produces frame_arm_out`() {
        val advisor = FramingAdvisor()
        val advice = advisor.update(goodArm(wristY = 0.98f), 0)
        assertEquals("frame_arm_out", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `hip low visibility produces frame_hip_out`() {
        val advisor = FramingAdvisor(minVisibility = 0.5f)
        val advice = advisor.update(goodArm(hipVis = 0.45f), 0)
        assertEquals("frame_hip_out", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `hip near left edge produces frame_hip_out`() {
        val advisor = FramingAdvisor()
        val advice = advisor.update(goodArm(hipX = 0.02f), 0)
        assertEquals("frame_hip_out", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `hip near right edge produces frame_hip_out`() {
        val advisor = FramingAdvisor()
        val advice = advisor.update(goodArm(hipX = 0.99f), 0)
        assertEquals("frame_hip_out", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `hip near top edge produces frame_hip_out`() {
        val advisor = FramingAdvisor()
        val advice = advisor.update(goodArm(hipY = 0.01f), 0)
        assertEquals("frame_hip_out", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `hip near bottom edge produces frame_hip_out`() {
        val advisor = FramingAdvisor()
        val advice = advisor.update(goodArm(hipY = 0.975f), 0)
        assertEquals("frame_hip_out", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `shoulder low visibility produces frame_side_on`() {
        val advisor = FramingAdvisor(minVisibility = 0.5f)
        val advice = advisor.update(goodArm(shoulderVis = 0.3f), 0)
        assertEquals("frame_side_on", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `elbow low visibility produces frame_side_on`() {
        val advisor = FramingAdvisor(minVisibility = 0.5f)
        val advice = advisor.update(goodArm(elbowVis = 0.4f), 0)
        assertEquals("frame_side_on", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `shoulder to hip distance above 0_6 produces frame_move_back`() {
        val advisor = FramingAdvisor()
        // shoulder at y=0.1, hip at y=0.75 -> vertical distance = 0.65 > 0.6
        val advice = advisor.update(goodArm(shoulderY = 0.1f, hipY = 0.75f), 0)
        assertEquals("frame_move_back", advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `shoulder to hip distance at exactly 0_6 does not produce frame_move_back`() {
        val advisor = FramingAdvisor()
        // shoulder at y=0.1, hip at y=0.7 -> distance = 0.60, not above 0.6
        val advice = advisor.update(goodArm(shoulderY = 0.1f, hipY = 0.7f), 0)
        assertNull(advice.hintKey)
        assertFalse(advice.ready)
    }

    // --- 2. Tests for hint priority ---

    @Test
    fun `nearArm null outranks wrist out of frame`() {
        val advisor = FramingAdvisor()
        val advice = advisor.update(null, 0)
        assertEquals("frame_no_person", advice.hintKey)
    }

    @Test
    fun `wrist out of frame outranks hip out of frame`() {
        val advisor = FramingAdvisor()
        // Both wrist and hip near edge
        val arm = goodArm(wristX = 0.01f, hipX = 0.01f)
        val advice = advisor.update(arm, 0)
        assertEquals("frame_arm_out", advice.hintKey)
    }

    @Test
    fun `hip out of frame outranks shoulder or elbow visibility`() {
        val advisor = FramingAdvisor()
        // Hip near edge and shoulderVis < 0.5
        val arm = goodArm(hipX = 0.02f, shoulderVis = 0.1f)
        val advice = advisor.update(arm, 0)
        assertEquals("frame_hip_out", advice.hintKey)
    }

    @Test
    fun `shoulder or elbow visibility outranks distance above 0_6`() {
        val advisor = FramingAdvisor()
        // Shoulder vis low and distance > 0.6
        val arm = goodArm(shoulderVis = 0.2f, shoulderY = 0.1f, hipY = 0.8f)
        val advice = advisor.update(arm, 0)
        assertEquals("frame_side_on", advice.hintKey)
    }

    // --- 3. Tests for the hold ---

    @Test
    fun `ready is false initially on first good frame`() {
        val advisor = FramingAdvisor(holdMs = 1_000)
        val advice = advisor.update(goodArm(), 0)
        assertNull(advice.hintKey)
        assertFalse(advice.ready)
    }

    @Test
    fun `ready remains false before holdMs has elapsed`() {
        val advisor = FramingAdvisor(holdMs = 1_000)
        advisor.update(goodArm(), 0)
        val midAdvice = advisor.update(goodArm(), 500)
        assertNull(midAdvice.hintKey)
        assertFalse(midAdvice.ready)

        val beforeAdvice = advisor.update(goodArm(), 999)
        assertNull(beforeAdvice.hintKey)
        assertFalse(beforeAdvice.ready)
    }

    @Test
    fun `ready becomes true exactly at holdMs`() {
        val advisor = FramingAdvisor(holdMs = 1_000)
        advisor.update(goodArm(), 0)
        val advice = advisor.update(goodArm(), 1_000)
        assertNull(advice.hintKey)
        assertTrue(advice.ready)
    }

    @Test
    fun `ready stays true while good frames continue`() {
        val advisor = FramingAdvisor(holdMs = 1_000)
        advisor.update(goodArm(), 0)
        advisor.update(goodArm(), 1_000)
        val advice = advisor.update(goodArm(), 2_500)
        assertNull(advice.hintKey)
        assertTrue(advice.ready)
    }

    @Test
    fun `custom holdMs is respected`() {
        val advisor = FramingAdvisor(holdMs = 300)
        advisor.update(goodArm(), 100)
        val at299 = advisor.update(goodArm(), 399)
        assertFalse(at299.ready)
        val at300 = advisor.update(goodArm(), 400)
        assertTrue(at300.ready)
    }

    // --- 4. Tests for immediate reset ---

    @Test
    fun `any problem resets ready immediately when already ready`() {
        val advisor = FramingAdvisor(holdMs = 1_000)
        advisor.update(goodArm(), 0)
        val readyAdvice = advisor.update(goodArm(), 1_000)
        assertTrue(readyAdvice.ready)

        // Single bad frame at t=1001
        val badAdvice = advisor.update(null, 1_001)
        assertFalse("Ready must immediately become false", badAdvice.ready)
        assertEquals("frame_no_person", badAdvice.hintKey)
    }

    @Test
    fun `recovering from a problem after being ready requires holding again`() {
        val advisor = FramingAdvisor(holdMs = 1_000)
        advisor.update(goodArm(), 0)
        val readyAdvice = advisor.update(goodArm(), 1_000)
        assertTrue(readyAdvice.ready)

        // Glitch occurs at t=1001
        val glitch = advisor.update(goodArm(wristVis = 0.1f), 1_001)
        assertFalse(glitch.ready)
        assertEquals("frame_arm_out", glitch.hintKey)

        // Good frames resume at t=1050
        advisor.update(goodArm(), 1_050)

        // Hint clears at t=1501 (1001 + 500)
        val cleared = advisor.update(goodArm(), 1_501)
        assertNull(cleared.hintKey)
        assertFalse(cleared.ready)

        // At t=2001 (500 ms held) -> still not ready
        val halfHold = advisor.update(goodArm(), 2_001)
        assertFalse(halfHold.ready)

        // At t=2501 (1000 ms held) -> ready again
        val recovered = advisor.update(goodArm(), 2_501)
        assertTrue(recovered.ready)
        assertNull(recovered.hintKey)
    }

    @Test
    fun `any problem resets hold progress during the hold period`() {
        val advisor = FramingAdvisor(holdMs = 1_000)
        advisor.update(goodArm(), 0)
        advisor.update(goodArm(), 800) // 800 ms into 1000 ms hold

        // Glitch frame at t=850
        val glitch = advisor.update(goodArm(wristVis = 0.1f), 850)
        assertEquals("frame_arm_out", glitch.hintKey)
        assertFalse(glitch.ready)

        // Good frames resume at t=900
        advisor.update(goodArm(), 900) // hint still stays due to 500 ms anti-flicker

        // Hint clears at t=1350 (850 + 500)
        val cleared = advisor.update(goodArm(), 1_350)
        assertNull(cleared.hintKey)
        assertFalse(cleared.ready)

        // Hold started at t=1350, so at t=1850 only 500 ms held -> not ready
        val midAdvice = advisor.update(goodArm(), 1_850)
        assertFalse(midAdvice.ready)

        // Full 1000 ms hold completes at t=2350
        val fullAdvice = advisor.update(goodArm(), 2_350)
        assertTrue(fullAdvice.ready)
    }

    // --- 5. Tests for anti-flicker (min 500 ms display) ---

    @Test
    fun `hint stays for at least 500 ms even if frame becomes valid immediately`() {
        val advisor = FramingAdvisor()
        val badAdvice = advisor.update(goodArm(wristVis = 0.2f), 0)
        assertEquals("frame_arm_out", badAdvice.hintKey)
        assertFalse(badAdvice.ready)

        // Problem resolved immediately at t=50
        val earlyAdvice = advisor.update(goodArm(), 50)
        assertEquals("Hint must not flicker away early", "frame_arm_out", earlyAdvice.hintKey)
        assertFalse(earlyAdvice.ready)

        val beforeExpiry = advisor.update(goodArm(), 499)
        assertEquals("frame_arm_out", beforeExpiry.hintKey)
        assertFalse(beforeExpiry.ready)

        // At t=500, exactly 500 ms elapsed, hint clears
        val atExpiry = advisor.update(goodArm(), 500)
        assertNull(atExpiry.hintKey)
        assertFalse(atExpiry.ready)
    }

    @Test
    fun `hint does not switch to a different problem before 500 ms has elapsed`() {
        val advisor = FramingAdvisor()
        advisor.update(goodArm(wristVis = 0.1f), 0) // frame_arm_out

        // Different problem occurs at t=200
        val midAdvice = advisor.update(null, 200)
        assertEquals("Initial hint must stay to prevent flicker", "frame_arm_out", midAdvice.hintKey)

        // At t=500, previous hint has stayed for 500 ms; now transitions to frame_no_person
        val nextAdvice = advisor.update(null, 500)
        assertEquals("frame_no_person", nextAdvice.hintKey)

        // The new hint must now stay for at least 500 ms (until t=1000)
        val goodMid = advisor.update(goodArm(), 700)
        assertEquals("frame_no_person", goodMid.hintKey)

        val nextCleared = advisor.update(goodArm(), 1_000)
        assertNull(nextCleared.hintKey)
    }

    @Test
    fun `hint that has stayed for longer than 500 ms clears immediately once resolved`() {
        val advisor = FramingAdvisor()
        advisor.update(goodArm(wristVis = 0.1f), 0)
        advisor.update(goodArm(wristVis = 0.1f), 400)
        advisor.update(goodArm(wristVis = 0.1f), 800) // Stayed for 800 ms

        // Resolved at t=850 (850 - 0 >= 500 ms) -> clears immediately
        val resolved = advisor.update(goodArm(), 850)
        assertNull("Hint already stayed >= 500 ms, so it clears immediately", resolved.hintKey)
        assertFalse(resolved.ready)
    }

    // --- 6. Tests for minVisibility threshold ---

    @Test
    fun `custom minVisibility treats landmarks below threshold as not visible`() {
        val advisor = FramingAdvisor(minVisibility = 0.8f)
        val advice = advisor.update(goodArm(wristVis = 0.75f), 0)
        assertEquals("frame_arm_out", advice.hintKey)
    }

    @Test
    fun `landmarks with visibility equal to minVisibility are visible`() {
        val advisor = FramingAdvisor(minVisibility = 0.5f)
        val advice = advisor.update(goodArm(wristVis = 0.5f), 0)
        assertNull(advice.hintKey)
    }
}
