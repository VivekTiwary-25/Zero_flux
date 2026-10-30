package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencySequenceTest {

    @Test
    fun `start speaks call, then hands, then push`() {
        val spoken = EmergencySequence().start(1_000L)
        assertEquals(listOf("em_call", "em_hands", "em_push"), spoken)
    }

    @Test
    fun `nothing is due before thirty seconds`() {
        val seq = EmergencySequence()
        val t0 = 8_000L
        seq.start(t0)
        assertEquals(emptyList<String>(), seq.tick(t0))
        assertEquals(emptyList<String>(), seq.tick(t0 + 29_999L))
    }

    @Test
    fun `aed is spoken once at thirty seconds`() {
        val seq = EmergencySequence()
        val t0 = 4_000L
        seq.start(t0)
        assertEquals(listOf("em_aed"), seq.tick(t0 + 30_000L))
        assertEquals(emptyList<String>(), seq.tick(t0 + 30_000L))
        assertEquals(emptyList<String>(), seq.tick(t0 + 45_000L))
    }

    @Test
    fun `a late arrival still gets the one aed`() {
        val seq = EmergencySequence()
        val t0 = 2_000L
        seq.start(t0)
        assertEquals(listOf("em_aed"), seq.tick(t0 + 30_500L))
    }

    @Test
    fun `continue is spoken at sixty seconds`() {
        val seq = EmergencySequence()
        val t0 = 9_000L
        seq.start(t0)
        seq.tick(t0 + 30_000L)
        assertEquals(emptyList<String>(), seq.tick(t0 + 59_999L))
        assertEquals(listOf("em_continue"), seq.tick(t0 + 60_000L))
        assertEquals(emptyList<String>(), seq.tick(t0 + 60_000L))
        assertEquals(emptyList<String>(), seq.tick(t0 + 90_000L))
    }

    @Test
    fun `a jump to sixty seconds speaks aed then continue`() {
        val seq = EmergencySequence()
        val t0 = 3_000L
        seq.start(t0)
        assertEquals(listOf("em_aed", "em_continue"), seq.tick(t0 + 60_000L))
        assertEquals(emptyList<String>(), seq.tick(t0 + 60_000L))
    }

    @Test
    fun `swap is spoken every two minutes and not between`() {
        val seq = EmergencySequence()
        val t0 = 11_000L
        seq.start(t0)
        seq.tick(t0 + 60_000L)
        assertEquals(emptyList<String>(), seq.tick(t0 + 119_999L))
        assertEquals(listOf("em_swap"), seq.tick(t0 + 120_000L))
        assertEquals(emptyList<String>(), seq.tick(t0 + 120_000L))
        assertEquals(emptyList<String>(), seq.tick(t0 + 239_999L))
        assertEquals(listOf("em_swap"), seq.tick(t0 + 240_000L))
        assertEquals(listOf("em_swap"), seq.tick(t0 + 360_000L))
    }

    @Test
    fun `repeated ticks in the same period never repeat a clip`() {
        val seq = EmergencySequence()
        val t0 = 6_000L
        seq.start(t0)
        val first = seq.tick(t0 + 150_000L)
        assertEquals(listOf("em_aed", "em_continue", "em_swap"), first)
        assertEquals(emptyList<String>(), seq.tick(t0 + 150_000L))
        assertEquals(emptyList<String>(), seq.tick(t0 + 200_000L))
    }

    @Test
    fun `a late tick speaks only the latest swap`() {
        val seq = EmergencySequence()
        val t0 = 1_500L
        seq.start(t0)
        seq.tick(t0 + 60_000L)
        // Crosses the 2, 4, 6, 8 and 10 minute marks. Only the 10 minute swap is due now.
        val late = seq.tick(t0 + 120_000L * 5)
        assertEquals(listOf("em_swap"), late)
        assertEquals(emptyList<String>(), seq.tick(t0 + 120_000L * 5))
        assertEquals(listOf("em_swap"), seq.tick(t0 + 120_000L * 6))
    }

    @Test
    fun `a late tick from the start does not backlog swaps`() {
        val seq = EmergencySequence()
        val t0 = 20_000L
        seq.start(t0)
        val late = seq.tick(t0 + 120_000L * 4 + 1L)
        assertEquals(listOf("em_aed", "em_continue", "em_swap"), late)
        assertEquals(1, late.count { it == "em_swap" })
    }

    @Test
    fun `the whole session speaks aed once`() {
        val seq = EmergencySequence()
        val t0 = 7_000L
        val spoken = ArrayList<String>()
        spoken += seq.start(t0)
        val times = longArrayOf(
            0L,
            29_999L,
            30_000L,
            30_000L,
            59_999L,
            60_000L,
            120_000L,
            240_000L,
            360_000L,
            1_000_000L,
        )
        for (at in times) spoken += seq.tick(t0 + at)
        assertEquals(1, spoken.count { it == "em_aed" })
        assertEquals(1, spoken.count { it == "em_continue" })
    }
}
