package com.kuro.app.domain.models

enum class CloneStatus { ACTIVE, ERROR }

enum class ActivityType { CREATED, LAUNCHED, DELETED }

/**
 * Real installed app entry resolved via PackageManager.
 * Icon drawables are loaded on demand through InstalledAppRepository icon cache
 * (never stored in this data class so it stays testable and parcel-safe).
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean
) {
    val id: String get() = packageName
    val name: String get() = label
    val iconLetter: String get() = label.firstOrNull()?.uppercase() ?: "?"
    val accentArgb: Long get() = 0xFF7C6CFF
    val sizeBytes: Long get() = 0L
}

/**
 * UI-facing clone profile. Persisted source of truth is CloneRecord (Room):
 * cloneId, packageName, cloneName, colorTag, createdAt, engineName.
 * Status and last-launched are enriched at repository level.
 */
data class CloneProfile(
    val id: String,
    val name: String,
    val sourceAppId: String,
    val sourceAppName: String,
    val status: CloneStatus,
    val engineUserId: Int,
    val createdAtMillis: Long,
    val lastLaunchedMillis: Long?,
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
    val onboardingCompleted: Boolean = false
)

enum class ThemeMode { SYSTEM, DARK, LIGHT }
