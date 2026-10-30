package com.zeroflux.cprcoach.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.FileNotFoundException
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * One streaming track mixes the metronome click and at most one voice clip.
 *
 * The audio thread counts samples for the beat. It does not use a timer.
 * The main-thread Handler only delivers play()'s onDone callback.
 * Click shape matches LabAudioService.
 */
class AudioEngine(
    context: Context,
    private val usage: Int = AudioAttributes.USAGE_MEDIA,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Object()
    private val cacheLock = Object()
    private val clips = HashMap<String, WavClip>()
    private val loader = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AudioEngine-load").apply { isDaemon = true }
    }
    private val click = buildClick()

    private var languageCode: String? = null
    private var languageEpoch = 0
    private var playToken = 0
    private var metronomeOn = false
    private var period = 0L
    private var phase = 0L
    private var clip: ShortArray? = null
    private var clipPos = 0
    private var clipDone: (() -> Unit)? = null

    @Volatile private var voiceMuted = false
    @Volatile private var released = false
    @Volatile private var trackRef: AudioTrack? = null
    private var closed = false

    val metronomeRunning: Boolean
        get() = synchronized(lock) { metronomeOn }

    private val thread = Thread(::runLoop, "AudioEngine").apply {
        priority = Thread.MAX_PRIORITY
    }

    init {
        thread.start()
    }

    /** Loads assets/clips/[code]/[key].wav off the calling thread. */
    fun setLanguage(code: String) {
        val epoch = synchronized(cacheLock) {
            languageEpoch += 1
            languageCode = code
            clips.clear()
            languageEpoch
        }
        if (released) return
        loader.execute {
            try {
                loadDirectory(code, epoch)
            } catch (e: Exception) {
                Log.w(TAG, "clip load failed for $code", e)
            }
        }
    }

    fun startMetronome(bpm: Int = 110) {
        require(bpm > 0) { "bpm must be positive" }
        val nextPeriod = WavClip.SAMPLE_RATE * 60L / bpm
        require(nextPeriod > 0L) { "bpm is too high to schedule" }
        synchronized(lock) {
            if (released) return
            period = nextPeriod
            phase = 0L
            metronomeOn = true
            lock.notifyAll()
        }
    }

    fun stopMetronome() {
        synchronized(lock) {
            metronomeOn = false
            lock.notifyAll()
        }
    }

    fun play(key: String, onDone: (() -> Unit)? = null) {
        if (released) {
            Log.w(TAG, "missing clip: engine released ($key)")
            postToMain(onDone)
            return
        }
        val code: String?
        val epoch: Int
        val cached: WavClip?
        synchronized(cacheLock) {
            code = languageCode
            epoch = languageEpoch
            cached = clips[key]
        }
        if (code == null) {
            cancelCurrent()
            Log.w(TAG, "missing clip: language not set ($key)")
            postToMain(onDone)
            return
        }
        val token = synchronized(lock) {
            if (released) return@synchronized -1
            playToken += 1
            clip = null
            clipPos = 0
            clipDone = null
            if (cached != null) {
                clip = cached.samples
                clipPos = 0
                clipDone = onDone
                lock.notifyAll()
            }
            playToken
        }
        if (token < 0) {
            postToMain(onDone)
            return
        }
        if (cached != null) return
        loader.execute {
            val loaded = try {
                openClip(code, key)
            } catch (e: Exception) {
                Log.w(TAG, "missing clip: clips/$code/$key.wav", e)
                null
            }
            if (loaded != null) {
                synchronized(cacheLock) {
                    if (languageEpoch == epoch && languageCode == code) clips[key] = loaded
                }
            }
            val samples = loaded?.samples
            val outcome = synchronized(lock) {
                if (token != playToken) LoadOutcome.SUPERSEDED
                else if (released || samples == null) LoadOutcome.DONE
                else {
                    clip = samples
                    clipPos = 0
                    clipDone = onDone
                    lock.notifyAll()
                    LoadOutcome.STARTED
                }
            }
            if (outcome == LoadOutcome.DONE) {
                if (samples == null) Log.w(TAG, "missing clip: clips/$code/$key.wav")
                postToMain(onDone)
            }
        }
    }

    fun stopClips() {
        synchronized(lock) {
            playToken += 1
            clip = null
            clipPos = 0
            clipDone = null
            lock.notifyAll()
        }
    }

    /** Silences clips. The metronome keeps running, and a clip still advances. */
    fun setVoiceMuted(muted: Boolean) {
        voiceMuted = muted
    }

    fun release() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            released = true
            metronomeOn = false
            val pending = clipDone
            clip = null
            clipDone = null
            lock.notifyAll()
            pending
        }
        try {
            trackRef?.stop()
        } catch (_: Exception) {
        }
        loader.shutdownNow()
        try {
            thread.join()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (pending != null) postToMain(pending)
    }

    private fun loadDirectory(code: String, epoch: Int) {
        val names = try {
            appContext.assets.list("clips/$code")
        } catch (e: Exception) {
            Log.w(TAG, "clip folder missing: clips/$code", e)
            null
        } ?: return
        for (name in names) {
            if (released) return
            if (!name.endsWith(".wav", ignoreCase = true)) continue
            val current = synchronized(cacheLock) { languageEpoch == epoch && languageCode == code }
            if (!current) return
            val key = name.substring(0, name.length - 4)
            val loaded = openClip(code, name) ?: continue
            synchronized(cacheLock) {
                if (languageEpoch == epoch && languageCode == code) clips[key] = loaded
            }
        }
    }

    private fun openClip(code: String, fileName: String): WavClip? {
        val path = if (fileName.endsWith(".wav", ignoreCase = true)) {
            "clips/$code/$fileName"
        } else {
            "clips/$code/$fileName.wav"
        }
        return try {
            val bytes = appContext.assets.open(path).use { it.readBytes() }
            WavClip.parse(bytes)
        } catch (_: FileNotFoundException) {
            null
        } catch (e: Exception) {
            Log.w(TAG, "bad clip: $path", e)
            null
        }
    }

    private fun cancelCurrent() {
        synchronized(lock) {
            playToken += 1
            clip = null
            clipPos = 0
            clipDone = null
        }
    }

    private fun runLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val track = openTrack()
        if (track == null) {
            abandonPlayback()
            return
        }
        trackRef = track
        val block = ShortArray(BLOCK_SAMPLES)
        try {
            while (true) {
                synchronized(lock) {
                    while (!released && !metronomeOn && clip == null) {
                        try {
                            lock.wait()
                        } catch (_: InterruptedException) {
                            if (released) return
                        }
                    }
                    if (released) return
                }
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
                render(block)
                if (!writeAll(track, block)) return
            }
        } finally {
            trackRef = null
            try {
                track.release()
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack release failed", e)
            }
        }
    }

    private fun openTrack(): AudioTrack? {
        return try {
            val blockBytes = BLOCK_SAMPLES * BYTES_PER_SAMPLE
            val min = AudioTrack.getMinBufferSize(
                WavClip.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferBytes = if (min > 0) maxOf(min, blockBytes) else blockBytes
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(WavClip.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes + (bufferBytes and 1))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                Log.e(TAG, "AudioTrack failed to initialise")
                null
            } else {
                track
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init failed", e)
            null
        }
    }

    private fun render(block: ShortArray) {
        synchronized(lock) {
            val periodNow = period
            val metro = metronomeOn && periodNow > 0L
            var samples = clip
            var pos = clipPos
            for (i in block.indices) {
                var mix = 0
                if (metro) {
                    val clickIndex = (phase % periodNow).toInt()
                    if (clickIndex < click.size) mix += click[clickIndex].toInt()
                    phase++
                }
                val current = samples
                if (current != null) {
                    if (pos < current.size) {
                        if (!voiceMuted) mix += current[pos].toInt()
                        pos++
                    }
                    if (pos >= current.size) {
                        val callback = clipDone
                        clip = null
                        clipDone = null
                        samples = null
                        pos = 0
                        if (callback != null) postToMain(callback)
                    }
                }
                block[i] = saturate(mix)
            }
            clipPos = pos
        }
    }

    private fun writeAll(track: AudioTrack, data: ShortArray): Boolean {
        var offset = 0
        try {
            while (offset < data.size && !released) {
                val wrote = track.write(data, offset, data.size - offset)
                if (wrote <= 0) {
                    Log.e(TAG, "AudioTrack.write failed: $wrote")
                    abandonPlayback()
                    return false
                }
                offset += wrote
            }
        } catch (e: IllegalStateException) {
            if (!released) Log.w(TAG, "AudioTrack.write failed", e)
            abandonPlayback()
            return false
        }
        return !released
    }

    private fun abandonPlayback() {
        val pending = synchronized(lock) {
            if (released) {
                null
            } else {
                released = true
                metronomeOn = false
                val callback = clipDone
                clip = null
                clipDone = null
                callback
            }
        }
        if (pending != null) postToMain(pending)
    }

    private fun postToMain(callback: (() -> Unit)?) {
        if (callback == null) return
        mainHandler.post(callback)
    }

    private enum class LoadOutcome { STARTED, DONE, SUPERSEDED }

    companion object {
        private const val TAG = "AudioEngine"
        private const val BLOCK_MS = 10
        private const val BLOCK_SAMPLES = WavClip.SAMPLE_RATE * BLOCK_MS / 1000
        private const val BYTES_PER_SAMPLE = 2
        private const val CLICK_MS = 30
        private const val CLICK_HZ = 1800.0

        private fun buildClick(): ShortArray {
            val clickSamples = WavClip.SAMPLE_RATE * CLICK_MS / 1000
            val beat = ShortArray(clickSamples)
            for (i in 0 until clickSamples) {
                val fade = 1.0 - i.toDouble() / clickSamples
                beat[i] = (sin(2 * PI * CLICK_HZ * i / WavClip.SAMPLE_RATE) * fade * Short.MAX_VALUE * 0.9)
                    .toInt()
                    .toShort()
            }
            return beat
        }

        private fun saturate(value: Int): Short = when {
            value > Short.MAX_VALUE.toInt() -> Short.MAX_VALUE
            value < Short.MIN_VALUE.toInt() -> Short.MIN_VALUE
            else -> value.toShort()
        }
    }
}
