package com.zeroflux.cprcoach.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandMatcherTest {

    private val enTable = CommandTable(
        commands = mapOf(
            VoiceCommand.START to listOf("start", "begin", "go", "resume"),
            VoiceCommand.STOP to listOf("stop", "pause", "halt", "quit", "end"),
            VoiceCommand.REPEAT to listOf("repeat", "again", "say again", "replay"),
            VoiceCommand.EMERGENCY to listOf("emergency", "help", "urgent", "rescue"),
            VoiceCommand.PRACTISE to listOf("practise", "practice", "training", "learn"),
            VoiceCommand.CALL to listOf("call", "dial", "phone", "ambulance"),
        ),
        languages = mapOf(
            "en" to listOf("english", "angrezi"),
            "kn" to listOf("kannada", "kanarese"),
            "hi" to listOf("hindi", "hindustani"),
            "ta" to listOf("tamil", "tamizh"),
        ),
    )

    private val knTable = CommandTable(
        commands = mapOf(
            VoiceCommand.START to listOf("ಸ್ಟಾರ್ಟ್", "ಶುರು", "ಶುರು ಮಾಡಿ", "ಆರಂಭಿಸಿ"),
            VoiceCommand.STOP to listOf("ಸ್ಟಾಪ್", "ನಿಲ್ಲಿಸಿ", "ನಿಲ್ಲು", "ಸಾಕು"),
            VoiceCommand.REPEAT to listOf("ರಿಪೀಟ್", "ಮತ್ತೆ ಹೇಳಿ", "ಮತ್ತೊಮ್ಮೆ", "ಪುನಃ ಹೇಳಿ"),
            VoiceCommand.EMERGENCY to listOf("ಎಮರ್ಜೆನ್ಸಿ", "ತುರ್ತು", "ಕಾಪಾಡಿ", "ಸಹಾಯ"),
            VoiceCommand.PRACTISE to listOf("ಪ್ರಾಕ್ಟೀಸ್", "ಪ್ರ್ಯಾಕ್ಟೀಸ್", "ಅಭ್ಯಾಸ", "ತರಬೇತಿ"),
            VoiceCommand.CALL to listOf("ಕಾಲ್", "ಕಾಲ್ ಮಾಡಿ", "ಫೋನ್ ಮಾಡಿ", "ಕರೆ ಮಾಡಿ"),
        ),
        languages = mapOf(
            "kn" to listOf("ಕನ್ನಡ", "ಕನ್ನಡ ಭಾಷೆ"),
            "en" to listOf("ಇಂಗ್ಲಿಷ್", "ಇಂಗ್ಲೀಷ್", "ಆಂಗ್ಲ"),
            "hi" to listOf("ಹಿಂದಿ", "ಹಿಂದಿ ಭಾಷೆ"),
        ),
    )

    private val matcher = CommandMatcher(
        table = mapOf(
            "en" to enTable,
            "kn" to knTable,
        ),
    )

    private val allCommands = VoiceCommand.entries.toSet()

    @Test
    fun `whole-word rule`() {
        // "restart" contains "start" as substring, but should not match
        assertEquals(MatchResult.None, matcher.match("restart", "en", allCommands, false))
        assertEquals(MatchResult.None, matcher.match("restarting now", "en", allCommands, false))
        assertEquals(MatchResult.None, matcher.match("starting", "en", allCommands, false))

        // Exact whole-word match
        assertEquals(MatchResult.Command(VoiceCommand.START), matcher.match("start", "en", allCommands, false))
        assertEquals(MatchResult.Command(VoiceCommand.START), matcher.match("please start", "en", allCommands, false))
        assertEquals(MatchResult.Command(VoiceCommand.START), matcher.match("start now", "en", allCommands, false))
        assertEquals(MatchResult.Command(VoiceCommand.START), matcher.match("please start now", "en", allCommands, false))

        // Whole phrase match: "say again" vs "say against"
        assertEquals(MatchResult.None, matcher.match("say against", "en", allCommands, false))
        assertEquals(MatchResult.Command(VoiceCommand.REPEAT), matcher.match("say again", "en", allCommands, false))
        assertEquals(MatchResult.Command(VoiceCommand.REPEAT), matcher.match("please say again", "en", allCommands, false))
    }

    @Test
    fun `allowed filter`() {
        val onlyStart = setOf(VoiceCommand.START)
        val startAndStop = setOf(VoiceCommand.START, VoiceCommand.STOP)

        // "stop" spoken when only START is allowed
        assertEquals(MatchResult.None, matcher.match("stop", "en", onlyStart, false))

        // "stop" spoken when STOP is allowed
        assertEquals(MatchResult.Command(VoiceCommand.STOP), matcher.match("stop", "en", startAndStop, false))

        // Allowed set is empty
        assertEquals(MatchResult.None, matcher.match("emergency", "en", emptySet(), false))
    }

    @Test
    fun `ambiguity gives None`() {
        // Two different commands in transcript
        assertEquals(MatchResult.None, matcher.match("start stop", "en", allCommands, false))
        assertEquals(MatchResult.None, matcher.match("stop and begin", "en", allCommands, false))

        // Command and language in transcript
        assertEquals(MatchResult.None, matcher.match("start kannada", "en", allCommands, true))

        // Two different languages in transcript
        assertEquals(MatchResult.None, matcher.match("english kannada", "en", allCommands, true))

        // Repeated same command is not ambiguous
        assertEquals(MatchResult.Command(VoiceCommand.START), matcher.match("start start", "en", allCommands, false))
        assertEquals(MatchResult.Command(VoiceCommand.START), matcher.match("start begin", "en", allCommands, false))
    }

    @Test
    fun `Kannada-script input`() {
        // Multi-word Kannada command
        assertEquals(MatchResult.Command(VoiceCommand.START), matcher.match("ಶುರು ಮಾಡಿ", "kn", allCommands, false))

        // Kannada command with danda punctuation
        assertEquals(MatchResult.Command(VoiceCommand.START), matcher.match("ಶುರು ಮಾಡಿ।", "kn", allCommands, false))

        // Single-word Kannada command
        assertEquals(MatchResult.Command(VoiceCommand.STOP), matcher.match("ನಿಲ್ಲಿಸಿ", "kn", allCommands, false))

        // Kannada language command
        assertEquals(MatchResult.Language("kn"), matcher.match("ಕನ್ನಡ", "kn", allCommands, true))

        // Kannada emergency command
        assertEquals(MatchResult.Command(VoiceCommand.EMERGENCY), matcher.match("ಎಮರ್ಜೆನ್ಸಿ", "kn", allCommands, false))
    }

    @Test
    fun `language match`() {
        // Allowed languages
        assertEquals(MatchResult.Language("kn"), matcher.match("kannada", "en", allCommands, true))
        assertEquals(MatchResult.Language("en"), matcher.match("english", "en", allCommands, true))
        assertEquals(MatchResult.Language("en"), matcher.match("angrezi", "en", allCommands, true))
        assertEquals(MatchResult.Language("hi"), matcher.match("switch to hindi", "en", allCommands, true))

        // Disallowed languages
        assertEquals(MatchResult.None, matcher.match("kannada", "en", allCommands, false))
        assertEquals(MatchResult.None, matcher.match("english", "en", allCommands, false))
    }

    @Test
    fun `unknown language code gives None`() {
        assertEquals(MatchResult.None, matcher.match("start", "xyz", allCommands, true))
        assertEquals(MatchResult.None, matcher.match("kannada", "unknown", allCommands, true))
        assertEquals(MatchResult.None, matcher.match("start", "", allCommands, true))
    }

    @Test
    fun `blank transcript and punctuation handling`() {
        assertEquals(MatchResult.None, matcher.match("", "en", allCommands, true))
        assertEquals(MatchResult.None, matcher.match("   ", "en", allCommands, true))
        assertEquals(MatchResult.None, matcher.match("...", "en", allCommands, true))

        // Transcript with exclamation and comma
        assertEquals(MatchResult.Command(VoiceCommand.EMERGENCY), matcher.match("emergency!!!", "en", allCommands, false))
        assertEquals(MatchResult.Command(VoiceCommand.START), matcher.match("please, start!", "en", allCommands, false))
    }
}
