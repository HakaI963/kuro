package com.kuro.app.data.local

import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneStatus
import com.kuro.app.domain.models.IsolationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloneRecordMappingTest {

    @Test
    fun `frozen record maps to FROZEN profile`() {
        val rec = CloneRecord(
            packageName = "com.example.notes",
            cloneName = "Notes",
            colorTag = 0xFF7C6CFF,
            createdAt = 1_000L,
            frozen = true,
            hidden = false,
            lastLaunched = null,
            isolationMode = IsolationMode.STANDARD.name,
            notificationsEnabled = true,
            separateData = true,
            biometricLock = false
        )
        val profile = rec.toProfile("Notes")
        assertEquals(CloneStatus.FROZEN, profile.status)
        assertEquals("com.example.notes", profile.id)
        assertEquals("Notes", profile.name)
    }

    @Test
    fun `hidden record maps to PRIVATE profile`() {
        val rec = CloneRecord(
            packageName = "com.example.browser",
            cloneName = "Browser",
            colorTag = 0xFF49D6C2,
            createdAt = 2_000L,
            frozen = false,
            hidden = true,
            lastLaunched = 3_000L,
            isolationMode = IsolationMode.STANDARD.name,
            notificationsEnabled = false,
            separateData = true,
            biometricLock = true
        )
        val profile = rec.toProfile("Browser")
        assertEquals(CloneStatus.PRIVATE, profile.status)
        assertEquals(3_000L, profile.lastLaunchedMillis)
    }

    @Test
    fun `plain record maps to ACTIVE profile`() {
        val rec = CloneRecord(
            packageName = "com.example.music",
            cloneName = "Music",
            colorTag = 0xFF5DB2FF,
            createdAt = 4_000L,
            frozen = false,
            hidden = false,
            lastLaunched = null,
            isolationMode = IsolationMode.WORK.name,
            notificationsEnabled = true,
            separateData = false,
            biometricLock = false
        )
        val profile = rec.toProfile("Music")
        assertEquals(CloneStatus.ACTIVE, profile.status)
        assertEquals(IsolationMode.WORK, profile.isolationMode)
        assertNull(profile.lastLaunchedMillis)
    }

    @Test
    fun `profile roundtrips through record`() {
        val rec = CloneRecord(
            packageName = "com.example.files",
            cloneName = "Files",
            colorTag = 0xFF3DDC97,
            createdAt = 5_000L,
            frozen = false,
            hidden = false,
            lastLaunched = 6_000L,
            isolationMode = IsolationMode.PRIVATE.name,
            notificationsEnabled = true,
            separateData = true,
            biometricLock = false
        )
        val roundtripped = rec.toProfile("Files").toRecord()
        assertEquals(rec, roundtripped)
    }

    @Test
    fun `activity record roundtrips`() {
        val event = com.kuro.app.domain.models.ActivityEvent(
            id = "99",
            cloneId = "com.example.notes",
            cloneName = "Notes",
            type = ActivityType.HIDDEN,
            timestampMillis = 7_000L,
            detail = "Hidden from launcher"
        )
        val rec = event.toRecord()
        assertEquals("HIDDEN", rec.type)
        val back = rec.copy(uid = 99).toEvent()
        assertEquals(ActivityType.HIDDEN, back.type)
        assertEquals("com.example.notes", back.cloneId)
    }
}
