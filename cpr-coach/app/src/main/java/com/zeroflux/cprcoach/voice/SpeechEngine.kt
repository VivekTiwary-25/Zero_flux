package com.zeroflux.cprcoach.voice

import android.content.Context

/** Speech-to-text entry point. Call methods sequentially from a background thread. */
class SpeechEngine(context: Context) {

    private val recognizerManager = SpeechRecognizerManager(context)

    fun transcribe(samples: FloatArray, sampleRate: Int, languageCode: String): String =
        recognizerManager.recognizeAudio(
            samples = samples,
            sampleRate = sampleRate,
            language = languageCode,
        )

    fun hasModel(languageCode: String): Boolean = recognizerManager.hasModel(languageCode)

    /** Loads the selected recogniser synchronously, so the next transcription avoids model loading. */
    fun preload(languageCode: String) {
        recognizerManager.preload(languageCode)
    }

    fun release() {
        recognizerManager.release()
    }
}
