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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.zeroflux.cprcoach.audio.AudioEngine
import com.zeroflux.cprcoach.audio.MetronomeService
import com.zeroflux.cprcoach.engine.EmergencyCoach
import com.zeroflux.cprcoach.engine.EmergencySequence
import com.zeroflux.cprcoach.engine.EmergencyWord
import com.zeroflux.cprcoach.engine.HandoverSummary
import com.zeroflux.cprcoach.engine.LandmarkGuard
import com.zeroflux.cprcoach.engine.OverlayPose
import com.zeroflux.cprcoach.engine.LiveAnalyzer
import com.zeroflux.cprcoach.engine.LiveReading
import com.zeroflux.cprcoach.engine.PoseEngine
import com.zeroflux.cprcoach.engine.TrackingState
import com.zeroflux.cprcoach.ui.AppLanguage
import com.zeroflux.cprcoach.ui.ArmOverlay
import com.zeroflux.cprcoach.ui.BeatSource
import com.zeroflux.cprcoach.ui.CoachColors
import com.zeroflux.cprcoach.ui.EmergencyContact
import com.zeroflux.cprcoach.ui.EmergencyScreen
import com.zeroflux.cprcoach.ui.EmergencySummaryScreen
import com.zeroflux.cprcoach.ui.EmergencyUiState
import com.zeroflux.cprcoach.ui.LocalStrings
import com.zeroflux.cprcoach.ui.PoseCameraView
import com.zeroflux.cprcoach.voice.VoiceCommand
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
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

internal fun openEmergencyContactDialer(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
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
    emergencyContact: EmergencyContact?,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val startMs = remember { SystemClock.uptimeMillis() }
    val playback = remember(appContext) { EmergencyPlayback(appContext) }
    val coach = remember { EmergencyCoach(startMs) }
    val engine by MetronomeService.engine.collectAsState()
    val currentEngine by rememberUpdatedState(engine)
    val beat = remember { BeatSource.of { currentEngine } }
    val micState by voice.state.collectAsState()
    val currentHome by rememberUpdatedState(onHome)
    val reportCameraVisible by rememberUpdatedState(onCameraVisibleChange)
    var summary by remember { mutableStateOf<HandoverSummary?>(null) }
    var word by remember { mutableStateOf(EmergencyWord.KEEP_PUSHING) }
    var needsSetup by remember { mutableStateOf(true) }
    var posture by remember { mutableStateOf<com.zeroflux.cprcoach.engine.PostureReading?>(null) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var audioStarted by remember { mutableStateOf(false) }
    val analyzerBox = remember { arrayOf(LiveAnalyzer()) }
    val overlayPose = remember { OverlayPose() }
    // Its own guard (not the analyzer's): the camera thread draws, the main thread analyses. Same as PractiseHost.
    val overlayGuard = remember { LandmarkGuard() }
    val replayName = remember(context) {
        if (BuildConfig.DEBUG) PractiseReplay.requestedFile(context) else null
    }

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
    // Debug only: synthetic input, so the real screen can be checked without a person.
    if (replayName != null) {
        LaunchedEffect(replayName) {
            try {
                PractiseReplay.play(context, replayName) {
                    overlayPose.push(overlayGuard.overlayFrame(it), android.os.SystemClock.uptimeMillis())
                    analyzerBox[0].addFrame(it)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("CPR_REPLAY", "Emergency replay failed", error)
            }
        }
    }
    // Straight away, not behind a button: CPR never waits. The camera is only composed after this.
    LaunchedEffect(playback) {
        playback.start(language.code)
        audioStarted = true
    }
    // Deliberately composition-scoped, not lifecycle-scoped: the dialer may cover us.
    LaunchedEffect(playback, coach) {
        var lastSequenceTickMs = 0L
        while (isActive && summary == null) {
            val now = SystemClock.uptimeMillis()
            elapsedSeconds = ((now - startMs) / 1000L).toInt()
            try {
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
                // Not side-on means no judgement: the same hint says how to fix the view.
                needsSetup = coach.needsSetup || coach.turnPhoneHint
                posture = reading.posture
                if (playback.voiceIdle) {
                    coach.nextCorrection(reading)?.let { playback.speak(it.key) }
                }
            } catch (error: RuntimeException) {
                if (error is CancellationException) throw error
                // The app and the beat share a process, so nothing here may escape and kill both.
                Log.e("CPR_ENGINE", "emergency coaching step failed; carrying on", error)
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
            instructionKey = when (playback.spokenKey) {
                "em_aed" -> "em_reminder_aed"
                "em_continue" -> "em_reminder_continue"
                "em_swap" -> "em_reminder_swap"
                else -> if (word == EmergencyWord.KEEP_PUSHING) playback.stepKey ?: word.key else word.key
            },
            elapsedSeconds = elapsedSeconds,
            showSetupHint = needsSetup && cameraPermission,
        ),
        micState = micState,
        onCall112 = { openEmergencyDialer(context) },
        onContact = emergencyContact?.let { contact ->
            { openEmergencyContactDialer(context, contact.phoneNumber) }
        },
        contactName = emergencyContact?.name,
        onStopConfirmed = finish,
        onMicTap = { voice.toggle() },
        beat = beat,
        camera = {
            if (audioStarted && cameraPermission && replayName == null) {
                EmergencyCamera(overlayPose, overlayGuard, onFrame = { frame ->
                    try {
                        analyzerBox[0].addFrame(frame)
                    } catch (error: RuntimeException) {
                        Log.e("CPR_ENGINE", "emergency frame failed; carrying on", error)
                        analyzerBox[0] = LiveAnalyzer()
                    }
                })
            }
            ArmOverlay(overlayPose, posture, showLabels = false)
            if (replayName != null) {
                Text(
                    LocalStrings.current.get("debug_replay"),
                    color = CoachColors.Amber,
                    modifier = Modifier.padding(top = 56.dp, start = 200.dp),
                )
            }
        },
    )
}

/** Camera failure of any kind only means no corrections, so nothing here may reach the audio. */
@Composable
private fun EmergencyCamera(overlayPose: OverlayPose, overlayGuard: LandmarkGuard, onFrame: (com.zeroflux.cprcoach.engine.PoseFrame) -> Unit) {
    val context = LocalContext.current
    val capture = remember { EmergencyCapture() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val currentFrame by rememberUpdatedState(onFrame)
    val traceDirectory = remember(context) {
        File(context.getExternalFilesDir(null) ?: context.filesDir, "traces")
    }

    DisposableEffect(capture, context) {
        val active = AtomicBoolean(true)
        // The model takes a moment to load, and the screen must not stall for it.
        thread(name = "EmergencyPoseLoad", isDaemon = true) {
            try {
                val pose = PoseEngine(context.applicationContext) { frame ->
                    overlayPose.push(overlayGuard.overlayFrame(frame), android.os.SystemClock.uptimeMillis())
                    if (active.get()) {
                        handler.post {
                            if (active.get()) currentFrame(frame)
                        }
                    }
                }
                if (BuildConfig.DEBUG) {
                    try {
                        pose.startTrace(traceDirectory)
                    } catch (error: Exception) {
                        Log.w("CPR_ENGINE", "emergency trace unavailable", error)
                    }
                }
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
    var spokenKey by mutableStateOf<String?>(null)
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
            spokenKey = null
            return
        }
        val audio = engine ?: return
        val key = pending.removeFirst()
        currentKey = key
        spokenKey = key
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
                    spokenKey = null
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
        spokenKey = null
        currentKey = null
        compressing = false
        engine?.stopClips()
        engine?.stopMetronome()
        engine = null
        mainHandler.removeCallbacksAndMessages(null)
        MetronomeService.stop(context)
    }
}
