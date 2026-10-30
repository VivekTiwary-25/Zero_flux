package com.zeroflux.cprcoach.ui

import androidx.compose.runtime.compositionLocalOf
import com.zeroflux.cprcoach.engine.TrackingState

/** The ten languages, in picker order. `code` names assets/strings/<code>.json and assets/clips/<code>/. */
enum class AppLanguage(val code: String, val nativeName: String) {
    EN("en", "English"),
    HI("hi", "हिन्दी"),
    KN("kn", "ಕನ್ನಡ"),
    TA("ta", "தமிழ்"),
    TE("te", "తెలుగు"),
    ML("ml", "മലയാളം"),
    MR("mr", "मराठी"),
    GU("gu", "ગુજરાતી"),
    BN("bn", "বাংলা"),
    OR("or", "ଓଡ଼ିଆ"),
}

/** Screen text lookup. Every visible word goes through this, so that language changes apply everywhere. */
fun interface Strings {
    fun get(key: String): String
}

val LocalStrings = compositionLocalOf<Strings> { Strings { it } }

enum class MicState { OFF, LOADING, LISTENING }

/** State of the camera screen. Framing is a state of this one screen, not a separate screen. */
sealed interface CameraUiState {
    /** Getting set up. [hintKey] is an on-screen string key for the holder, never spoken. Null when ready. */
    data class Framing(val ready: Boolean, val hintKey: String?) : CameraUiState

    /** Spoken 3-2-1 before the session. */
    data class Countdown(val secondsLeft: Int) : CameraUiState

    data class Live(
        val tracking: TrackingState,
        /** Only non-null when it is really measured. If null, never show a number. */
        val ratePerMinute: Int?,
        val inRange: Boolean?,
        /** Null when the arms can't be seen. */
        val elbowsBent: Boolean?,
        val secondsLeft: Int,
        val totalSeconds: Int,
        val isPaused: Boolean,
    ) : CameraUiState
}
