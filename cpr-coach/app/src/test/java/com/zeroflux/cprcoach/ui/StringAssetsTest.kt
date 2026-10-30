package com.zeroflux.cprcoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StringAssetsTest {

    @Test
    fun `all ten string files have the english keys apart from the english only ones`() {
        assertEquals(
            "AppLanguage must be the ten languages from the spec, in picker order",
            listOf("en", "hi", "kn", "ta", "te", "ml", "mr", "gu", "bn", "or"),
            AppLanguage.entries.map { it.code },
        )

        val english = AssetJson.readObject(File(TestAssets.assetsDir, "strings/en.json"))
        val englishKeys = english.keys
        val englishOnly = setOf("mic_enable", "camera_numeric_value", "live_no_rate")

        for (language in AppLanguage.entries) {
            val file = File(TestAssets.assetsDir, "strings/${language.code}.json")
            assertTrue("Missing strings file for ${language.code}: $file", file.isFile)

            val keys = AssetJson.readObject(file).keys
            val missing = englishKeys - keys
            assertEquals(
                "${language.code}.json is missing keys that en.json has: ${missing - englishOnly}",
                emptySet<String>(),
                missing - englishOnly,
            )
            val extra = keys - englishKeys
            assertEquals(
                "${language.code}.json has keys that en.json does not: $extra",
                emptySet<String>(),
                extra,
            )
        }
    }

    @Test
    fun `every clip folder has a wav for every english phrase key`() {
        val phraseFile = File(TestAssets.projectDir, "tools/clipgen/phrases_en.json")
        val phraseKeys = AssetJson.readObject(phraseFile).keys
        assertTrue("phrases_en.json has no keys", phraseKeys.isNotEmpty())

        for (language in AppLanguage.entries) {
            val folder = File(TestAssets.assetsDir, "clips/${language.code}")
            assertTrue("Missing clip folder for ${language.code}: $folder", folder.isDirectory)

            for (key in phraseKeys) {
                val clip = File(folder, "$key.wav")
                assertTrue("Missing clip for ${language.code}/$key: $clip", clip.isFile)
                assertTrue("Empty clip for ${language.code}/$key: $clip", clip.length() > 0L)
            }
        }
    }
}

/**
 * Finds the Android project from the Gradle test working directory and reads the JSON assets with
 * no third-party dependency (the JVM test classpath has no org.json).
 */
internal object TestAssets {
    val projectDir: File by lazy { findProjectDir() }
    val assetsDir: File by lazy { File(projectDir, "app/src/main/assets") }

    private fun findProjectDir(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (isProjectDir(dir)) return dir
            val nested = File(dir, "cpr-coach")
            if (isProjectDir(nested)) return nested
            dir = dir.parentFile
        }
        throw AssertionError("Cannot find cpr-coach from ${File("").absoluteFile}")
    }

    private fun isProjectDir(dir: File): Boolean =
        File(dir, "app/src/main/assets/strings/en.json").isFile &&
            File(dir, "tools/clipgen/phrases_en.json").isFile
}

internal object AssetJson {

    fun readObject(file: File): Map<String, Any?> =
        asObject(parse(file.readText(Charsets.UTF_8)), file.path)

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) {
            throw AssertionError("Unexpected JSON after the root value at offset ${reader.offset}")
        }
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun asObject(value: Any?, path: String): Map<String, Any?> =
        value as? Map<String, Any?>
            ?: throw AssertionError("$path must be a JSON object, was $value")

    @Suppress("UNCHECKED_CAST")
    fun asArray(value: Any?, path: String): List<Any?> =
        value as? List<Any?>
            ?: throw AssertionError("$path must be a JSON array, was $value")

    fun asString(value: Any?, path: String): String =
        value as? String ?: throw AssertionError("$path must be a JSON string, was $value")

    fun objectAt(node: Map<String, Any?>, key: String, path: String): Map<String, Any?> {
        if (!node.containsKey(key)) throw AssertionError("$path.$key is missing")
        return asObject(node[key], "$path.$key")
    }

    fun arrayAt(node: Map<String, Any?>, key: String, path: String): List<Any?> {
        if (!node.containsKey(key)) throw AssertionError("$path.$key is missing")
        return asArray(node[key], "$path.$key")
    }

    private class Reader(private val text: String) {
        var offset = 0
            private set

        fun atEnd(): Boolean = offset >= text.length

        fun skipWhitespace() {
            while (!atEnd() && text[offset].isWhitespace()) offset++
        }

        fun readValue(): Any? {
            skipWhitespace()
            return when (val c = current()) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else fail("Unexpected '$c'")
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (current() == '}') {
                offset++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                result[key] = readValue()
                skipWhitespace()
                when (current()) {
                    ',' -> offset++
                    '}' -> {
                        offset++
                        return result
                    }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (current() == ']') {
                offset++
                return result
            }
            while (true) {
                result.add(readValue())
                skipWhitespace()
                when (current()) {
                    ',' -> offset++
                    ']' -> {
                        offset++
                        return result
                    }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val result = StringBuilder()
            while (true) {
                if (atEnd()) fail("Unterminated string")
                when (val c = text[offset++]) {
                    '"' -> return result.toString()
                    '\\' -> result.append(readEscape())
                    else -> result.append(c)
                }
            }
        }

        private fun readEscape(): Char {
            if (atEnd()) fail("Unterminated escape")
            return when (val c = text[offset++]) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (offset + 4 > text.length) fail("Truncated unicode escape")
                    val hex = text.substring(offset, offset + 4)
                    offset += 4
                    hex.toIntOrNull(16)?.toChar() ?: fail("Bad unicode escape: $hex")
                }
                else -> fail("Bad escape: \\$c")
            }
        }

        private fun readNumber(): Double {
            val start = offset
            if (current() == '-') offset++
            while (!atEnd() && text[offset].isDigit()) offset++
            if (!atEnd() && text[offset] == '.') {
                offset++
                while (!atEnd() && text[offset].isDigit()) offset++
            }
            if (!atEnd() && (text[offset] == 'e' || text[offset] == 'E')) {
                offset++
                if (!atEnd() && (text[offset] == '+' || text[offset] == '-')) offset++
                while (!atEnd() && text[offset].isDigit()) offset++
            }
            return text.substring(start, offset).toDouble()
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (!text.startsWith(word, offset)) fail("Expected '$word'")
            offset += word.length
            return value
        }

        private fun expect(c: Char) {
            if (current() != c) fail("Expected '$c'")
            offset++
        }

        private fun current(): Char {
            if (atEnd()) fail("Unexpected end of JSON")
            return text[offset]
        }

        private fun fail(message: String): Nothing =
            throw AssertionError("$message at offset $offset in JSON")
    }
}
