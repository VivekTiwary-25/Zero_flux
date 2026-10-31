package com.zeroflux.cprcoach.ui

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

private const val KEY_CONTACT_NAME = "emergency_contact_name"
private const val KEY_CONTACT_NUMBER = "emergency_contact_number"
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

data class EmergencyContact(val name: String, val phoneNumber: String)

/** App settings backed by [SharedPreferences]. */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val contactStore = EmergencyContactStore(
        read = { key -> prefs.getString(key, null) },
        write = { name, number ->
            prefs.edit().apply {
                if (name == null || number == null) {
                    remove(KEY_CONTACT_NAME)
                    remove(KEY_CONTACT_NUMBER)
                } else {
                    putString(KEY_CONTACT_NAME, name)
                    putString(KEY_CONTACT_NUMBER, number)
                }
            }.apply()
        },
    )

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

    var emergencyContact: EmergencyContact?
        get() = contactStore.load()
        set(value) = contactStore.save(value)

    companion object {
        private const val PREFS_NAME = "cpr_coach_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_WALKTHROUGH_SEEN = "walkthrough_seen"
    }
}

internal class EmergencyContactStore(
    private val read: (String) -> String?,
    private val write: (String?, String?) -> Unit,
) {
    fun load(): EmergencyContact? {
        val name = read(KEY_CONTACT_NAME)?.takeIf(String::isNotBlank) ?: return null
        val number = read(KEY_CONTACT_NUMBER)?.takeIf(String::isNotBlank) ?: return null
        return EmergencyContact(name, number)
    }

    fun save(contact: EmergencyContact?) {
        write(contact?.name?.takeIf(String::isNotBlank), contact?.phoneNumber?.takeIf(String::isNotBlank))
    }
}
