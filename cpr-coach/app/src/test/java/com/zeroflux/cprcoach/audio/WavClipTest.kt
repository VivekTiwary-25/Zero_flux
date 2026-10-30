package com.zeroflux.cprcoach.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavClipTest {

    @Test
    fun parsesHeaderAndSkipsExtraChunk() {
        val source = shortArrayOf(0, 3_000, -1_000, 32_000)
        val junk = byteArrayOf(0x7f, 0x7f, 0x00)
        val wav = wavBytes(
            sampleRate = 16_000,
            samples = source,
            extraBeforeData = chunk("JUNK", junk),
            extraAfterData = chunk("fact", byteArrayOf(1, 0, 0, 0)),
            fmtExtraBytes = 2,
        )
        val clip = WavClip.parse(wav)
        assertEquals(WavClip.SAMPLE_RATE, clip.sampleRate)
        assertEquals(source.size * WavClip.SAMPLE_RATE / 16_000, clip.samples.size)
        assertEquals(source[0].toInt(), clip.samples[0].toInt())
        assertEquals(source[1].toInt(), clip.samples[3].toInt())
        assertEquals(1_000, clip.samples[1].toInt())
    }

    @Test
    fun resampledLengthMatchesVoiceRates() {
        val at16k = WavClip.parse(wavBytes(16_000, ShortArray(160) { it.toShort() }))
        assertEquals(160 * WavClip.SAMPLE_RATE / 16_000, at16k.samples.size)

        val at22050 = WavClip.parse(wavBytes(22_050, ShortArray(147) { it.toShort() }))
        assertEquals(147 * WavClip.SAMPLE_RATE / 22_050, at22050.samples.size)
        assertEquals(320, at22050.samples.size)

        val already = WavClip.parse(wavBytes(WavClip.SAMPLE_RATE, shortArrayOf(5, -6, 7)))
        assertEquals(3, already.samples.size)
        assertEquals(-6, already.samples[1].toInt())
    }

    @Test
    fun rejectsBadFormats() {
        val nonPcm = assertThrows(IllegalArgumentException::class.java) {
            WavClip.parse(wavBytes(16_000, shortArrayOf(0), format = 3, bits = 16))
        }
        assertTrue(nonPcm.message!!.contains("PCM"))

        val eightBit = assertThrows(IllegalArgumentException::class.java) {
            WavClip.parse(wavBytes(16_000, shortArrayOf(0), format = 1, bits = 8))
        }
        assertTrue(eightBit.message!!.contains("16-bit"))

        val stereo = assertThrows(IllegalArgumentException::class.java) {
            WavClip.parse(wavBytes(16_000, shortArrayOf(0, 1), channels = 2))
        }
        assertTrue(stereo.message!!.contains("mono"))

        val notWave = assertThrows(IllegalArgumentException::class.java) {
            WavClip.parse(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
        }
        assertTrue(notWave.message!!.contains("RIFF"))
    }

    private fun assertThrows(type: Class<out Throwable>, body: () -> Unit): Throwable =
        org.junit.Assert.assertThrows(type, body)
}

private fun wavBytes(
    sampleRate: Int,
    samples: ShortArray,
    format: Int = 1,
    bits: Int = 16,
    channels: Int = 1,
    extraBeforeData: ByteArray? = null,
    extraAfterData: ByteArray? = null,
    fmtExtraBytes: Int = 0,
): ByteArray {
    val fmt = ByteArray(16 + fmtExtraBytes)
    writeU16(fmt, 0, format)
    writeU16(fmt, 2, channels)
    writeU32(fmt, 4, sampleRate)
    val blockAlign = channels * bits / 8
    writeU32(fmt, 8, sampleRate * blockAlign)
    writeU16(fmt, 12, blockAlign)
    writeU16(fmt, 14, bits)
    val data = ByteArray(samples.size * 2)
    for (i in samples.indices) {
        data[i * 2] = (samples[i].toInt() and 0xff).toByte()
        data[i * 2 + 1] = (samples[i].toInt() shr 8 and 0xff).toByte()
    }
    val chunks = ArrayList<ByteArray>()
    chunks.add(chunk("fmt ", fmt))
    if (extraBeforeData != null) chunks.add(extraBeforeData)
    chunks.add(chunk("data", data))
    if (extraAfterData != null) chunks.add(extraAfterData)
    var payload = 4
    for (part in chunks) payload += part.size
    val out = ByteArray(8 + payload)
    writeFourCc(out, 0, "RIFF")
    writeU32(out, 4, payload)
    writeFourCc(out, 8, "WAVE")
    var pos = 12
    for (part in chunks) {
        part.copyInto(out, pos)
        pos += part.size
    }
    return out
}

private fun chunk(id: String, body: ByteArray): ByteArray {
    val out = ByteArray(8 + body.size + (body.size and 1))
    writeFourCc(out, 0, id)
    writeU32(out, 4, body.size)
    body.copyInto(out, 8)
    return out
}

private fun writeFourCc(out: ByteArray, offset: Int, id: String) {
    for (i in 0 until 4) out[offset + i] = id[i].code.toByte()
}

private fun writeU16(out: ByteArray, offset: Int, value: Int) {
    out[offset] = (value and 0xff).toByte()
    out[offset + 1] = (value shr 8 and 0xff).toByte()
}

private fun writeU32(out: ByteArray, offset: Int, value: Int) {
    out[offset] = (value and 0xff).toByte()
    out[offset + 1] = (value shr 8 and 0xff).toByte()
    out[offset + 2] = (value shr 16 and 0xff).toByte()
    out[offset + 3] = (value shr 24 and 0xff).toByte()
}
