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
    usage: Int = AudioAttributes.USAGE_MEDIA,
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

    // Guarded by lock. The audio thread rebuilds its track when this differs from the track's usage.
    private var currentUsage = usage

    private var languageCode: String? = null
    private var languageEpoch = 0
    private var playToken = 0
    private var metronomeOn = false
    private var metronomeEpoch = 0
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
        runOnLoader {
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
            metronomeEpoch += 1
            period = nextPeriod
            phase = 0L
            metronomeOn = true
            lock.notifyAll()
        }
    }

    fun stopMetronome() {
        synchronized(lock) {
            metronomeOn = false
            metronomeEpoch += 1
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
            // A replaced clip still reports done, so a chain of clips can never hang.
            postToMain(clipDone)
            playToken += 1
            clip = null
            clipPos = 0
            // Own the callback even while loading, so failure/stop can complete it.
            clipDone = onDone
            if (cached != null) {
                clip = cached.samples
                clipPos = 0
                lock.notifyAll()
            }
            playToken
        }
        if (token < 0) {
            postToMain(onDone)
            return
        }
        if (cached != null) return
        runOnLoader {
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
                else if (released || samples == null) {
                    postToMain(clipDone)
                    clipDone = null
                    LoadOutcome.DONE
                }
                else {
                    clip = samples
                    clipPos = 0
                    lock.notifyAll()
                    LoadOutcome.STARTED
                }
            }
            if (outcome == LoadOutcome.DONE) {
                if (samples == null) Log.w(TAG, "missing clip: clips/$code/$key.wav")
            }
        }
    }

    /**
     * Changes the AudioAttributes usage of the running engine. The audio thread rebuilds its single
     * track at the next block boundary; beat phase, clip position, mute and callbacks are untouched
     * because they only advance on accepted writes. No-op after release.
     */
    fun setUsage(newUsage: Int) {
        synchronized(lock) {
            if (released || currentUsage == newUsage) return
            currentUsage = newUsage
            lock.notifyAll()
        }
    }

    fun stopClips() {
        synchronized(lock) {
            postToMain(clipDone)
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
            thread.join(RELEASE_WAIT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (pending != null) postToMain(pending)
    }

    /** The loader is shut down by release(), which can race with a call already on its way here. */
    private fun runOnLoader(block: () -> Unit) {
        try {
            loader.execute(block)
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            Log.w(TAG, "clip load skipped: engine released", e)
        }
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
            postToMain(clipDone)
            playToken += 1
            clip = null
            clipPos = 0
            clipDone = null
        }
    }

    private fun runLoop() {
        var track: AudioTrack? = null
        var trackUsage = 0
        val block = ShortArray(BLOCK_SAMPLES)
        val pending = AudioMixBlock(click)
        val recovery = AudioRecovery(
            REOPEN_WAIT_MS,
            RECOVERY_WINDOW_MS,
            WavClip.SAMPLE_RATE * RECOVERY_WINDOW_MS / 1000,
        )
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (!released) {
                synchronized(lock) {
                    while (!released && !metronomeOn && clip == null) {
                        lock.wait()
                    }
                }
                if (released) break
                var failure: Exception? = null
                try {
                    if (track != null && synchronized(lock) { currentUsage != trackUsage }) {
                        // Usage changed: swap tracks. Nothing was consumed for the unwritten block.
                        trackRef = null
                        try {
                            track.release()
                        } catch (e: Exception) {
                            Log.w(TAG, "AudioTrack release failed", e)
                        }
                        track = null
                    }
                    if (track == null) {
                        track = openTrack()
                        if (track != null) trackUsage = openedUsage
                    }
                    val playbackTrack = track
                    if (playbackTrack != null && !released) {
                        if (playbackTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            playbackTrack.play()
                        }
                        render(block, pending)
                        if (writeAll(playbackTrack, block, pending, recovery)) continue
                    }
                } catch (e: Exception) {
                    failure = e
                }
                if (released) break
                // One warning per outage, even if replacement tracks fail after a few blocks.
                val report = recovery.failed()
                if (report) Log.w(TAG, "AudioTrack unavailable; retrying until release", failure)
                trackRef = null
                try {
                    track?.release()
                } catch (e: Exception) {
                    if (report) Log.w(TAG, "AudioTrack release failed", e)
                }
                track = null
                if (!waitForRetry(recovery.retryDelayMs)) break
            }
        } catch (e: InterruptedException) {
            if (!released) Log.e(TAG, "Audio playback interrupted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback failed", e)
        } finally {
            abandonPlayback()
            trackRef = null
            try {
                track?.release()
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack release failed", e)
            }
        }
    }

    private fun waitForRetry(delayMs: Long): Boolean = synchronized(lock) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(delayMs)
        while (!released) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0L) break
            java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(lock, remaining)
        }
        !released
    }

    // Creation/publication and release's flag change share the lock: no post-release open.
    private var openedUsage = 0

    private fun openTrack(): AudioTrack? = synchronized(lock) {
        if (released) return@synchronized null
        val wantedUsage = currentUsage
        try {
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
                        .setUsage(wantedUsage)
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
                null
            } else {
                openedUsage = wantedUsage
                track.also { trackRef = it }
            }
        } catch (_: Exception) {
            // The recovery loop rate-limits diagnostics for both open and write failures.
            null
        }
    }

    private fun render(block: ShortArray, pending: AudioMixBlock) {
        synchronized(lock) {
            pending.render(
                block, period, metronomeOn, phase, metronomeEpoch,
                clip, clipPos, playToken, voiceMuted,
            )
        }
    }

    private fun commit(pending: AudioMixBlock, wrote: Int) {
        synchronized(lock) {
            pending.accept(wrote)
            if (released) return
            // A UI command can replace/reset either source while a blocking write is in flight.
            if (pending.metronomeEpoch == metronomeEpoch) phase = pending.phase
            if (pending.playToken == playToken && pending.samples != null && clip === pending.samples) {
                if (pending.clipFinished) {
                    val callback = clipDone
                    clip = null
                    clipPos = 0
                    clipDone = null
                    postToMain(callback)
                } else {
                    clipPos = pending.clipPosition
                }
            }
        }
    }

    private fun writeAll(
        track: AudioTrack,
        data: ShortArray,
        pending: AudioMixBlock,
        recovery: AudioRecovery,
    ): Boolean {
        var offset = 0
        while (offset < data.size && !released) {
            val wrote = track.write(data, offset, data.size - offset)
            if (wrote <= 0) return false
            commit(pending, wrote)
            recovery.accepted(wrote)
            offset += wrote
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
                playToken += 1
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
        private const val REOPEN_WAIT_MS = 300L
        // Reuse the former 20-attempt horizon for slow retries and sustained-success detection.
        private const val RECOVERY_WINDOW_MS = 20 * REOPEN_WAIT_MS
        private const val RELEASE_WAIT_MS = 500L
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
    }
}

/** Retry forever; a short-lived replacement must not reset backoff or diagnostic suppression. */
internal class AudioRecovery(
    private val initialDelayMs: Long,
    private val recoveryWindowMs: Long,
    private val stableSamples: Long,
) {
    private var failedForMs = 0L
    private var acceptedSamples = 0L

    val retryDelayMs: Long
        get() = if (failedForMs >= recoveryWindowMs) recoveryWindowMs else initialDelayMs

    /** True only for the first failure of an outage. */
    fun failed(): Boolean {
        val first = failedForMs == 0L
        acceptedSamples = 0L
        failedForMs = minOf(recoveryWindowMs, failedForMs + initialDelayMs)
        return first
    }

    fun accepted(count: Int) {
        if (failedForMs == 0L) return
        acceptedSamples = minOf(stableSamples, acceptedSamples + count)
        if (acceptedSamples >= stableSamples) {
            failedForMs = 0L
            acceptedSamples = 0L
        }
    }
}

/** One reusable rendered snapshot. Only accepted write prefixes advance its source positions. */
internal class AudioMixBlock(private val click: ShortArray) {
    var metronomeEpoch = 0
        private set
    var playToken = 0
        private set
    var samples: ShortArray? = null
        private set
    private var initialPhase = 0L
    private var initialClipPosition = 0
    private var metro = false
    private var accepted = 0

    val phase: Long get() = initialPhase + if (metro) accepted else 0
    val clipPosition: Int
        get() = minOf(samples?.size ?: 0, initialClipPosition + accepted)
    val clipFinished: Boolean
        get() = samples != null && clipPosition >= samples!!.size

    fun render(
        block: ShortArray,
        period: Long,
        metronomeOn: Boolean,
        phase: Long,
        metronomeEpoch: Int,
        samples: ShortArray?,
        clipPosition: Int,
        playToken: Int,
        voiceMuted: Boolean,
    ) {
        this.metronomeEpoch = metronomeEpoch
        this.playToken = playToken
        this.samples = samples
        initialPhase = phase
        initialClipPosition = clipPosition
        metro = metronomeOn && period > 0L
        accepted = 0
        var beatPhase = phase
        var pos = clipPosition
        for (i in block.indices) {
            var mix = 0
            if (metro) {
                val clickIndex = (beatPhase % period).toInt()
                if (clickIndex < click.size) mix += click[clickIndex].toInt()
                beatPhase++
            }
            if (samples != null && pos < samples.size) {
                if (!voiceMuted) mix += samples[pos].toInt()
                pos++
            }
            block[i] = mix.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    fun accept(count: Int) {
        accepted += count
    }
}
