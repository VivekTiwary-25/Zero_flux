package com.zeroflux.cprcoach

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.zeroflux.cprcoach.audio.AudioEngine
import com.zeroflux.cprcoach.audio.MetronomeService
import com.zeroflux.cprcoach.engine.EmergencySequence
import com.zeroflux.cprcoach.ui.AppLanguage
import com.zeroflux.cprcoach.ui.EmergencyScreen
import com.zeroflux.cprcoach.ui.EmergencyUiState
import com.zeroflux.cprcoach.voice.VoiceCommand
import java.util.ArrayDeque
import kotlinx.coroutines.delay

internal fun openEmergencyDialer(context: Context) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A device without a dialer must still be able to provide CPR coaching.
    }
}

@Composable
fun EmergencyHost(language: AppLanguage, voice: VoiceRouter, onHome: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val playback = remember(appContext) { EmergencyPlayback(appContext) }
    val engine by MetronomeService.engine.collectAsState()
    val micState by voice.state.collectAsState()
    val currentHome by rememberUpdatedState(onHome)

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
                VoiceCommand.STOP -> {
                    playback.stop()
                    currentHome()
                }
                else -> Unit
            }
        }
        onDispose { unbind() }
    }
    // Deliberately composition-scoped, not lifecycle-scoped: the dialer may cover us.
    LaunchedEffect(playback) {
        while (true) {
            delay(1_000L)
            playback.tick()
        }
    }

    EmergencyScreen(
        state = EmergencyUiState(
            compressing = playback.compressing,
            helperCameraOn = false,
            stepKey = playback.stepKey,
        ),
        micState = micState,
        onCall112 = { openEmergencyDialer(context) },
        onStartCompressions = { playback.start(language.code) },
        onToggleHelperCamera = {},
        onStopConfirmed = {
            playback.stop()
            currentHome()
        },
        onMicTap = { voice.toggle() },
    )
}

/** Main-thread owner of the sequence and its single in-flight voice clip. */
private class EmergencyPlayback(private val context: Context) {
    var compressing by mutableStateOf(false)
        private set
    var stepKey by mutableStateOf<String?>(null)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sequence = EmergencySequence()
    private val pending = ArrayDeque<String>()
    private var engine: AudioEngine? = null
    private var currentKey: String? = null
    private var sequenceStarted = false
    private var generation = 0L
    private var stopped = false

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

    private fun playNext() {
        if (stopped || !compressing || currentKey != null || pending.isEmpty()) return
        val audio = engine ?: return
        val key = pending.removeFirst()
        currentKey = key
        stepKey = when (key) {
            "em_call" -> "em_step_call"
            "em_hands" -> "em_step_hands"
            "em_push" -> "em_step_push"
            else -> stepKey
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
