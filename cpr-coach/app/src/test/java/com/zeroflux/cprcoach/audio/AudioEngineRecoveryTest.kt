package com.zeroflux.cprcoach.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEngineRecoveryTest {
    @Test
    fun longOutageKeepsCalmRetriesAndReportsOnlyOnce() {
        val recovery = AudioRecovery(300, 6_000, 600)
        assertTrue(recovery.failed())
        assertEquals(300L, recovery.retryDelayMs)
        repeat(1_000) { assertFalse(recovery.failed()) }
        assertEquals(6_000L, recovery.retryDelayMs)
    }

    @Test
    fun shortLivedReplacementDoesNotResetOutage() {
        val recovery = AudioRecovery(300, 6_000, 600)
        assertTrue(recovery.failed())
        repeat(40) {
            recovery.accepted(599)
            assertFalse(recovery.failed())
        }
        assertEquals(6_000L, recovery.retryDelayMs)
        recovery.accepted(300)
        recovery.accepted(299)
        assertEquals(6_000L, recovery.retryDelayMs)
        recovery.accepted(1)
        assertEquals(300L, recovery.retryDelayMs)
        assertTrue(recovery.failed())
    }

    @Test
    fun failedWriteDoesNotConsumeRenderedBeatOrClip() {
        val pending = AudioMixBlock(shortArrayOf(100, 50))
        val voice = shortArrayOf(1, 2, 3, 4)
        val block = ShortArray(6)
        pending.render(block, 4, true, 0, 7, voice, 0, 9, false)
        assertArrayEquals(shortArrayOf(101, 52, 3, 4, 100, 50), block)
        assertEquals(0L, pending.phase)
        assertEquals(0, pending.clipPosition)
        assertFalse(pending.clipFinished)
        // No samples accepted: reopening must render exactly the same source positions.
        val retry = ShortArray(6)
        pending.render(retry, 4, true, pending.phase, 7, voice, pending.clipPosition, 9, false)
        assertArrayEquals(block, retry)
        assertEquals(7, pending.metronomeEpoch)
        assertEquals(9, pending.playToken)
    }

    @Test
    fun partialWriteResumesUnwrittenSuffixAndCompletesOnlyAtAcceptedTail() {
        val pending = AudioMixBlock(shortArrayOf(100, 50))
        val voice = shortArrayOf(1, 2, 3, 4)
        val first = ShortArray(6)
        pending.render(first, 4, true, 0, 0, voice, 0, 0, false)
        pending.accept(2)
        assertEquals(2L, pending.phase)
        assertEquals(2, pending.clipPosition)
        assertFalse(pending.clipFinished)
        val replacement = ShortArray(6)
        pending.render(replacement, 4, true, pending.phase, 0, voice, pending.clipPosition, 0, false)
        assertArrayEquals(shortArrayOf(3, 4, 100, 50, 0, 0), replacement)
        pending.accept(1)
        assertFalse(pending.clipFinished)
        pending.accept(1)
        assertTrue(pending.clipFinished)
        assertEquals(4L, pending.phase)
        assertEquals(4, pending.clipPosition)
    }

    @Test
    fun voiceMuteStillConsumesVoiceButNotTheMetronome() {
        val pending = AudioMixBlock(shortArrayOf(100, 50))
        val block = ShortArray(4)
        pending.render(block, 4, true, 0, 0, shortArrayOf(3, 4), 0, 0, true)
        assertArrayEquals(shortArrayOf(100, 50, 0, 0), block)
        pending.accept(block.size)
        assertTrue(pending.clipFinished)
        assertEquals(4L, pending.phase)
    }

    @Test
    fun stoppedMetronomeDoesNotAdvanceAndMixSaturates() {
        val pending = AudioMixBlock(shortArrayOf(Short.MAX_VALUE))
        val block = ShortArray(2)
        pending.render(block, 1, true, 0, 0, shortArrayOf(1, Short.MIN_VALUE), 0, 0, false)
        assertArrayEquals(shortArrayOf(Short.MAX_VALUE, -1), block)
        pending.render(block, 1, false, 8, 0, null, 0, 0, false)
        pending.accept(2)
        assertArrayEquals(shortArrayOf(0, 0), block)
        assertEquals(8L, pending.phase)
    }
}
