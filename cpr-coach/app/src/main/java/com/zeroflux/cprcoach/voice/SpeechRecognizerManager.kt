package com.zeroflux.cprcoach.voice

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class SpeechRecognizerManager(private val context: Context) {

    // Languages served by IndicConformer (NeMo CTC). English stays on Whisper tiny.en.
    private val indicConformerLanguages = setOf("hi", "gu", "mr", "ta", "te", "or", "bn", "kn", "ml")

    private val indicConformerDir = File(context.filesDir, INDIC_CONFORMER_DIR_NAME)

    // Hold this lock from model selection through stream release, including native construction.
    private val modelLock = Any()
    // Set before waiting for the lock so queued operations cannot reload after teardown.
    private val released = AtomicBoolean(false)

    private var currentModelKey = ""
    private var recognizer: OfflineRecognizer? = null

    // IndicConformer wrappers are per language (each one is a thin graph over the shared encoder
    // weights), so switching language means a new recognizer; the same language reuses it.
    private fun modelKeyFor(language: String): String = when {
        language == "en" -> "en"
        language in indicConformerLanguages -> "ic-$language"
        else -> ""
    }

    fun hasModel(language: String): Boolean {
        return try {
            when {
                language == "en" -> {
                    for (asset in listOf(EN_ENCODER, EN_DECODER, EN_TOKENS)) {
                        context.assets.open(asset).use { }
                    }
                    true
                }
                language in indicConformerLanguages -> indicFiles(language).all {
                    it.isFile && it.canRead() && it.length() > 0 && it.inputStream().use { input -> input.read() != -1 }
                }
                else -> false
            }
        } catch (failure: Exception) {
            Log.e(PERF_TAG, "Speech model unavailable for language=$language", failure)
            false
        }
    }

    private fun indicFiles(language: String) = listOf(
        File(indicConformerDir, "$language.onnx"),
        File(indicConformerDir, "languages/$language/tokens.txt"),
        File(indicConformerDir, INDIC_WEIGHTS),
    )

    /** Load the selected model now, or fail if it is unavailable or cannot initialize. */
    fun preload(language: String) {
        synchronized(modelLock) {
            checkNotNull(getRecognizer(language)) { "Speech model could not be loaded for language=$language" }
        }
    }

    // Caller must hold modelLock for the entire lifetime of any use of the returned recognizer.
    private fun getRecognizer(language: String): OfflineRecognizer? {
        if (released.get()) return null
        try {
            val modelKey = modelKeyFor(language)
            if (modelKey.isEmpty() || !hasModel(language)) {
                Log.e(PERF_TAG, "No available speech model for language=$language")
                return null
            }

            // Reuse the currently loaded model only while its files remain available.
            if (recognizer != null && currentModelKey == modelKey) {
                return recognizer
            }

            // Release before loading another model to avoid two native sessions in memory.
            val previous = recognizer
            recognizer = null
            currentModelKey = ""
            previous?.release()

            // English is bundled; IndicConformer reads private files by absolute path.
            var assetManager: android.content.res.AssetManager? = context.assets
            val modelConfig = if (modelKey == "en") {
                OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = EN_ENCODER,
                        decoder = EN_DECODER,
                        language = "en",
                        task = "transcribe"
                    ),
                    tokens = EN_TOKENS,
                    modelType = "whisper",
                    numThreads = 2,
                    provider = "cpu"
                )
            } else {
                assetManager = null
                val (model, tokens) = indicFiles(language)
                OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = model.absolutePath),
                    tokens = tokens.absolutePath,
                    numThreads = 2,
                    provider = "cpu"
                )
            }

            // Recheck directly before native construction: bad paths can abort native code.
            if (!hasModel(language)) {
                Log.e(PERF_TAG, "Speech model disappeared for language=$language")
                return null
            }
            val loadStartMs = SystemClock.elapsedRealtime()
            val loaded = OfflineRecognizer(
                assetManager = assetManager,
                config = OfflineRecognizerConfig(modelConfig = modelConfig)
            )
            if (released.get()) {
                // Teardown may have been requested while native construction held the lock.
                loaded.release()
                return null
            }
            recognizer = loaded
            currentModelKey = modelKey
            Log.d(PERF_TAG, "model load key=$modelKey deltaMs=${SystemClock.elapsedRealtime() - loadStartMs}")
            return loaded
        } catch (failure: Exception) {
            Log.e(PERF_TAG, "Speech model initialization failed for language=$language", failure)
            return null
        } catch (failure: LinkageError) {
            Log.e(PERF_TAG, "Speech native library unavailable for language=$language", failure)
            return null
        }
    }

    fun recognizeAudio(
        samples: FloatArray,
        sampleRate: Int,
        language: String
    ): String = synchronized(modelLock) {

        // PERF-INSTRUMENTATION (temporary): splits model-load cost from decode cost and
        // records audio duration so real-time factor can be computed from logcat alone.
        val audioMs = if (sampleRate > 0) samples.size * 1000L / sampleRate else 0L
        val getStartMs = SystemClock.elapsedRealtime()
        // Missing, unsupported or unloadable models produce no transcript.
        val currentRecognizer = getRecognizer(language) ?: return@synchronized ""
        val loadMs = SystemClock.elapsedRealtime() - getStartMs
        val stream = currentRecognizer.createStream()

        try {
            val decodeStartMs = SystemClock.elapsedRealtime()
            stream.acceptWaveform(
                samples,
                sampleRate = sampleRate
            )

            currentRecognizer.decode(stream)

            val text = currentRecognizer.getResult(stream).text
            val decodeMs = SystemClock.elapsedRealtime() - decodeStartMs
            Log.d(
                PERF_TAG,
                "decode lang=$language audioMs=$audioMs loadMs=$loadMs decodeMs=$decodeMs " +
                    "rtf=${if (audioMs > 0) decodeMs.toDouble() / audioMs else -1.0} textLen=${text.length}"
            )
            text

        } finally {
            stream.release()
        }
    }

    fun release() {
        val firstRelease = released.compareAndSet(false, true)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Never block the UI behind native loading or decoding. This thread ends after cleanup.
            if (firstRelease) {
                Thread({ releaseRecognizer() }, "SpeechRecognizerRelease").start()
            }
        } else {
            releaseRecognizer()
        }
    }

    private fun releaseRecognizer() {
        synchronized(modelLock) {
            val previous = recognizer
            recognizer = null
            currentModelKey = ""
            previous?.release()
        }
    }

    companion object {
        // PERF-INSTRUMENTATION (temporary): remove with the perf-measurement pass.
        private const val PERF_TAG = "CPR_STT"
        private const val EN_ENCODER = "tiny.en-encoder.int8.onnx"
        private const val EN_DECODER = "tiny.en-decoder.int8.onnx"
        private const val EN_TOKENS = "tiny.en-tokens.txt"
        private const val INDIC_WEIGHTS = "shared/encoder.weights.bin"

        // The ONE place that says where the IndicConformer package lives on the phone: a folder in
        // the app's private files dir holding shared/encoder.weights.bin, <lang>.onnx and
        // languages/<lang>/tokens.txt. Swapping in another build of the package needs no code change:
        // push the files here (docs/STT_INDICCONFORMER.md).
        const val INDIC_CONFORMER_DIR_NAME = "indicconformer"

    }
}
