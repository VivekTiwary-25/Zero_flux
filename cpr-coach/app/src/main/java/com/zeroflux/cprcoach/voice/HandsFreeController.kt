package com.zeroflux.cprcoach.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.zeroflux.cprcoach.ui.MicState
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns the microphone and VAD, but not the injected [SpeechEngine] or [CoroutineScope]. */
class HandsFreeController(
    context: Context,
    private val speech: SpeechEngine,
    private val scope: CoroutineScope,
    private val silenceTimeoutMs: Long = 6_000,
) {
    private val applicationContext = context.applicationContext
    private val assets = applicationContext.assets
    private val mutableState = MutableStateFlow(MicState.OFF)
    val state: StateFlow<MicState> = mutableState.asStateFlow()

    @Volatile
    var onTranscript: ((String) -> Unit)? = null

    private val lock = Any()
    private val speechMutex = Mutex()
    private val captureMutex = Mutex()
    private var session: Job? = null
    private var activeRecorder: AudioRecord? = null
    private var released = false

    fun toggle(languageCode: String) {
        synchronized(lock) {
            if (released) return
            if (session != null) {
                stopSession()
                return
            }
            if (!hasMicrophonePermission()) {
                Log.w(TAG, "Microphone permission denied")
                mutableState.value = MicState.OFF
                return
            }
            val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                val owner = currentCoroutineContext().job
                try {
                    speechMutex.withLock {
                        currentCoroutineContext().ensureActive()
                        check(speech.hasModel(languageCode)) { "Speech model unavailable for language=$languageCode" }
                        speech.preload(languageCode)
                    }
                    currentCoroutineContext().ensureActive()
                    // A previous native decode may still be returning after cancellation.
                    captureMutex.withLock { capture(owner, languageCode) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    Log.e(TAG, "Hands-free listening failed", failure)
                }
            }
            session = job
            mutableState.value = MicState.LOADING
            job.invokeOnCompletion {
                synchronized(lock) {
                    if (session === job) {
                        session = null
                        mutableState.value = MicState.OFF
                    }
                }
            }
            job.start()
        }
    }

    fun stop() = stopSession()

    fun release() {
        synchronized(lock) {
            released = true
            stopSession()
            onTranscript = null
        }
    }

    private fun stopSession(expected: Job? = null) {
        synchronized(lock) {
            if (expected != null && session !== expected) return
            session?.cancel()
            session = null
            mutableState.value = MicState.OFF
            stopMicrophone()
        }
    }

    private fun stopMicrophone(expected: AudioRecord? = null) {
        synchronized(lock) {
            val input = activeRecorder ?: return
            if (expected != null && input !== expected) return
            activeRecorder = null
            // Cancellation alone cannot interrupt AudioRecord's blocking native read.
            cleanup("stop microphone") { input.stop() }
        }
    }

    private suspend fun capture(owner: Job, languageCode: String) = coroutineScope {
        var vad: Vad? = null
        var recorder: AudioRecord? = null
        var echoCanceler: AcousticEchoCanceler? = null
        val segments = Channel<FloatArray>(Channel.UNLIMITED)
        var recognition: Job? = null
        var watchdog: Job? = null
        val transcriptDeadline = AtomicLong(Long.MAX_VALUE)
        try {
            currentCoroutineContext().ensureActive()
            check(hasMicrophonePermission()) { "Microphone permission denied" }
            val silero = SileroVadModelConfig(model = "silero_vad.onnx")
            // Fail in Kotlin before passing a missing asset to the native model loader.
            assets.open(silero.model).use { }
            val detector = Vad(
                assetManager = assets,
                config = VadModelConfig(sileroVadModelConfig = silero, sampleRate = SAMPLE_RATE),
            )
            vad = detector
            val minBufferBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minBufferBytes > 0) { "Unsupported microphone buffer: $minBufferBytes" }
            check(
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            ) { "Microphone permission revoked" }
            val input = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferBytes, silero.windowSize * Short.SIZE_BYTES),
            )
            recorder = input
            check(input.state == AudioRecord.STATE_INITIALIZED) { "Microphone did not initialize" }
            if (AcousticEchoCanceler.isAvailable()) {
                val effect = AcousticEchoCanceler.create(input.audioSessionId)
                echoCanceler = effect
                // Listening without echo cancelling still works, so this must not end the session.
                if (effect == null || effect.setEnabled(true) != AudioEffect.SUCCESS) Log.w(TAG, "Could not enable echo canceler")
            }
            val lastSpeechAt = AtomicLong(SystemClock.elapsedRealtime())
            synchronized(lock) {
                // Loading may finish after stop/release. Serialize this final gate and startup
                // with stopSession so a disposed owner can never start the microphone later.
                owner.ensureActive()
                check(!released && session === owner) { "Microphone session is no longer current" }
                check(hasMicrophonePermission()) { "Microphone permission revoked" }
                activeRecorder = input
                input.startRecording()
                check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Microphone did not start recording"
                }
                lastSpeechAt.set(SystemClock.elapsedRealtime())
                mutableState.value = MicState.LISTENING
            }
            recognition = launch(Dispatchers.Default) {
                // One consumer, never on the microphone reader's coroutine.
                for (samples in segments) {
                    val text = speechMutex.withLock {
                        currentCoroutineContext().ensureActive()
                        speech.transcribe(samples, SAMPLE_RATE, languageCode)
                    }
                    if (text.isNotBlank()) {
                        withContext(Dispatchers.Main.immediate) {
                            synchronized(lock) {
                                if (session === owner && owner.isActive &&
                                    SystemClock.elapsedRealtime() < transcriptDeadline.get()
                                ) onTranscript?.invoke(text)
                            }
                        }
                    }
                }
            }
            // Register cancellation cleanup before entering a blocking native read.
            watchdog = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    while (isActive) {
                        if (!hasMicrophonePermission()) {
                            Log.w(TAG, "Microphone permission revoked")
                            stopSession(owner)
                            return@launch
                        }
                        val remaining = silenceTimeoutMs - (SystemClock.elapsedRealtime() - lastSpeechAt.get())
                        if (remaining <= 0) {
                            synchronized(lock) {
                                if (session !== owner || !owner.isActive) return@launch
                                transcriptDeadline.set(SystemClock.elapsedRealtime() + TRANSCRIPT_DRAIN_TIMEOUT_MS)
                                stopMicrophone(input)
                            }
                            Log.i(TAG, "Auto-muted after VAD silence; draining transcripts")
                            delay(TRANSCRIPT_DRAIN_TIMEOUT_MS)
                            stopSession(owner)
                            return@launch
                        }
                        delay(remaining)
                    }
                } finally {
                    // Cancellation/errors must unblock read, without cancelling a graceful drain.
                    stopMicrophone(input)
                }
            }
            val pcm = ShortArray(silero.windowSize)
            val window = FloatArray(silero.windowSize)
            var filled = 0
            while (currentCoroutineContext().isActive && transcriptDeadline.get() == Long.MAX_VALUE) {
                check(hasMicrophonePermission()) { "Microphone permission revoked" }
                val count = input.read(pcm, filled, pcm.size - filled, AudioRecord.READ_BLOCKING)
                currentCoroutineContext().ensureActive()
                check(hasMicrophonePermission()) { "Microphone permission revoked" }
                if (count <= 0 && transcriptDeadline.get() != Long.MAX_VALUE) break
                check(count > 0) { "Microphone read failed: $count" }
                filled += count
                if (filled != pcm.size) continue
                for (index in pcm.indices) {
                    window[index] = pcm[index] / -Short.MIN_VALUE.toFloat()
                }
                detector.acceptWaveform(window)
                if (detector.isSpeechDetected()) lastSpeechAt.set(SystemClock.elapsedRealtime())
                while (!detector.empty()) {
                    val samples = detector.front().samples
                    detector.pop()
                    check(segments.trySend(samples).isSuccess) { "Transcription worker stopped" }
                }
                filled = 0
            }
            currentCoroutineContext().ensureActive()
            // The bundled sherpa VAD can emit speech not yet ended by a silence window.
            detector.flush()
            while (!detector.empty()) {
                val samples = detector.front().samples
                detector.pop()
                check(segments.trySend(samples).isSuccess) { "Transcription worker stopped" }
            }
            segments.close()
            recognition.join()
        } finally {
            watchdog?.cancel()
            recognition?.cancel()
            segments.cancel()
            synchronized(lock) {
                if (activeRecorder === recorder) activeRecorder = null
            }
            recorder?.let { input ->
                cleanup("stop microphone") {
                    if (input.recordingState == AudioRecord.RECORDSTATE_RECORDING) input.stop()
                }
                echoCanceler?.let { effect -> cleanup("release echo canceler") { effect.release() } }
                cleanup("release microphone") { input.release() }
            }
            vad?.let { detector -> cleanup("release VAD") { detector.release() } }
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private inline fun cleanup(operation: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Throwable) {
            Log.e(TAG, "Failed to $operation", failure)
        }
    }

    private companion object {
        const val TAG = "CPR_MIC"
        // Required input format for the bundled Silero model and speech recognizers.
        const val SAMPLE_RATE = 16_000
        const val TRANSCRIPT_DRAIN_TIMEOUT_MS = 3_000L
    }
}
