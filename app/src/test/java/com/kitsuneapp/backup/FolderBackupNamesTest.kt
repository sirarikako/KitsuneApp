package com.kitsuneapp.backup

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderBackupNamesTest {
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
    fun comparesByActualTimeAndRejectsNonBackupNames() {
        val older = checkNotNull(AutoBackupName.parse("kitsune-auto-20261118T003015+0800-0001.json"))
        val newer = checkNotNull(AutoBackupName.parse("kitsune-auto-20261117T170000Z-0001.json"))
        assertTrue(newer > older)
        assertNull(AutoBackupName.parse("kitsune-auto-20261118T003015+0900-0001.json"))
        assertNull(AutoBackupName.parse("kitsune-auto-20261131T003015+0800-0001.json"))
        assertNull(AutoBackupName.parse("kitsune-auto-20261118T003015+0800-0001.json.ok"))
    }
}
