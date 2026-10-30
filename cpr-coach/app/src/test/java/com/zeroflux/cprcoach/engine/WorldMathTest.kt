package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldMathTest {
    @Test fun straightArmIs180() {
        assertEquals(180.0, WorldMath.elbowDegrees(0f, 0f, 0f, 0f, 0.3f, 0f, 0f, 0.6f, 0f)!!, 1e-4)
    }

    @Test fun rightAngleInDepthPlane() {
        // Upper arm along y, forearm along z: only a 3D angle sees this as 90.
        assertEquals(90.0, WorldMath.elbowDegrees(0f, 0f, 0f, 0f, 0.3f, 0f, 0f, 0.3f, 0.25f)!!, 1e-4)
    }

    @Test fun degenerateOrNonFiniteIsNull() {
        assertNull(WorldMath.elbowDegrees(0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f))
        assertNull(WorldMath.elbowDegrees(Float.NaN, 0f, 0f, 0f, 1f, 0f, 0f, 2f, 0f))
    }

    @Test fun shoulderOverWristIgnoresHeight() {
        assertEquals(0.5, WorldMath.shoulderOverWrist(0f, 0f, 0.3f, 0.4f)!!, 1e-6)
        assertNull(WorldMath.shoulderOverWrist(Float.NaN, 0f, 0f, 0f))
    }

    @Test fun captureRule() {
        val none = Long.MIN_VALUE
        assertTrue(FrameDump.shouldCapture(1000, none, 0, false))
        assertFalse(FrameDump.shouldCapture(1400, 1000, 1, false))
        assertTrue(FrameDump.shouldCapture(1500, 1000, 1, false))
        assertFalse(FrameDump.shouldCapture(9000, 1000, 1, true))
        assertFalse(FrameDump.shouldCapture(9000, 1000, FrameDump.MAX_IMAGES, false))
    }
}
