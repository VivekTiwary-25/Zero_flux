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

    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(PUNCTUATION_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun matchesAny(cleanedTranscript: String, phrases: List<String>): Boolean {
        for (phrase in phrases) {
            val norm = normalize(phrase)
            if (norm.isEmpty()) continue
            val regex = Regex("""(?U)\b${Regex.escape(norm)}\b""")
            if (regex.containsMatchIn(cleanedTranscript)) {
                return true
            }
        }
        return false
    }

    companion object {
        private val PUNCTUATION_REGEX = Regex("""(?U)[\p{P}\p{S}\u0964\u0965]""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
