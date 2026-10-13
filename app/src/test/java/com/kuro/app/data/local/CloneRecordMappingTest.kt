package com.kuro.app.data.local

import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloneRecordMappingTest {

    @Test
    fun `engine-confirmed record maps to ACTIVE profile`() {
        val rec = CloneRecord(
            packageName = "com.example.notes",
            cloneId = "u1",
            cloneName = "Notes",
            colorTag = 0xFF7C6CFF,
            createdAt = 1_000L,
            engineName = "blackbox",
            engineUserId = 1,
            lastLaunched = null
        )
        val profile = rec.toProfile("Notes", engineConfirmed = true)
        assertEquals(CloneStatus.ACTIVE, profile.status)
        assertEquals("u1", profile.id)
        assertEquals("Notes", profile.name)
        assertEquals(1, profile.engineUserId)
        assertNull(profile.lastLaunchedMillis)
    }

    @Test
    fun `unconfirmed record maps to ERROR profile`() {
        val rec = CloneRecord(
            packageName = "com.example.browser",
            cloneId = "u2",
            cloneName = "Browser",
            colorTag = 0xFF49D6C2,
            createdAt = 2_000L,
            engineName = "blackbox",
            engineUserId = 2,
            lastLaunched = 3_000L
        )
        val profile = rec.toProfile("Browser", engineConfirmed = false)
        assertEquals(CloneStatus.ERROR, profile.status)
        assertEquals(3_000L, profile.lastLaunchedMillis)
    }

    @Test
    fun `profile roundtrips through record`() {
        val rec = CloneRecord(
            packageName = "com.example.files",
            cloneId = "u3",
            cloneName = "Files",
            colorTag = 0xFF3DDC97,
            createdAt = 5_000L,
            engineName = "blackbox",
            engineUserId = 3,
            lastLaunched = 6_000L
        )
        val roundtripped = rec.toProfile("Files", engineConfirmed = true).toRecord("blackbox")
        assertEquals(rec, roundtripped)
    }

    @Test
    fun `activity record roundtrips`() {
        val event = com.kuro.app.domain.models.ActivityEvent(
            id = "99",
            cloneId = "u1",
            cloneName = "Notes",
            type = ActivityType.LAUNCHED,
            timestampMillis = 7_000L,
            detail = "Launched by engine"
        )
        val rec = event.toRecord()
        assertEquals("LAUNCHED", rec.type)
        val back = rec.copy(uid = 99).toEvent()
        assertEquals(ActivityType.LAUNCHED, back.type)
        assertEquals("u1", back.cloneId)
    }
}
