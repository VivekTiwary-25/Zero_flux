package com.zeroflux.cprcoach

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.zeroflux.cprcoach.audio.AudioEngine
import com.zeroflux.cprcoach.ui.AppLanguage
import com.zeroflux.cprcoach.ui.MicState
import com.zeroflux.cprcoach.ui.loadCommandTable
import com.zeroflux.cprcoach.voice.CommandMatcher
import com.zeroflux.cprcoach.voice.HandsFreeController
import com.zeroflux.cprcoach.voice.MatchResult
import com.zeroflux.cprcoach.voice.SpeechEngine
import com.zeroflux.cprcoach.voice.VoiceCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One microphone for the app. Routes and live audio are owned by the visible host. */
class VoiceRouter(
    context: Context,
    scope: CoroutineScope,
    initialLanguage: AppLanguage,
    private val onLanguage: (AppLanguage) -> Unit,
) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val speech = SpeechEngine(app)
    private val voiceJob = SupervisorJob(scope.coroutineContext[Job])
    private val voiceScope = CoroutineScope(scope.coroutineContext + voiceJob)
    private val controller = HandsFreeController(app, speech, voiceScope)
    private val matcher = CommandMatcher(loadCommandTable(app))
    private val engines = mutableSetOf<AudioEngine>()
    private var language = initialLanguage
    private var released = false
    @Volatile private var languageEpoch = 0
    @Volatile private var route: Route? = null

    val state: StateFlow<MicState> = controller.state

    private class Route(
        val allowed: Set<VoiceCommand>,
        val allowLanguages: Boolean,
        val onCommand: (VoiceCommand) -> Unit,
    )

    init {
        controller.onTranscript = { transcript ->
            val destination = route
            val epoch = languageEpoch
            handler.post {
                // A transcript queued before navigation or a language change cannot act afterward.
                if (!released && destination != null && route === destination && epoch == languageEpoch) {
                    when (val result = matcher.match(
                        transcript, language.code, destination.allowed, destination.allowLanguages,
                    )) {
                        is MatchResult.Command -> destination.onCommand(result.command)
                        is MatchResult.Language -> AppLanguage.entries
                            .firstOrNull { it.code == result.code }?.let(onLanguage)
                        MatchResult.None -> Unit
                    }
                }
            }
        }
        voiceScope.launch {
            state.collect { mic ->
                engines.forEach { it.setVoiceMuted(mic == MicState.LISTENING) }
            }
        }
    }

    fun bind(
        allowed: Set<VoiceCommand>,
        allowLanguages: Boolean = false,
        onCommand: (VoiceCommand) -> Unit,
    ): () -> Unit {
        val binding = Route(allowed, allowLanguages, onCommand)
        route = binding
        return { if (route === binding) route = null }
    }

    fun attachAudio(engine: AudioEngine): () -> Unit {
        engines.add(engine)
        engine.setLanguage(language.code)
        engine.setVoiceMuted(state.value == MicState.LISTENING)
        return {
            engines.remove(engine)
            engine.setVoiceMuted(false)
        }
    }

    fun setLanguage(value: AppLanguage) {
        if (language == value || released) return
        controller.stop()
        languageEpoch += 1
        language = value
        engines.forEach {
            it.setVoiceMuted(false)
            it.setLanguage(value.code)
        }
    }

    fun toggle() {
        if (released) return
        if (state.value != MicState.OFF) {
            controller.stop()
            return
        }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED || !speech.hasModel(language.code)) return
        // The existing recognizer reports English as available even when its assets are absent.
        // Check before native construction: missing model files must never abort the process.
        if (language == AppLanguage.EN && ENGLISH_ASSETS.any { name ->
                runCatching { app.assets.open(name).use { } }.isFailure
            }) return
        controller.toggle(language.code)
    }

    fun release() {
        if (released) return
        released = true
        route = null
        controller.release()
        engines.forEach { it.setVoiceMuted(false) }
        engines.clear()
        voiceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        // Native decoding can outlive cancellation. Do not free its recognizer until it returns.
        CoroutineScope(Dispatchers.Default).launch {
            voiceJob.join()
            speech.release()
        }
    }

    private companion object {
        val ENGLISH_ASSETS = listOf(
            "tiny.en-encoder.int8.onnx", "tiny.en-decoder.int8.onnx", "tiny.en-tokens.txt",
        )
    }
}
