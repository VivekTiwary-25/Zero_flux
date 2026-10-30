package com.zeroflux.cprcoach

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.zeroflux.cprcoach.audio.AudioEngine
import com.zeroflux.cprcoach.audio.MetronomeService
import com.zeroflux.cprcoach.engine.EmergencyCoach
import com.zeroflux.cprcoach.engine.EmergencySequence
import com.zeroflux.cprcoach.engine.EmergencyWord
import com.zeroflux.cprcoach.engine.HandoverSummary
import com.zeroflux.cprcoach.engine.LiveAnalyzer
import com.zeroflux.cprcoach.engine.LiveReading
import com.zeroflux.cprcoach.engine.PoseEngine
import com.zeroflux.cprcoach.engine.TrackingState
import com.zeroflux.cprcoach.ui.AppLanguage
import com.zeroflux.cprcoach.ui.EmergencyScreen
import com.zeroflux.cprcoach.ui.EmergencySummaryScreen
import com.zeroflux.cprcoach.ui.EmergencyUiState
import com.zeroflux.cprcoach.ui.PoseCameraView
import com.zeroflux.cprcoach.voice.VoiceCommand
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal fun openEmergencyDialer(context: Context) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A device without a dialer must still be able to provide CPR coaching.
    }
}

/**
 * One tap starts everything. Audio starts first and depends on nothing else: the camera only ever
 * adds corrections on top, and if it never works the rescuer still has the beat and the steps.
 */
@Composable
fun EmergencyHost(
    language: AppLanguage,
    voice: VoiceRouter,
    cameraPermission: Boolean,
    onCameraVisibleChange: (Boolean) -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val startMs = remember { SystemClock.uptimeMillis() }
    val playback = remember(appContext) { EmergencyPlayback(appContext) }
    val coach = remember { EmergencyCoach(startMs) }
    val engine by MetronomeService.engine.collectAsState()
    val micState by voice.state.collectAsState()
    val currentHome by rememberUpdatedState(onHome)
    val reportCameraVisible by rememberUpdatedState(onCameraVisibleChange)
    var summary by remember { mutableStateOf<HandoverSummary?>(null) }
    var word by remember { mutableStateOf(EmergencyWord.KEEP_PUSHING) }
    var needsSetup by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    val analyzerBox = remember { arrayOf(LiveAnalyzer()) }

    val finish: () -> Unit = {
        if (summary == null) {
            playback.stop()
            summary = coach.summary(SystemClock.uptimeMillis())
        }
    }
    val finishCurrent by rememberUpdatedState(finish)

    val cameraVisible = summary == null
    DisposableEffect(cameraVisible) {
        reportCameraVisible(cameraVisible)
        onDispose { reportCameraVisible(false) }
    }
    // Stop is a deliberate hold. A stray back press in a panic must not end the coaching.
    BackHandler(enabled = summary == null) { }

    DisposableEffect(playback) {
        onDispose { playback.stop() }
    }
    // Attach before starting the opening clips so language and mic mute are already set.
    DisposableEffect(voice, engine) {
        val detach = engine?.let { voice.attachAudio(it) }
        onDispose { detach?.invoke() }
    }
    DisposableEffect(playback, engine) {
        playback.setEngine(engine)
        onDispose { playback.setEngine(null) }
    }
    DisposableEffect(voice, playback, context) {
        val unbind = voice.bind(setOf(VoiceCommand.CALL, VoiceCommand.STOP)) { command ->
            when (command) {
                VoiceCommand.CALL -> openEmergencyDialer(context)
                VoiceCommand.STOP -> finishCurrent()
                else -> Unit
            }
        }
        onDispose { unbind() }
    }
    // Straight away, not behind a button: CPR never waits.
    LaunchedEffect(playback) { playback.start(language.code) }
    // Deliberately composition-scoped, not lifecycle-scoped: the dialer may cover us.
    LaunchedEffect(playback, coach) {
        var lastSequenceTickMs = 0L
        while (isActive && summary == null) {
            val now = SystemClock.uptimeMillis()
            if (now - lastSequenceTickMs >= 1_000L) {
                lastSequenceTickMs = now
                playback.tick()
            }
            val reading = try {
                analyzerBox[0].read(now)
            } catch (error: RuntimeException) {
                // A bad reading must never reach the rescuer, and must never stop the coaching.
                Log.e("CPR_ENGINE", "emergency analysis failed, showing no instruction", error)
                analyzerBox[0] = LiveAnalyzer()
                LiveReading(now, TrackingState.LOST, null, null, null)
            }
            coach.update(reading)
            word = coach.word
            needsSetup = coach.needsSetup
            elapsedSeconds = ((now - startMs) / 1000L).toInt()
            if (playback.voiceIdle) {
                coach.nextCorrection(reading)?.let { playback.speak(it.key) }
            }
            delay(250L)
        }
    }

    val finished = summary
    if (finished != null) {
        EmergencySummaryScreen(
            summary = finished,
            onCall112 = { openEmergencyDialer(context) },
            onHome = { currentHome() },
        )
        return
    }
    EmergencyScreen(
        state = EmergencyUiState(
            // The opening steps say what to do first, until the camera has something better.
            instructionKey = if (word == EmergencyWord.KEEP_PUSHING) playback.stepKey ?: word.key else word.key,
            elapsedSeconds = elapsedSeconds,
            showSetupHint = needsSetup && cameraPermission,
        ),
        micState = micState,
        onCall112 = { openEmergencyDialer(context) },
        onStopConfirmed = finish,
        onMicTap = { voice.toggle() },
        camera = {
            if (cameraPermission) EmergencyCamera(onFrameSample = { analyzerBox[0].addFrame(it) })
        },
    )
}

/** Camera failure of any kind only means no corrections, so nothing here may reach the audio. */
@Composable
private fun EmergencyCamera(onFrameSample: (com.zeroflux.cprcoach.engine.PoseSample?) -> Unit) {
    val context = LocalContext.current
    val capture = remember { EmergencyCapture() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val currentSample by rememberUpdatedState(onFrameSample)
    val traceDirectory = remember(context) {
        File(context.getExternalFilesDir(null) ?: context.filesDir, "traces")
    }

    DisposableEffect(capture, context) {
        val active = AtomicBoolean(true)
        // The model takes a moment to load, and the screen must not stall for it.
        thread(name = "EmergencyPoseLoad", isDaemon = true) {
            try {
                val pose = PoseEngine(context.applicationContext) { frame ->
                    if (active.get()) {
                        handler.post {
                            if (active.get()) currentSample(frame.sample)
                        }
                    }
                }
                if (BuildConfig.DEBUG) pose.startTrace(traceDirectory)
                // Disposed while loading: open then close, so the engine has exactly one owner.
                capture.open(pose)
                if (!active.get()) capture.close()
            } catch (error: Throwable) {
                // Includes native load errors: on this thread they would otherwise kill the app and the beat.
                Log.e("CPR_ENGINE", "emergency pose engine unavailable; audio coaching continues", error)
            }
        }
        onDispose {
            active.set(false)
            handler.removeCallbacksAndMessages(null)
            capture.close()
        }
    }
    PoseCameraView(onImage = capture::submit, modifier = Modifier.fillMaxSize())
}

/** Snapshot the engine under lock; PoseEngine owns submission/teardown races and late images. */
private class EmergencyCapture {
    private val lock = Any()
    private var engine: PoseEngine? = null

    fun open(pose: PoseEngine) {
        synchronized(lock) { engine = pose }
    }

    fun submit(image: ImageProxy) {
        // Do not make main-thread close wait for MediaPipe while holding this lock.
        val pose = synchronized(lock) { engine }
        if (pose == null) image.close() else pose.submit(image)
    }

    fun close() {
        val pose = synchronized(lock) {
            engine.also { engine = null }
        }
        pose?.stopTrace()
        pose?.close()
    }
}

/** Main-thread owner of the sequence and its single in-flight voice clip. */
private class EmergencyPlayback(private val context: Context) {
    var stepKey by mutableStateOf<String?>(null)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sequence = EmergencySequence()
    private val pending = ArrayDeque<String>()
    private var compressing = false
    private var engine: AudioEngine? = null
    private var currentKey: String? = null
    private var sequenceStarted = false
    private var generation = 0L
    private var stopped = false

    /** Scripted steps always go first. A correction only ever fills silence, so it is never stale. */
    val voiceIdle: Boolean
        get() = !stopped && sequenceStarted && engine != null && currentKey == null && pending.isEmpty()

    fun start(languageCode: String) {
        if (stopped || compressing) return
        MetronomeService.start(context, languageCode)
        compressing = true
        startWhenReady()
    }

    fun setEngine(next: AudioEngine?) {
        if (stopped || engine === next) return
        generation += 1
        engine?.stopClips()
        // Preserve an interrupted clip if the service replaces its audio engine.
        currentKey?.let { pending.addFirst(it) }
        currentKey = null
        engine = next
        startWhenReady()
    }

    private fun startWhenReady() {
        if (stopped || !compressing || engine == null) return
        if (!sequenceStarted) {
            sequenceStarted = true
            pending.addAll(sequence.start(SystemClock.uptimeMillis()))
        }
        playNext()
    }

    fun tick() {
        if (stopped || !compressing || !sequenceStarted) return
        pending.addAll(sequence.tick(SystemClock.uptimeMillis()))
        playNext()
    }

    fun speak(key: String) {
        if (!voiceIdle) return
        pending.add(key)
        playNext()
    }

    private fun playNext() {
        if (stopped || !compressing || currentKey != null) return
        if (pending.isEmpty()) {
            stepKey = null
            return
        }
        val audio = engine ?: return
        val key = pending.removeFirst()
        currentKey = key
        stepKey = when (key) {
            "em_call" -> "em_step_call"
            "em_hands" -> "em_step_hands"
            "em_push" -> "em_step_push"
            else -> null
        }
        val token = ++generation
        audio.play(key) {
            // Always defer to main: even a synchronous/missing-clip callback cannot recurse.
            mainHandler.post {
                if (!stopped && token == generation && engine === audio) {
                    currentKey = null
                    playNext()
                }
            }
        }
    }

    fun stop() {
        if (stopped) return
        // Invalidate onDone before stopping clips or requesting service shutdown.
        stopped = true
        generation += 1
        pending.clear()
        currentKey = null
        compressing = false
        engine?.stopClips()
        engine?.stopMetronome()
        engine = null
        mainHandler.removeCallbacksAndMessages(null)
        MetronomeService.stop(context)
    }
}
