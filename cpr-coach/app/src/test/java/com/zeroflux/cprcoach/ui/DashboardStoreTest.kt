package com.zeroflux.cprcoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DashboardStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing store returns empty without creating a backup`() {
        val file = storeFile()

        assertEquals(HistoryFile(), DashboardStore.readStore(file))
        assertFalse(file.exists())
        assertFalse(backupFile(file).exists())
    }

    @Test
    fun `valid history is read without being moved to a backup`() {
        val file = storeFile()
        val history = HistoryFile(
            samplesCleared = true,
            sessions = listOf(PractiseRecord("saved", durationSeconds = 30)),
        )
        val text = DashboardLogic.encodeStore(history)
        file.writeText(text)

        assertEquals(history, DashboardStore.readStore(file))
        assertEquals(text, file.readText())
        assertFalse(backupFile(file).exists())
    }

    @Test
    fun `corrupt history is preserved before an empty store can be saved`() {
        val file = storeFile()
        val corrupt = "{\"sessions\":["
        file.writeText(corrupt)

        assertEquals(HistoryFile(), DashboardStore.readStore(file))
        assertFalse(file.exists())
        assertEquals(corrupt, backupFile(file).readText())

        val replacement = HistoryFile(samplesCleared = true)
        DashboardStore.writeStore(file, DashboardLogic.encodeStore(replacement))

        assertEquals(replacement, DashboardStore.readStore(file))
        assertEquals(corrupt, backupFile(file).readText())
    }

    @Test
    fun `failed backup rename reports failure and leaves corrupt history untouched`() {
        val file = storeFile()
        val corrupt = "{"
        file.writeText(corrupt)
        val backup = backupFile(file)
        assertTrue(backup.mkdir())
        val marker = File(backup, "keep")
        marker.writeText("existing backup")

        val failure = runCatching { DashboardStore.readStore(file) }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(corrupt, file.readText())
        assertEquals("existing backup", marker.readText())
    }

    @Test
    fun `atomic write replaces existing history and removes temporary file`() {
        val file = storeFile()
        val previous = HistoryFile(sessions = listOf(PractiseRecord("old", durationSeconds = 10)))
        file.writeText(DashboardLogic.encodeStore(previous))
        val replacement = HistoryFile(
            samplesCleared = true,
            sessions = listOf(PractiseRecord("new", durationSeconds = 20)),
        )

        DashboardStore.writeStore(file, DashboardLogic.encodeStore(replacement))

        assertEquals(replacement, DashboardStore.readStore(file))
        assertFalse(File(file.parentFile, "${file.name}.tmp").exists())
        assertFalse(backupFile(file).exists())
    }

    @Test
    fun `failed atomic rename leaves its destination intact and reports failure`() {
        val file = storeFile()
        assertTrue(file.mkdir())
        val marker = File(file, "keep")
        marker.writeText("original destination")

        val failure = runCatching {
            DashboardStore.writeStore(file, DashboardLogic.encodeStore(HistoryFile()))
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals("original destination", marker.readText())
        assertFalse(File(file.parentFile, "${file.name}.tmp").exists())
    }

    private fun storeFile(): File = File(temporaryFolder.root, "dashboard_sessions.json")

    private fun backupFile(file: File): File = File(file.parentFile, "${file.name}.bak")
}
