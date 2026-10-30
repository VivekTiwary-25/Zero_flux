package com.zeroflux.cprcoach.voice

import com.zeroflux.cprcoach.ui.AssetJson
import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ScreenCommandsTest {

    private val tables: Map<String, CommandTable> by lazy {
        val root = AssetJson.readObject(File(TestAssets.assetsDir, "commands.json"))
        listOf("en", "hi").associateWith { code ->
            val language = AssetJson.objectAt(root, code, "commands.json")
            val commands = COMMAND_KEYS.mapValues { (_, key) ->
                AssetJson.arrayAt(language, key, "commands.json.$code")
                    .mapIndexed { index, value ->
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
            CommandTable(commands = commands, languages = languages)
        }
    }

    private val matcher by lazy { CommandMatcher(tables) }

    // VoiceRouter receives these policies through bind(); it exposes no screen policy constants.
    // Repeat the expectations from AppRoot, PractiseHost and EmergencyHost without changing them.
    @Test
    fun `home accepts only emergency practise and language names`() {
        assertScreen(
            "Home",
            setOf(VoiceCommand.EMERGENCY, VoiceCommand.PRACTISE),
            allowLanguages = true,
        )
    }

    @Test
    fun `practise accepts only start stop and repeat`() {
        assertScreen(
            "Practise",
            setOf(VoiceCommand.START, VoiceCommand.STOP, VoiceCommand.REPEAT),
        )
    }

    @Test
    fun `emergency accepts only call and stop`() {
        assertScreen("Emergency", setOf(VoiceCommand.CALL, VoiceCommand.STOP))
    }

    @Test
    fun `scorecard ignores commands and language names`() {
        assertScreen("Scorecard", emptySet())
    }

    @Test
    fun `walkthrough ignores commands and language names`() {
        assertScreen("Walkthrough", emptySet())
    }

    private fun assertScreen(
        screen: String,
        allowed: Set<VoiceCommand>,
        allowLanguages: Boolean = false,
    ) {
        for ((code, table) in tables) {
            for ((command, phrases) in table.commands) {
                val expected = if (command in allowed) {
                    MatchResult.Command(command)
                } else {
                    MatchResult.None
                }
                for (phrase in phrases) {
                    // Includes the literal English "call": Home must ignore it and every call alias.
                    assertEquals(
                        "$screen: '$phrase' in $code ($command)",
                        expected,
                        matcher.match(phrase, code, allowed, allowLanguages),
                    )
                }
            }
            for ((target, phrases) in table.languages) {
                val expected = if (allowLanguages) {
                    MatchResult.Language(target)
                } else {
                    MatchResult.None
                }
                for (phrase in phrases) {
                    assertEquals(
                        "$screen: '$phrase' in $code (language $target)",
                        expected,
                        matcher.match(phrase, code, allowed, allowLanguages),
                    )
                }
            }
        }
    }

    private companion object {
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
