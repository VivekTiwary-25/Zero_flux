package com.zeroflux.cprcoach

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zeroflux.cprcoach.audio.AudioEngine
import com.zeroflux.cprcoach.engine.LandmarkGuard
import com.zeroflux.cprcoach.engine.PoseEngine
import com.zeroflux.cprcoach.engine.PoseFrame
import com.zeroflux.cprcoach.engine.PractiseSession
import com.zeroflux.cprcoach.engine.Scorecard
import com.zeroflux.cprcoach.engine.SessionAudio
import com.zeroflux.cprcoach.ui.AppLanguage
import com.zeroflux.cprcoach.ui.AppSettings
import com.zeroflux.cprcoach.engine.OverlayPose
import com.zeroflux.cprcoach.ui.ArmOverlay
import com.zeroflux.cprcoach.ui.BeatSource
import com.zeroflux.cprcoach.ui.CameraOverlay
import com.zeroflux.cprcoach.ui.PushLog
import com.zeroflux.cprcoach.ui.CameraUiState
import com.zeroflux.cprcoach.ui.CoachColors
import com.zeroflux.cprcoach.ui.LocalStrings
import com.zeroflux.cprcoach.engine.TrackingState
import com.zeroflux.cprcoach.ui.DashboardStore
import com.zeroflux.cprcoach.ui.MicButton
import com.zeroflux.cprcoach.ui.PoseCameraView
import com.zeroflux.cprcoach.ui.ScorecardScreen
import com.zeroflux.cprcoach.ui.WalkthroughScreen
import com.zeroflux.cprcoach.voice.VoiceCommand
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PractiseHost(
    settings: AppSettings,
    language: AppLanguage,
    voice: VoiceRouter,
    cameraPermission: Boolean,
    onCameraVisibleChange: (Boolean) -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val replayName = remember(context) {
        if (BuildConfig.DEBUG) PractiseReplay.requestedFile(context) else null
    }
    var replayFailed by remember(replayName) { mutableStateOf(false) }
    val audio = remember { PractiseAudio() }
    // Keep the completion clip alive on the scorecard; the Practise host owns its audio.
    DisposableEffect(voice, audio) {
        val engine = AudioEngine(context)
        audio.engine = engine
        val detachAudio = voice.attachAudio(engine)
        onDispose {
            detachAudio()
            audio.engine = null
            engine.release()
        }
    }
    // VoiceRouter applies the current language to the attached audio engine.
    var walkthrough by remember { mutableStateOf(!settings.walkthroughSeen) }
    var scorecard by remember { mutableStateOf<Scorecard?>(null) }
    val cameraVisible = !walkthrough && scorecard == null
    val reportCameraVisible by rememberUpdatedState(onCameraVisibleChange)

    DisposableEffect(cameraVisible) {
        reportCameraVisible(cameraVisible)
        onDispose { reportCameraVisible(false) }
    }

    Column(Modifier.fillMaxSize()) {
        if (replayName != null) {
            Text(
                LocalStrings.current.get(if (replayFailed) "debug_replay_error" else "debug_replay"),
                color = CoachColors.Amber,
                modifier = Modifier.fillMaxWidth().background(CoachColors.CameraScrim).padding(8.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                walkthrough -> PractiseQuietScreen(voice) { modifier ->
                    val finishWalkthrough = {
                        settings.walkthroughSeen = true
                        walkthrough = false
                    }
                    WalkthroughScreen(
                        onSkip = finishWalkthrough,
                        onStart = finishWalkthrough,
                        modifier = modifier,
                    )
                }
                scorecard != null -> PractiseQuietScreen(voice) { modifier ->
                    ScorecardScreen(
                        card = requireNotNull(scorecard),
                        onSwapRoles = { scorecard = null },
                        onHome = onHome,
                        modifier = modifier,
                    )
                }
                else -> PractiseCamera(
                    audio = audio,
                    voice = voice,
                    cameraPermission = cameraPermission,
                    replayName = replayName,
                    onReplayError = { replayFailed = true },
                    onWalkthrough = {
                        audio.engine?.stopClips()
                        walkthrough = true
                    },
                    onScorecard = { card, rates ->
                        scorecard = card
                        // PractiseCamera reports each session once, so one call is one session to save.
                        if (replayName == null) {
                            val request = PendingSave(
                                id = "p-${System.currentTimeMillis()}",
                                endedAtEpochMs = System.currentTimeMillis(),
                                card = card,
                                rates = rates,
                            )
                            val app = context.applicationContext
                            // Own scope: leaving the screen right after the scorecard must not cancel the save.
                            saveScope.launch {
                                try {
                                    DashboardStore.savePractise(
                                        context = app,
                                        id = request.id,
                                        endedAtEpochMs = request.endedAtEpochMs,
                                        durationSeconds = request.card.sessionSeconds,
                                        averageRate = request.card.averageRate,
                                        secondsInRange = request.card.secondsInRange,
                                        elbowsStraightPercent = request.card.elbowsStraightPercent,
                                        pauses = request.card.pauses,
                                        measuredSeconds = request.card.measuredSeconds,
                                        rates = request.rates,
                                    )
                                } catch (_: Exception) {
                                    // A failed save must not affect the session or the scorecard.
                                }
                            }
                        }
                    },
                    onHome = onHome,
                )
            }
        }
    }
}

/** Reserve a separate bottom row, rather than covering either screen's navigation. */
@Composable
private fun PractiseQuietScreen(
    voice: VoiceRouter,
    content: @Composable (Modifier) -> Unit,
) {
    val micState by voice.state.collectAsState()
    DisposableEffect(voice) {
        val unbind = voice.bind(emptySet()) { }
        onDispose { unbind() }
    }
    Column(
        Modifier.fillMaxSize().background(CoachColors.Navy)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content(Modifier.fillMaxSize())
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            MicButton(state = micState, onTap = voice::toggle)
        }
    }
}

@Composable
private fun PractiseCamera(
    audio: PractiseAudio,
    voice: VoiceRouter,
    cameraPermission: Boolean,
    replayName: String?,
    onReplayError: () -> Unit,
    onWalkthrough: () -> Unit,
    onScorecard: (Scorecard, List<Int?>) -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val session = remember { PractiseSession(audio) }
    val capture = remember { PractiseCapture() }
    val overlayPose = remember { OverlayPose() }
    // The skeleton keeps the last plausible arm instead of flickering onto a wrong wrist.
    val overlayGuard = remember { LandmarkGuard() }
    val pushLog = remember { PushLog() }
    // Remembered here, so a new PractiseCamera (a new session) starts with its own rates and flag.
    val rateSamples = remember { mutableListOf<Int?>() }
    val reported = remember { AtomicBoolean(false) }
    val beat = remember { BeatSource.of { audio.engine } }
    // Real and replayed frames both pass here, so the dots always come from detected pushes.
    val feedFrame: (PoseFrame) -> Unit = remember(session, pushLog) {
        { frame ->
            val live = session.ui.value as? CameraUiState.Live
            if (live != null && !live.isPaused) pushLog.onFrame(frame)
            session.onFrame(frame)
        }
    }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val ui by session.ui.collectAsState()
    val micState by voice.state.collectAsState()
    val showScorecard by rememberUpdatedState(onScorecard)
    val goHome by rememberUpdatedState(onHome)
    val permission by rememberUpdatedState(cameraPermission)
    val replaying = BuildConfig.DEBUG && replayName != null
    val reportReplayError by rememberUpdatedState(onReplayError)
    val traceDirectory = remember(context) {
        File(context.getExternalFilesDir(null) ?: context.filesDir, "traces")
    }


    LaunchedEffect(session) {
        while (isActive) {
            val live = session.ui.value as? CameraUiState.Live
            if (live != null) {
                // A paused second has no rate: missing, not zero.
                rateSamples.add(if (live.isPaused || live.tracking != TrackingState.BEATING) null else live.ratePerMinute)
                delay(1_000)
            } else {
                delay(250)
            }
        }
    }
    val startSession: () -> Unit = {
        if (permission || replaying) {
            val wasFraming = session.ui.value is CameraUiState.Framing
            session.start(SystemClock.uptimeMillis())
            if (!replaying && wasFraming && session.ui.value is CameraUiState.Countdown) {
                capture.startTrace(traceDirectory)
            }
        }
    }
    val stopSession: () -> Unit = {
        session.stop()
        capture.stopTrace()
        audio.engine?.stopClips()
        val card = session.scorecard.value
        if (card != null) {
            if (reported.compareAndSet(false, true)) showScorecard(card, rateSamples.toList())
        } else goHome()
    }
    val startCurrent by rememberUpdatedState(startSession)
    val stopCurrent by rememberUpdatedState(stopSession)

    DisposableEffect(session, voice) {
        audio.reset()
        val unbind = voice.bind(setOf(VoiceCommand.START, VoiceCommand.STOP, VoiceCommand.REPEAT)) {
            when (it) {
                VoiceCommand.START -> startCurrent()
                VoiceCommand.STOP -> stopCurrent()
                VoiceCommand.REPEAT -> audio.repeat()
                else -> Unit
            }
        }
        onDispose {
            unbind()
            session.stop()
            capture.stopTrace()
        }
    }

    DisposableEffect(session, cameraPermission, replaying) {
        val active = AtomicBoolean(true)
        if (!replaying && cameraPermission) {
            // The model takes a moment to load, and the screen must not stall for it. Same as EmergencyHost.
            thread(name = "PractisePoseLoad", isDaemon = true) {
                try {
                    val pose = PoseEngine(context.applicationContext) { frame ->
                        overlayPose.push(overlayGuard.overlayFrame(frame), SystemClock.uptimeMillis())
                        handler.post {
                            if (active.get()) feedFrame(frame)
                        }
                    }
                    if (active.get()) {
                        capture.open(pose)
                        // Disposed while opening: the engine still has exactly one owner.
                        if (!active.get()) capture.close()
                    } else {
                        pose.close()
                    }
                } catch (error: Throwable) {
                    // Includes native load errors: on this thread they would otherwise kill the app.
                    Log.e("CPR_ENGINE", "practise pose engine unavailable", error)
                }
            }
        } else if (!replaying) {
            session.onFrame(PoseFrame(SystemClock.uptimeMillis(), null, null, 0L))
        }
        onDispose {
            active.set(false)
            capture.close()
        }
    }

    LaunchedEffect(session, replayName) {
        if (replaying) {
            try {
                PractiseReplay.play(context, requireNotNull(replayName)) { frame ->
                    overlayPose.push(overlayGuard.overlayFrame(frame), SystemClock.uptimeMillis())
                    feedFrame(frame)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("CPR_REPLAY", "Replay failed; camera remains disabled", error)
                reportReplayError()
            }
        }
    }

    LaunchedEffect(session) {
        session.onEngineError = { error -> Log.e("CPR_ENGINE", "analysis failed, showing no number", error) }
        while (isActive) {
            session.tick(SystemClock.uptimeMillis())
            val card = session.scorecard.value
            if (card != null) {
                capture.stopTrace()
                if (reported.compareAndSet(false, true)) showScorecard(card, rateSamples.toList())
                break
            }
            delay(250L)
        }
    }

    Box(Modifier.fillMaxSize().background(CoachColors.Navy)) {
        if (!replaying && cameraPermission) {
            PoseCameraView(onImage = capture::submit, modifier = Modifier.fillMaxSize())
        }
        ArmOverlay(overlayPose, (ui as? CameraUiState.Live)?.posture, showLabels = true)
        CameraOverlay(
            state = if (cameraPermission || replaying) ui else CameraUiState.Framing(false, "frame_no_person"),
            micState = micState,
            onStart = startSession,
            onPauseToggle = session::togglePause,
            onStop = stopSession,
            onMicTap = voice::toggle,
            beat = beat,
            pushes = pushLog,
        )
        // Help only while setting up: leaving a running session here would lose it.
        if (ui is CameraUiState.Framing) {
            TextButton(
                onClick = onWalkthrough,
                modifier = Modifier.align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(LocalStrings.current.get("practise_help"), color = CoachColors.TextPrimary)
            }
        }
    }
}

private class PractiseAudio : SessionAudio {
    var engine: AudioEngine? = null
    private var lastKey: String? = null

    override fun play(key: String) {
        lastKey = key
        engine?.play(key)
    }

    override fun startMetronome() { engine?.startMetronome() }
    override fun stopMetronome() { engine?.stopMetronome() }
    override fun stopVoice() { engine?.stopClips() }
    fun repeat() { lastKey?.let { engine?.play(it) } }
    fun reset() {
        lastKey = null
        engine?.stopClips()
    }
}

/** Serialize submission against teardown; every rejected image still has an owner. */
private class PractiseCapture {
    private val lock = Any()
    private var engine: PoseEngine? = null

    fun open(pose: PoseEngine) {
        synchronized(lock) { engine = pose }
    }

    fun submit(image: ImageProxy) {
        // PoseEngine guards its own teardown, and holding this lock would make the main thread wait on MediaPipe.
        val pose = synchronized(lock) { engine }
        if (pose == null) image.close() else pose.submit(image)
    }

    fun startTrace(directory: File) {
        synchronized(lock) { engine?.startTrace(directory) }
    }

    fun stopTrace() {
        synchronized(lock) { engine?.stopTrace() }
    }

    fun close() {
        val pose = synchronized(lock) {
            engine.also { engine = null }
        }
        pose?.close()
    }
}

private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private data class PendingSave(
    val id: String,
    val endedAtEpochMs: Long,
    val card: Scorecard,
    val rates: List<Int?>,
)
