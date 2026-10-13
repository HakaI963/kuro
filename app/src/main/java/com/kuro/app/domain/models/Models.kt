package com.kuro.app.domain.models

enum class CloneStatus { ACTIVE, FROZEN, PRIVATE, SYNCING, ERROR }

enum class IsolationMode { STANDARD, PRIVATE, WORK, EXPERIMENTAL }

enum class ActivityType { CREATED, LAUNCHED, FROZEN, UNFROZEN, DELETED }

data class InstalledApp(
    val id: String,
    val name: String,
    val packageName: String,
    val versionName: String,
    val sizeBytes: Long,
    val iconLetter: String,
    val accentArgb: Long
)

data class CloneProfile(
    val id: String,
    val name: String,
    val sourceAppId: String,
    val sourceAppName: String,
    val status: CloneStatus,
    val isolationMode: IsolationMode,
    val createdAtMillis: Long,
    val lastLaunchedMillis: Long?,
    val storageBytes: Long,
    val notificationsEnabled: Boolean,
    val separateData: Boolean,
    val hiddenFromLauncher: Boolean,
    val biometricLock: Boolean,
    val labelColorArgb: Long,
    val iconLetter: String
)

data class ActivityEvent(
    val id: String,
    val cloneId: String,
    val cloneName: String,
    val type: ActivityType,
    val timestampMillis: Long,
    val detail: String
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val reduceMotion: Boolean = false,
    val defaultToLibrary: Boolean = false,
    val autoFreezeIdle: Boolean = true,
    val showHiddenClones: Boolean = true,
    val appLockEnabled: Boolean = false,
    val biometricUnlock: Boolean = false,
    val onboardingCompleted: Boolean = false
)

enum class ThemeMode { SYSTEM, DARK, LIGHT }
