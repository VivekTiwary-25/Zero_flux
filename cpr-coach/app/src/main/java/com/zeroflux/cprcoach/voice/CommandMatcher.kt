package com.zeroflux.cprcoach.voice

enum class VoiceCommand {
    EMERGENCY,
    PRACTISE,
    START,
    STOP,
    REPEAT,
    CALL,
}

sealed interface MatchResult {
    data class Command(val command: VoiceCommand) : MatchResult
    data class Language(val code: String) : MatchResult
    data object None : MatchResult
}

data class CommandTable(
    val commands: Map<VoiceCommand, List<String>>,
    val languages: Map<String, List<String>>,
)

class CommandMatcher(val table: Map<String, CommandTable>) {

    fun match(
        transcript: String,
        languageCode: String,
        allowed: Set<VoiceCommand>,
        allowLanguages: Boolean,
    ): MatchResult {
        val commandTable = table[languageCode]
            ?: table[languageCode.trim().lowercase()]
            ?: return MatchResult.None

        val cleaned = normalize(transcript)
        if (cleaned.isEmpty()) {
            return MatchResult.None
        }

        val matchedCommands = mutableSetOf<VoiceCommand>()
        for ((command, phrases) in commandTable.commands) {
            if (command !in allowed) continue
            if (matchesAny(cleaned, phrases)) {
                matchedCommands.add(command)
            }
        }

        val matchedLanguages = mutableSetOf<String>()
        if (allowLanguages) {
            for ((code, phrases) in commandTable.languages) {
                if (matchesAny(cleaned, phrases)) {
                    matchedLanguages.add(code)
                }
            }
        }

        if (matchedCommands.size > 1 || matchedLanguages.size > 1) {
            return MatchResult.None
        }
        if (matchedCommands.isNotEmpty() && matchedLanguages.isNotEmpty()) {
            return MatchResult.None
        }
        if (matchedCommands.size == 1) {
            return MatchResult.Command(matchedCommands.first())
        }
        if (matchedLanguages.size == 1) {
            return MatchResult.Language(matchedLanguages.first())
        }
        return MatchResult.None
    }

    /**
     * Keeps letters, digits and combining marks (needed by Indian scripts), and turns all else into single
     * spaces. Plain character checks, because Android's regex engine differs from the JVM's that tests run on.
     */
    private fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        var pendingSpace = false
        for (c in text.lowercase()) {
            if (c.isLetterOrDigit() || Character.getType(c).toByte() in COMBINING_MARKS) {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(c)
            } else {
                pendingSpace = true
            }
        }
        return out.toString()
    }

    private fun matchesAny(cleanedTranscript: String, phrases: List<String>): Boolean {
        val padded = " $cleanedTranscript "
        return phrases.any { phrase ->
            val norm = normalize(phrase)
            norm.isNotEmpty() && padded.contains(" $norm ")
        }
    }

    private companion object {
        val COMBINING_MARKS = setOf(
            Character.NON_SPACING_MARK,
            Character.COMBINING_SPACING_MARK,
            Character.ENCLOSING_MARK,
        )
    }
}
