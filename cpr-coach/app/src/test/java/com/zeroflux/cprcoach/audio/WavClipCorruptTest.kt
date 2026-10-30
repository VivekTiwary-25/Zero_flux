package com.zeroflux.cprcoach.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** A corrupt clip must fail with IllegalArgumentException (logged by AudioEngine), never anything else. */
class WavClipCorruptTest {

    private fun wav(rate: Int, dataDeclared: Long, dataActual: Int, fmtSize: Int = 16): ByteArray {
        val b = ByteBuffer.allocate(12 + 8 + fmtSize + 8 + dataActual).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(0).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(fmtSize)
        if (fmtSize >= 16) {
            b.putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            repeat(fmtSize - 16) { b.put(0) }
        } else {
            repeat(fmtSize) { b.put(0) }
        }
        b.put("data".toByteArray()).putInt(dataDeclared.toInt())
        repeat(dataActual) { b.put(1) }
        return b.array()
    }

    private fun assertRejectedOrSafe(bytes: ByteArray) {
        try {
            WavClip.parse(bytes)
        } catch (_: IllegalArgumentException) {
        } catch (e: Throwable) {
            fail("unexpected ${e::class.java.name}: ${e.message}")
        }
    }

    @Test
    fun emptyDataChunkIsEmptyClip() {
        assertEquals(0, WavClip.parse(wav(16_000, 0, 0)).samples.size)
    }

    @Test
    fun truncatedDataIsRejected() = assertRejectedOrSafe(wav(16_000, 1000, 10))

    @Test
    fun hugeDeclaredSizeIsRejected() = assertRejectedOrSafe(wav(16_000, 0xFFFFFFFFL, 10))

    @Test
    fun hugeAndNegativeRatesAreSafe() {
        assertRejectedOrSafe(wav(Int.MAX_VALUE, 4, 4))
        assertRejectedOrSafe(wav(-5, 4, 4))
        assertRejectedOrSafe(wav(0, 4, 4))
    }

    @Test
    fun shortFmtAndGarbageAreRejected() {
        assertRejectedOrSafe(wav(16_000, 4, 4, fmtSize = 8))
        assertRejectedOrSafe(ByteArray(0))
        assertRejectedOrSafe(ByteArray(11))
        assertRejectedOrSafe(ByteArray(64) { 0x52 })
    }
}
