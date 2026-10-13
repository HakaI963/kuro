package com.kuro.app.data.local

import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloneRecordMappingTest {

    @Test
    fun `record maps to ACTIVE profile`() {
        val rec = CloneRecord(
            packageName = "com.example.notes",
            cloneId = "c1",
            cloneName = "Notes",
            colorTag = 0xFF7C6CFF,
            createdAt = 1_000L,
            engineName = "blackbox-stage1",
            lastLaunched = null
        )
        val profile = rec.toProfile("Notes")
        assertEquals(CloneStatus.ACTIVE, profile.status)
        assertEquals("c1", profile.id)
        assertEquals("Notes", profile.name)
        assertNull(profile.lastLaunchedMillis)
    }

    @Test
    fun `record preserves launch timestamp`() {
        val rec = CloneRecord(
            packageName = "com.example.browser",
            cloneId = "c2",
            cloneName = "Browser",
            colorTag = 0xFF49D6C2,
            createdAt = 2_000L,
            engineName = "blackbox-stage1",
            lastLaunched = 3_000L
        )
        val profile = rec.toProfile("Browser")
        assertEquals(3_000L, profile.lastLaunchedMillis)
    }

    @Test
    fun `profile roundtrips through record`() {
        val rec = CloneRecord(
            packageName = "com.example.files",
            cloneId = "c3",
            cloneName = "Files",
            colorTag = 0xFF3DDC97,
            createdAt = 5_000L,
            engineName = "blackbox-stage1",
            lastLaunched = 6_000L
        )
        val roundtripped = rec.toProfile("Files").toRecord("blackbox-stage1")
        assertEquals(rec, roundtripped)
    }

    @Test
    fun `activity record roundtrips`() {
        val event = com.kuro.app.domain.models.ActivityEvent(
            id = "99",
            cloneId = "c1",
            cloneName = "Notes",
            type = ActivityType.LAUNCHED,
            timestampMillis = 7_000L,
            detail = "Launched inside Kuro space"
        )
        val rec = event.toRecord()
        assertEquals("LAUNCHED", rec.type)
        val back = rec.copy(uid = 99).toEvent()
        assertEquals(ActivityType.LAUNCHED, back.type)
        assertEquals("c1", back.cloneId)
    }
}
