package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPoseTest {
    private fun arm(x: Float, y: Float = 0.5f, vis: Float = 0.9f) =
        ArmLandmarks(x, y, vis, x, y + 0.1f, vis, x, y + 0.2f, vis, x, y + 0.3f, vis)

    private fun frame(t: Long, arm: ArmLandmarks?) = PoseFrame(t, null, arm, 0L)

    @Test
    fun mapsPortraitExactly() {
        assertEquals(0f, OverlayMapping.mapX(0f, 300f, 400f, 0.75f), 1e-3f)
        assertEquals(300f, OverlayMapping.mapX(1f, 300f, 400f, 0.75f), 1e-3f)
        assertEquals(200f, OverlayMapping.mapY(0.5f, 300f, 400f, 0.75f), 1e-3f)
    }

    @Test
    fun landscape20by9On4by3CropsTopAndBottom() {
        val w = 2000f
        val h = 900f
        val a = 4f / 3f
        assertEquals(0f, OverlayMapping.mapX(0f, w, h, a), 1e-2f)
        assertEquals(2000f, OverlayMapping.mapX(1f, w, h, a), 1e-2f)
        // Shown height 1500, so the top 300 px are cropped.
        assertEquals(-300f, OverlayMapping.mapY(0f, w, h, a), 1e-2f)
        assertEquals(450f, OverlayMapping.mapY(0.5f, w, h, a), 1e-2f)
        assertEquals(1200f, OverlayMapping.mapY(1f, w, h, a), 1e-2f)
    }

    @Test
    fun tallViewCropsSides() {
        // 9:20 view on a 3:4 image: height fits (shown width 750), width overflows.
        assertEquals(-150f, OverlayMapping.mapX(0f, 450f, 1000f, 0.75f), 1e-3f)
        assertEquals(600f, OverlayMapping.mapX(1f, 450f, 1000f, 0.75f), 1e-3f)
    }

    @Test
    fun noFrameMeansNothing() {
        assertNull(OverlayPose().at(1000))
    }

    @Test
    fun extrapolatesButCapsAhead() {
        val p = OverlayPose()
        p.push(frame(0, arm(0.3f)), 0)
        p.push(frame(50, arm(0.4f)), 50)
        val at50 = p.at(50)!!.x[0]
        val at90 = p.at(90)!!.x[0]
        val at300 = p.at(300)!!.x[0]
        val at350 = p.at(350)!!.x[0]
        assertTrue(at90 > at50)
        assertEquals(at300, p.at(130)!!.x[0], 1e-6f)
        assertEquals(at300, at350, 1e-6f)
    }

    @Test
    fun fadesAfterNoArmThenHides() {
        val p = OverlayPose()
        p.push(frame(0, arm(0.3f)), 0)
        p.push(frame(50, arm(0.3f)), 50)
        assertEquals(1f, p.at(60)!!.alpha, 1e-6f)
        p.push(frame(100, null), 100)
        val mid = p.at(250)!!.alpha
        assertTrue(mid in 0.01f..0.99f)
        assertNull(p.at(401))
        assertFalse(p.isVisible(401))
    }

    @Test
    fun staleFramesFadeInsteadOfFreezing() {
        val p = OverlayPose()
        p.push(frame(0, arm(0.3f)), 0)
        assertEquals(1f, p.at(300)!!.alpha, 1e-6f)
        assertNull(p.at(800))
    }

    @Test
    fun badValuesAreIgnored() {
        val p = OverlayPose()
        p.push(frame(0, arm(Float.NaN)), 0)
        assertNull(p.at(10))
        p.push(frame(10, arm(0.3f, vis = 0.1f)), 10)
        assertNull(p.at(20))
        p.push(frame(20, arm(0.3f)), 20)
        val out = p.at(20)
        assertNotNull(out)
        assertTrue(out!!.x[0].isFinite())
    }

    @Test
    fun restartsAfterGapWithoutGliding() {
        val p = OverlayPose()
        p.push(frame(0, arm(0.1f)), 0)
        p.push(frame(50, arm(0.1f)), 50)
        p.push(frame(100, null), 100)
        p.push(frame(2000, arm(0.9f)), 2000)
        assertEquals(0.9f, p.at(2000)!!.x[0], 1e-4f)
    }
}
