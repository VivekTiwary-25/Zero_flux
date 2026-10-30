package com.zeroflux.cprcoach.ui

import android.content.Context
import com.zeroflux.cprcoach.voice.CommandTable
import com.zeroflux.cprcoach.voice.VoiceCommand
import org.json.JSONObject

class AssetStrings private constructor(
    private val strings: Map<String, String>,
    private val fallback: Map<String, String> = emptyMap(),
) : Strings {

    override fun get(key: String): String {
        return strings[key] ?: fallback[key] ?: key
    }

    companion object {
        fun load(context: Context, code: String): AssetStrings {
            val enMap = loadStringsMap(context, "en")
            val targetCode = code.trim().lowercase()
            val targetMap = if (targetCode == "en") {
                enMap
            } else {
                loadStringsMap(context, targetCode)
            }
            return AssetStrings(strings = targetMap, fallback = enMap)
        }

        fun loadCommandTable(context: Context): Map<String, CommandTable> {
            return com.zeroflux.cprcoach.ui.loadCommandTable(context)
        }
    }
}

private fun loadStringsMap(context: Context, code: String): Map<String, String> {
    return try {
        val path = "strings/$code.json"
        val text = context.assets.open(path).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        val map = HashMap<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!json.isNull(key)) {
                map[key] = json.optString(key, key)
            }
        }
        map
    } catch (_: Exception) {
        emptyMap()
    }
}

fun loadCommandTable(context: Context): Map<String, CommandTable> {
    val text = try {
        context.assets.open("commands.json").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        return emptyMap()
    }
    return parseCommandTable(text)
}

fun parseCommandTable(json: String): Map<String, CommandTable> {
    val root = try {
        JSONObject(json)
    } catch (_: Exception) {
        return emptyMap()
    }
    val result = mutableMapOf<String, CommandTable>()
    val langKeys = root.keys()
    while (langKeys.hasNext()) {
        val langCode = langKeys.next()
        val langObj = root.optJSONObject(langCode) ?: continue

        val commands = mutableMapOf<VoiceCommand, List<String>>()
        for (cmd in VoiceCommand.entries) {
            val key = cmd.name.lowercase()
            val array = langObj.optJSONArray(key)
                ?: if (key == "practise") langObj.optJSONArray("practice") else null
            val phrases = mutableListOf<String>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val phrase = array.optString(i, "").trim()
                    if (phrase.isNotEmpty()) {
                        phrases.add(phrase)
                    }
                }
            }
            commands[cmd] = phrases
        }

        val languages = mutableMapOf<String, List<String>>()
        val languagesObj = langObj.optJSONObject("languages")
        if (languagesObj != null) {
            val targetKeys = languagesObj.keys()
            while (targetKeys.hasNext()) {
                val targetCode = targetKeys.next()
                val array = languagesObj.optJSONArray(targetCode)
                val phrases = mutableListOf<String>()
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val phrase = array.optString(i, "").trim()
                        if (phrase.isNotEmpty()) {
                            phrases.add(phrase)
                        }
                    }
                }
                languages[targetCode] = phrases
            }
        }
        result[langCode] = CommandTable(commands = commands, languages = languages)
    }
    return result
}
