package com.zeroflux.cprcoach.ui

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * Resolves the default [AppLanguage] from a system language tag (such as "hi-IN", "or", "ory").
 * Falls back to [AppLanguage.EN] if the system language does not match any supported language.
 */
fun defaultLanguageFor(systemLanguageTag: String): AppLanguage {
    val clean = systemLanguageTag.trim()
    val base = clean.split('-', '_').firstOrNull()?.lowercase().orEmpty()
    val code = when (base) {
        "ory", "ori" -> "or"
        "eng" -> "en"
        "hin" -> "hi"
        "kan" -> "kn"
        "tam" -> "ta"
        "tel" -> "te"
        "mal" -> "ml"
        "mar" -> "mr"
        "guj" -> "gu"
        "ben" -> "bn"
        else -> base
    }
    return AppLanguage.entries.firstOrNull { it.code == code } ?: AppLanguage.EN
}

/**
 * App settings backed by [SharedPreferences].
 *
 * Persists the user's chosen [AppLanguage] and whether the Practise walkthrough has been seen.
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: AppLanguage
        get() {
            val saved = prefs.getString(KEY_LANGUAGE, null)
            return if (saved != null) {
                AppLanguage.entries.firstOrNull { it.code == saved }
                    ?: defaultLanguageFor(saved)
            } else {
                defaultLanguageFor(Locale.getDefault()?.language ?: "")
            }
        }
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value.code).apply()
        }

    var walkthroughSeen: Boolean
        get() = prefs.getBoolean(KEY_WALKTHROUGH_SEEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_WALKTHROUGH_SEEN, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "cpr_coach_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_WALKTHROUGH_SEEN = "walkthrough_seen"
    }
}
