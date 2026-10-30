package com.zeroflux.cprcoach.audio

/**
 * 16-bit mono PCM loaded from a RIFF WAV and resampled to [SAMPLE_RATE].
 *
 * Unknown chunks are skipped. Output length is
 * `sourceSampleCount * SAMPLE_RATE / sourceRate` (integer division), which keeps
 * the clip the same duration at the mix rate. Interpolation is linear.
 */
class WavClip(
    val samples: ShortArray,
    val sampleRate: Int,
) {
    companion object {
        const val SAMPLE_RATE = 48_000

        @JvmStatic
        fun parse(bytes: ByteArray): WavClip {
            val pcm = decodePcm16Mono(bytes)
            return WavClip(resampleLinear(pcm.samples, pcm.sampleRate, SAMPLE_RATE), SAMPLE_RATE)
        }
    }
}

private class Pcm16(val sampleRate: Int, val samples: ShortArray)

private fun decodePcm16Mono(bytes: ByteArray): Pcm16 {
    if (bytes.size < 12 || fourCc(bytes, 0) != "RIFF" || fourCc(bytes, 8) != "WAVE") {
        throw IllegalArgumentException("WAV is not a RIFF WAVE file")
    }
    var pos = 12
    var audioFormat: Int? = null
    var channels: Int? = null
    var rate: Int? = null
    var bits: Int? = null
    var dataOffset = -1
    var dataSize = 0
    while (pos + 8 <= bytes.size) {
        val id = fourCc(bytes, pos)
        val size = u32(bytes, pos + 4)
        val start = pos + 8
        if (size > bytes.size - start) {
            throw IllegalArgumentException("WAV chunk $id is truncated")
        }
        when (id) {
            "fmt " -> {
                if (size < 16) throw IllegalArgumentException("WAV fmt chunk is too small")
                audioFormat = u16(bytes, start)
                channels = u16(bytes, start + 2)
                rate = u32(bytes, start + 4).toInt()
                bits = u16(bytes, start + 14)
            }
            "data" -> if (dataOffset < 0) {
                dataOffset = start
                dataSize = size.toInt()
            }
        }
        val next = start + size + (size and 1L)
        pos = if (next > bytes.size) bytes.size else next.toInt()
    }
    if (audioFormat == null) throw IllegalArgumentException("WAV missing fmt chunk")
    if (audioFormat != PCM_FORMAT) {
        throw IllegalArgumentException("WAV is not PCM (format=$audioFormat)")
    }
    if (bits != 16) throw IllegalArgumentException("WAV is not 16-bit (bits=$bits)")
    if (channels != 1) throw IllegalArgumentException("WAV is not mono (channels=$channels)")
    if (rate == null || rate <= 0) throw IllegalArgumentException("WAV sample rate is invalid ($rate)")
    if (dataOffset < 0) throw IllegalArgumentException("WAV missing data chunk")
    val count = dataSize / 2
    val samples = ShortArray(count)
    var offset = dataOffset
    for (i in 0 until count) {
        samples[i] = s16(bytes, offset)
        offset += 2
    }
    return Pcm16(rate, samples)
}

private fun resampleLinear(input: ShortArray, sourceRate: Int, targetRate: Int): ShortArray {
    if (sourceRate <= 0) throw IllegalArgumentException("WAV sample rate is invalid ($sourceRate)")
    if (input.isEmpty() || sourceRate == targetRate) return input.copyOf()
    val outLen = (input.size.toLong() * targetRate / sourceRate).toInt()
    if (outLen <= 0) return ShortArray(0)
    val out = ShortArray(outLen)
    val last = input.lastIndex
    for (i in 0 until outLen) {
        val pos = i.toLong() * sourceRate
        val idx = (pos / targetRate).toInt().coerceAtMost(last)
        val frac = pos % targetRate
        val s0 = input[idx].toInt()
        val s1 = input[(idx + 1).coerceAtMost(last)].toInt()
        out[i] = (s0 + (s1 - s0).toLong() * frac / targetRate).toInt().toShort()
    }
    return out
}

private const val PCM_FORMAT = 1

private fun fourCc(bytes: ByteArray, offset: Int): String = buildString(4) {
    for (i in 0 until 4) append((bytes[offset + i].toInt() and 0xff).toChar())
}

private fun u16(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

private fun u32(bytes: ByteArray, offset: Int): Long =
    (bytes[offset].toLong() and 0xff) or
        ((bytes[offset + 1].toLong() and 0xff) shl 8) or
        ((bytes[offset + 2].toLong() and 0xff) shl 16) or
        ((bytes[offset + 3].toLong() and 0xff) shl 24)

private fun s16(bytes: ByteArray, offset: Int): Short =
    ((bytes[offset].toInt() and 0xff) or (bytes[offset + 1].toInt() shl 8)).toShort()
