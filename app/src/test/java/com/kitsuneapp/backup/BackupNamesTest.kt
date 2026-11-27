package com.kitsuneapp.backup

import com.kitsuneapp.data.BackupKind
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNamesTest {
    @Test
    fun suggestsPlusEightNamesForSingleFileExports() {
        val time = Instant.parse("2026-11-17T16:30:15Z")
        assertEquals("kitsune-manual-20261118T003015+0800.json", suggestedBackupFilename(BackupKind.MANUAL, time))
        assertEquals("kitsune-pre-restore-20261118T003015+0800.json", suggestedBackupFilename(BackupKind.PRE_RESTORE, time))
    }

    @Test
    fun writesOffsetAndContinuesTheSequenceAcrossBothFilenameFormats() {
        val time = Instant.parse("2026-11-17T16:30:15Z")
        val names = sequenceOf(
            "kitsune-auto-20261117T163015Z-9999.json",
            "kitsune-auto-20261118T003015+0800-10000.json",
            "kitsune-auto-20261118T003015Z-99999.json",
        )
        val next = AutoBackupName(time, nextAutoBackupSequence(names, time))
        assertEquals("kitsune-auto-20261118T003015+0800-10001.json", next.filename())
        assertEquals(next, AutoBackupName.parse(next.filename()))
        assertTrue(next > checkNotNull(AutoBackupName.parse("kitsune-auto-20261117T163015Z-9999.json")))
    }

    @Test
    fun reservesSequenceOfAnOrphanedCompletionMarker() {
        val time = Instant.parse("2026-11-17T16:30:15Z")
        val names = sequenceOf(
            "kitsune-auto-20261118T003015+0800-0002.json",
            "kitsune-auto-20261118T003015+0800-0005.json.ok",
        )
        assertEquals(6L, nextAutoBackupSequence(names, time))
    }

    @Test
    fun comparesByActualTimeAndRejectsNonBackupNames() {
        val older = checkNotNull(AutoBackupName.parse("kitsune-auto-20261118T003015+0800-0001.json"))
        val newer = checkNotNull(AutoBackupName.parse("kitsune-auto-20261117T170000Z-0001.json"))
        assertTrue(newer > older)
        assertNull(AutoBackupName.parse("kitsune-auto-20261118T003015+0900-0001.json"))
        assertNull(AutoBackupName.parse("kitsune-auto-20261131T003015+0800-0001.json"))
        assertNull(AutoBackupName.parse("kitsune-auto-20261118T003015+0800-0001.json.ok"))
    }

    @Test
    fun formatsReadableTimeFileSizeAndTreePath() {
        val time = Instant.parse("2026-11-17T16:30:15Z")
        assertEquals("2026-11-18 00:30:15 (+08:00)", backupReadableTime(time))
        assertEquals("0 B", formatBackupSize(-5L))
        assertEquals("512 B", formatBackupSize(512L))
        assertEquals("1.5 KB", formatBackupSize(1536L))
        assertEquals("2.50 MB", formatBackupSize(2L * 1024 * 1024 + 512 * 1024))
        assertEquals("Documents/KitsuneBackup", readableTreePath("primary:Documents/KitsuneBackup"))
        assertEquals("内部存储根目录", readableTreePath("primary:"))
        assertEquals("SDCARD/Backups", readableTreePath("SDCARD:Backups"))
        assertEquals("root", readableTreePath("root"))
    }
}
