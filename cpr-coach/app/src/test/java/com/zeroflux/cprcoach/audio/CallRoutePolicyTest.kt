package com.zeroflux.cprcoach.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRoutePolicyTest {
    @Test fun noCallStaysMedia() {
        val p = CallRoutePolicy()
        assertNull(p.onCallActive(false))
        assertEquals(AudioRoute.MEDIA, p.route)
    }

    @Test fun callStartsThenEnds() {
        val p = CallRoutePolicy()
        assertEquals(AudioRoute.VOICE_SPEAKER, p.onCallActive(true))
        assertEquals(AudioRoute.MEDIA, p.onCallActive(false))
    }

    @Test fun repeatedSameModeDoesNothing() {
        val p = CallRoutePolicy()
        p.onCallActive(true)
        repeat(5) { assertNull(p.onCallActive(true)) }
        p.onCallActive(false)
        repeat(5) { assertNull(p.onCallActive(false)) }
    }

    @Test fun stopDuringCallRestoresOnceAndFreezes() {
        val p = CallRoutePolicy()
        p.onCallActive(true)
        assertEquals(AudioRoute.MEDIA, p.onStop())
        assertNull(p.onStop())
        assertNull(p.onCallActive(true))
        assertEquals(AudioRoute.MEDIA, p.route)
    }

    @Test fun stopWithoutCallChangesNothing() {
        assertNull(CallRoutePolicy().onStop())
    }

    @Test fun callModes() {
        assertTrue(CallRoutePolicy.isCallMode(2))
        assertTrue(CallRoutePolicy.isCallMode(3))
        assertFalse(CallRoutePolicy.isCallMode(0))
        assertFalse(CallRoutePolicy.isCallMode(1))
    }
}
