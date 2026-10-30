package com.zeroflux.cprcoach.voice

import com.zeroflux.cprcoach.ui.AppLanguage
import com.zeroflux.cprcoach.ui.AssetJson
import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommandAssetsTest {

    private val root: Map<String, Any?> by lazy {
        AssetJson.readObject(File(TestAssets.assetsDir, "commands.json"))
    }

    private val tables: Map<String, CommandTable> by lazy {
        COMMAND_TABLE_CODES.associateWith { tableFor(it) }
    }

    private val matcher = CommandMatcher(tables)

    private val allCommands = VoiceCommand.entries.toSet()

    @Test
    fun `commands json has all ten languages and every command key`() {
        val codes = AppLanguage.entries.map { it.code }
        assertEquals(
            "AppLanguage must be the ten languages from the spec",
            listOf("en", "hi", "kn", "ta", "te", "ml", "mr", "gu", "bn", "or"),
            codes,
        )
        assertEquals(
            "commands.json language codes do not match AppLanguage",
            codes.toSet(),
            root.keys,
        )

        for (code in codes) {
            val language = AssetJson.objectAt(root, code, "commands.json")

            for ((command, key) in COMMAND_KEYS) {
                assertTrue(
                    "commands.json.$code is missing the '$key' key for $command",
                    language.containsKey(key),
                )
                val phrases = AssetJson.arrayAt(language, key, "commands.json.$code")
                assertTrue("commands.json.$code.$key is empty", phrases.isNotEmpty())
                phrases.forEachIndexed { index, phrase ->
                    val text = AssetJson.asString(phrase, "commands.json.$code.$key[$index]")
                    assertTrue(
                        "commands.json.$code.$key[$index] is blank",
                        text.isNotBlank(),
                    )
                }
            }

            val languages = AssetJson.objectAt(language, "languages", "commands.json.$code")
            assertEquals(
                "commands.json.$code.languages must name all ten languages",
                codes.toSet(),
                languages.keys,
            )
            for (target in codes) {
                val phrases = AssetJson.arrayAt(
                    languages,
                    target,
                    "commands.json.$code.languages",
                )
                assertTrue(
                    "commands.json.$code.languages.$target is empty",
                    phrases.isNotEmpty(),
                )
                phrases.forEachIndexed { index, phrase ->
                    val text = AssetJson.asString(
                        phrase,
                        "commands.json.$code.languages.$target[$index]",
                    )
                    assertTrue(
                        "commands.json.$code.languages.$target[$index] is blank",
                        text.isNotBlank(),
                    )
                }
            }
        }
    }

    @Test
    fun `each keyword matches its own command in english and hindi`() {
        for (code in COMMAND_TABLE_CODES) {
            val table = tables.getValue(code)
            for ((command, phrases) in table.commands) {
                for (phrase in phrases) {
                    assertEquals(
                        "'$phrase' in $code must match only $command",
                        MatchResult.Command(command),
                        matcher.match(phrase, code, allCommands, false),
                    )
                }
            }
        }
    }

    @Test
    fun `no keyword matches two commands in english and hindi`() {
        for (code in COMMAND_TABLE_CODES) {
            val table = tables.getValue(code)
            for ((command, phrases) in table.commands) {
                for (phrase in phrases) {
                    val hits = table.commands.keys.filter { candidate ->
                        matcher.match(phrase, code, setOf(candidate), false) ==
                            MatchResult.Command(candidate)
                    }
                    assertEquals(
                        "'$phrase' in $code matches more than one command: $hits",
                        listOf(command),
                        hits,
                    )
                }
            }
        }
    }

    @Test
    fun `everyday sentences behave as expected`() {
        assertEquals(
            MatchResult.Command(VoiceCommand.START),
            matcher.match("please start now", "en", allCommands, false),
        )
        // Matching is literal keyword matching: punctuation is stripped, but not negation,
        // so "don't stop" still contains the stop keyword.
        assertEquals(
            MatchResult.Command(VoiceCommand.STOP),
            matcher.match("don't stop", "en", allCommands, false),
        )
        assertEquals(
            MatchResult.Command(VoiceCommand.STOP),
            matcher.match("please don't stop", "en", allCommands, false),
        )
        assertEquals(
            MatchResult.Command(VoiceCommand.CALL),
            matcher.match("call an ambulance", "en", allCommands, false),
        )
        assertEquals(
            MatchResult.Command(VoiceCommand.REPEAT),
            matcher.match("can you say again", "en", allCommands, false),
        )

        assertEquals(
            MatchResult.Command(VoiceCommand.START),
            matcher.match("अब शुरू करो", "hi", allCommands, false),
        )
        assertEquals(
            MatchResult.Command(VoiceCommand.STOP),
            matcher.match("रुको मत", "hi", allCommands, false),
        )
        assertEquals(
            MatchResult.Command(VoiceCommand.PRACTISE),
            matcher.match("कृपया अभ्यास करें", "hi", allCommands, false),
        )
        assertEquals(
            MatchResult.Command(VoiceCommand.EMERGENCY),
            matcher.match("मदद करो", "hi", allCommands, false),
        )
        assertEquals(
            MatchResult.Command(VoiceCommand.CALL),
            matcher.match("फोन लगाओ", "hi", allCommands, false),
        )
        assertEquals(
            MatchResult.Command(VoiceCommand.REPEAT),
            matcher.match("फिर से बोलो", "hi", allCommands, false),
        )
    }

    private fun tableFor(code: String): CommandTable {
        val language = AssetJson.objectAt(root, code, "commands.json")
        val commands = COMMAND_KEYS.mapValues { (_, key) ->
            AssetJson.arrayAt(language, key, "commands.json.$code").mapIndexed { index, value ->
                AssetJson.asString(value, "commands.json.$code.$key[$index]")
            }
        }
        val languageNodes = AssetJson.objectAt(language, "languages", "commands.json.$code")
        val languages = languageNodes.mapValues { (target, value) ->
            AssetJson.asArray(value, "commands.json.$code.languages.$target")
                .mapIndexed { index, phrase ->
                    AssetJson.asString(
                        phrase,
                        "commands.json.$code.languages.$target[$index]",
                    )
                }
        }
        return CommandTable(commands = commands, languages = languages)
    }

    private companion object {
        val COMMAND_TABLE_CODES = listOf("en", "hi")

        val COMMAND_KEYS: Map<VoiceCommand, String> = linkedMapOf(
            VoiceCommand.EMERGENCY to "emergency",
            VoiceCommand.PRACTISE to "practise",
            VoiceCommand.START to "start",
            VoiceCommand.STOP to "stop",
            VoiceCommand.REPEAT to "repeat",
            VoiceCommand.CALL to "call",
        )
    }
}
