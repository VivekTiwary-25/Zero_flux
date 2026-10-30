package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadGovernorTest {
    private fun feed(
        g: LoadGovernor,
        from: Long,
        seconds: Int,
        latency: Long?,
        thermal: Int = 0,
        batteryTempC: Float = Float.NaN,
    ): Long {
        var t = from
        repeat(seconds * 10) { g.update(t, latency, thermal, batteryTempC); t += 100 }
        return t
    }

    @Test fun emptyWindowDoesNotCrashOrMove() {
        val g = LoadGovernor()
        assertEquals(30L, g.update(0, null, 0))
        assertEquals(30L, g.update(10_000, null, 1))
    }

    @Test fun highLatencyStepsDownThenStops() {
        val g = LoadGovernor()
        feed(g, 0, 60, 300)
        assertEquals(2, g.step)
        assertEquals(90L, g.gapMs)
    }

    @Test fun normalBatteryIgnoresThermalStatusesBelowCritical() {
        val justBelowHot = Float.fromBits(LoadGovernor.HOT_BATTERY_TEMP_C.toBits() - 1)
        for (thermal in 0 until LoadGovernor.THERMAL_CRITICAL) {
            val g = LoadGovernor()
            assertEquals(30L, g.update(0, null, thermal, justBelowHot))
            feed(g, 0, 60, 60, thermal, 35f)
            assertEquals(0, g.step)
        }
    }

    @Test fun hotBoundaryStepsDownEvenWithNormalThermalStatus() {
        val g = LoadGovernor()
        assertEquals(60L, g.update(0, null, 0, LoadGovernor.HOT_BATTERY_TEMP_C))
        assertEquals(90L, g.update(1, null, LoadGovernor.THERMAL_SEVERE, 40f))
    }

    @Test fun hotModerateStatusImmediatelyUsesMiddleStep() {
        val g = LoadGovernor()
        assertEquals(60L, g.update(0, null, LoadGovernor.THERMAL_MODERATE, 40f))
    }

    @Test fun knownCriticalAndHigherImmediatelyUseLowestStepEvenWhenCool() {
        for (thermal in LoadGovernor.THERMAL_CRITICAL..6) {
            for (batteryTempC in listOf(20f, 35f, 40f)) {
                val g = LoadGovernor()
                assertEquals(90L, g.update(0, null, thermal, batteryTempC))
            }
        }
    }

    @Test fun unknownTemperatureIgnoresEveryThermalStatus() {
        for (thermal in 0..6) {
            val g = LoadGovernor()
            assertEquals(30L, g.update(0, null, thermal, Float.NaN))
            assertEquals(30L, g.update(1, null, thermal))
        }
    }

    @Test fun unknownTemperatureStillStepsDownForLatencyAtCriticalStatus() {
        val g = LoadGovernor()
        feed(g, 0, 60, 300, LoadGovernor.THERMAL_CRITICAL)
        assertEquals(2, g.step)
    }

    @Test fun unknownTemperatureAllowsSlowRecoveryEvenAtCriticalStatus() {
        val g = LoadGovernor(2)
        feed(g, 0, 61, 60, LoadGovernor.THERMAL_CRITICAL)
        assertEquals(0, g.step)
    }

    @Test fun normalBatteryStillStepsDownForSustainedHighLatency() {
        val g = LoadGovernor()
        feed(g, 0, 60, 300, LoadGovernor.THERMAL_SEVERE, 35f)
        assertEquals(2, g.step)
    }

    @Test fun highLatencyWaitsForHoldTimeAtEachStep() {
        val g = LoadGovernor()
        feed(g, 0, 4, 300)
        assertEquals(30L, g.update(LoadGovernor.DOWN_HOLD_MS - 1, 300, 0))
        assertEquals(60L, g.update(LoadGovernor.DOWN_HOLD_MS, 300, 0))
        feed(g, 4_100, 3, 300)
        assertEquals(60L, g.update(2 * LoadGovernor.DOWN_HOLD_MS - 1, 300, 0))
        assertEquals(90L, g.update(2 * LoadGovernor.DOWN_HOLD_MS, 300, 0))
    }

    @Test fun highLatencyRequiresEnoughRecentSamples() {
        val g = LoadGovernor()
        g.update(0, null, 0)
        repeat(LoadGovernor.MIN_SAMPLES - 1) {
            g.update(4_000L + it, 300, 0)
        }
        assertEquals(0, g.step)
        assertEquals(60L, g.update(4_000L + LoadGovernor.MIN_SAMPLES, 300, 0))
    }

    @Test fun staleHighLatencyDoesNotCauseAStepDown() {
        val g = LoadGovernor()
        repeat(LoadGovernor.MIN_SAMPLES) { g.update(it.toLong(), 300, 0) }
        assertEquals(30L, g.update(LoadGovernor.WINDOW_MS + LoadGovernor.MIN_SAMPLES, null, 0))
    }

    @Test fun hotMiddleStepCanStillStepDownForSustainedLatency() {
        val g = LoadGovernor()
        assertEquals(60L, g.update(0, null, 0, 40f))
        feed(g, 100, 4, 300, batteryTempC = 40f)
        assertEquals(2, g.step)
    }

    @Test fun exactLatencyThresholdsHold() {
        val high = LoadGovernor()
        feed(high, 0, 60, LoadGovernor.HIGH_LATENCY_MS)
        assertEquals(0, high.step)
        val low = LoadGovernor(2)
        feed(low, 0, 60, LoadGovernor.LOW_LATENCY_MS)
        assertEquals(2, low.step)
    }

    @Test fun stepsUpSlowlyAfterCoolingNotEverySecond() {
        val g = LoadGovernor(2)
        var t = feed(g, 0, 20, 60)
        assertEquals(2, g.step)
        t = feed(g, t, 20, 60)
        assertEquals(1, g.step)
        feed(g, t, 5, 60)
        assertEquals(1, g.step)
    }

    @Test fun coolingRecoversOneStepAtEachHoldBoundaryDespiteSevereStatus() {
        val g = LoadGovernor()
        assertEquals(90L, g.update(0, null, LoadGovernor.THERMAL_SEVERE, 40f))
        feed(g, 100, 29, 60, LoadGovernor.THERMAL_SEVERE, 35f)
        assertEquals(90L, g.update(LoadGovernor.UP_HOLD_MS - 1, 60, 3, 35f))
        assertEquals(60L, g.update(LoadGovernor.UP_HOLD_MS, 60, 3, 35f))
        feed(g, 30_100, 29, 60, LoadGovernor.THERMAL_SEVERE, 35f)
        assertEquals(60L, g.update(2 * LoadGovernor.UP_HOLD_MS - 1, 60, 3, 35f))
        assertEquals(30L, g.update(2 * LoadGovernor.UP_HOLD_MS, 60, 3, 35f))
    }

    @Test fun hotPhoneNeverStepsUp() {
        val g = LoadGovernor(2)
        feed(g, 0, 120, 50, thermal = 0, batteryTempC = 40f)
        assertEquals(2, g.step)
    }

    @Test fun coolCriticalPhoneNeverStepsUp() {
        val g = LoadGovernor(2)
        feed(g, 0, 120, 50, LoadGovernor.THERMAL_CRITICAL, 35f)
        assertEquals(2, g.step)
    }

    @Test fun neverBelowTenFps() {
        assertTrue(LoadGovernor.GAPS_MS.all { it <= 100L })
    }

    @Test fun midLatencyHolds() {
        val g = LoadGovernor(1)
        feed(g, 0, 120, 120)
        assertEquals(1, g.step)
    }

    @Test
    fun `every step stays above the rate estimator's minimum on a 30 fps camera`() {
        val cameraFrameMs = 1000.0 / 30
        for (gap in LoadGovernor.GAPS_MS) {
            val framesWaited = kotlin.math.ceil(gap / cameraFrameMs)
            val perSecond = 1000.0 / (framesWaited * cameraFrameMs)
            assertTrue("gap $gap gives $perSecond a second", perSecond >= 10.0)
        }
    }

    @Test fun `top step stays at or above 24 fps on a 30 fps camera`() {
        val g = LoadGovernor()
        assertEquals(0, g.step)
        val cameraFrameMs = 1000.0 / 30
        val framesWaited = kotlin.math.ceil(g.gapMs / cameraFrameMs)
        val perSecond = 1000.0 / (framesWaited * cameraFrameMs)
        assertTrue("top gap ${g.gapMs} gives $perSecond a second", perSecond >= 24.0)
    }
}
