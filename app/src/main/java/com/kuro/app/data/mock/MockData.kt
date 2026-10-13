package com.kuro.app.data.mock

import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.CloneStatus
import com.kuro.app.domain.models.InstalledApp
import com.kuro.app.domain.models.IsolationMode

object MockData {
    private const val DAY = 86_400_000L
    private const val NOW = 1_730_000_000_000L

    val installedApps = listOf(
        InstalledApp("notes", "Notes", "com.example.notes", "2.4.1", 48_000_000L, "N", 0xFF7C6CFF),
        InstalledApp("camera", "Camera", "com.example.camera", "5.1.0", 92_000_000L, "C", 0xFF5DB2FF),
        InstalledApp("files", "Files", "com.example.files", "3.8.2", 36_000_000L, "F", 0xFF49D6C2),
        InstalledApp("music", "Music", "com.example.music", "4.2.0", 71_000_000L, "M", 0xFF3DDC97),
        InstalledApp("browser", "Browser", "com.example.browser", "6.0.3", 118_000_000L, "B", 0xFFFFC861),
        InstalledApp("wallet", "Wallet", "com.example.wallet", "1.9.4", 54_000_000L, "W", 0xFFFF6B6B)
    )

    val apkSamples = listOf(
        InstalledApp("apk-notes", "Notes 2.4.1", "com.example.notes", "2.4.1", 48_000_000L, "N", 0xFF7C6CFF),
        InstalledApp("apk-music", "Music 4.2.0", "com.example.music", "4.2.0", 71_000_000L, "M", 0xFF3DDC97)
    )

    val initialClones = listOf(
        CloneProfile(
            id = "clone-notes-work",
            name = "Notes • Work",
            sourceAppId = "notes",
            sourceAppName = "Notes",
            status = CloneStatus.ACTIVE,
            isolationMode = IsolationMode.WORK,
            createdAtMillis = NOW - 12 * DAY,
            lastLaunchedMillis = NOW - 2 * 3_600_000L,
            storageBytes = 214_000_000L,
            notificationsEnabled = true,
            separateData = true,
            hiddenFromLauncher = false,
            biometricLock = false,
            labelColorArgb = 0xFF7C6CFF,
            iconLetter = "N"
        ),
        CloneProfile(
            id = "clone-browser-private",
            name = "Browser • Private",
            sourceAppId = "browser",
            sourceAppName = "Browser",
            status = CloneStatus.PRIVATE,
            isolationMode = IsolationMode.PRIVATE,
            createdAtMillis = NOW - 7 * DAY,
            lastLaunchedMillis = NOW - 26 * 3_600_000L,
            storageBytes = 386_000_000L,
            notificationsEnabled = false,
            separateData = true,
            hiddenFromLauncher = true,
            biometricLock = true,
            labelColorArgb = 0xFF49D6C2,
            iconLetter = "B"
        ),
        CloneProfile(
            id = "clone-music-frozen",
            name = "Music • Test",
            sourceAppId = "music",
            sourceAppName = "Music",
            status = CloneStatus.FROZEN,
            isolationMode = IsolationMode.STANDARD,
            createdAtMillis = NOW - 3 * DAY,
            lastLaunchedMillis = NOW - 3 * DAY,
            storageBytes = 142_000_000L,
            notificationsEnabled = true,
            separateData = false,
            hiddenFromLauncher = false,
            biometricLock = false,
            labelColorArgb = 0xFF5DB2FF,
            iconLetter = "M"
        ),
        CloneProfile(
            id = "clone-files-sync",
            name = "Files • Lab",
            sourceAppId = "files",
            sourceAppName = "Files",
            status = CloneStatus.SYNCING,
            isolationMode = IsolationMode.EXPERIMENTAL,
            createdAtMillis = NOW - 1 * DAY,
            lastLaunchedMillis = null,
            storageBytes = 98_000_000L,
            notificationsEnabled = true,
            separateData = true,
            hiddenFromLauncher = false,
            biometricLock = false,
            labelColorArgb = 0xFF3DDC97,
            iconLetter = "F"
        )
    )

    val initialActivity = listOf(
        ActivityEvent("ev1", "clone-notes-work", "Notes • Work", ActivityType.LAUNCHED, NOW - 2 * 3_600_000L, "Opened isolated space"),
        ActivityEvent("ev2", "clone-files-sync", "Files • Lab", ActivityType.CREATED, NOW - 1 * DAY, "Profile prepared"),
        ActivityEvent("ev3", "clone-music-frozen", "Music • Test", ActivityType.FROZEN, NOW - 2 * DAY, "Frozen to save resources"),
        ActivityEvent("ev4", "clone-browser-private", "Browser • Private", ActivityType.CREATED, NOW - 7 * DAY, "Private profile prepared"),
        ActivityEvent("ev5", "clone-notes-work", "Notes • Work", ActivityType.CREATED, NOW - 12 * DAY, "Work profile prepared")
    )
}
