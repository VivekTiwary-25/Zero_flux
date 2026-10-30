package com.zeroflux.cprcoach

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidHazardsTest {
    @Test
    fun `main source avoids JVM-only regex and packages`() {
        val projectDir = findProjectDir()
        val sourceDir = File(projectDir, "app/src/main/java")
        val forbidden = listOf(
            Regex("\\(\\?[Ud]"),
            Regex("Pattern\\.UNICODE_CHARACTER_CLASS"),
            Regex("\\\\p\\{Is[A-Za-z][A-Za-z0-9_]*}"),
            Regex("java\\.lang\\.management|java\\.awt|javax\\.swing"),
        )
        val violations = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .flatMap { file ->
                file.readLines().flatMapIndexed { index, line ->
                    if (forbidden.any { it.containsMatchIn(line) }) {
                        listOf("${file.relativeTo(projectDir).path}:${index + 1}: $line")
                    } else {
                        emptyList()
                    }
                }
            }
            .toList()

        assertTrue(
            "Android-incompatible JVM constructs found:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun findProjectDir(): File {
        var dir: File? = File("").canonicalFile
        while (dir != null) {
            if (File(dir, "app/src/main/java").isDirectory) return dir
            dir = dir.parentFile
        }
        throw AssertionError("Could not find app/src/main/java from ${File("").canonicalPath}")
    }
}
