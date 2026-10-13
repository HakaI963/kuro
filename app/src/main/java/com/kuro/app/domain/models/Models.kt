package com.kuro.app.domain.models

enum class CloneStatus { ACTIVE, FROZEN, PRIVATE, SYNCING, ERROR }

enum class IsolationMode { STANDARD, PRIVATE, WORK, EXPERIMENTAL }

enum class ActivityType { CREATED, LAUNCHED, FROZEN, UNFROZEN, HIDDEN, UNHIDDEN, DELETED }

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
 * UI-facing clone profile. Persisted source of truth is CloneRecord (Room);
 * status/icon fields are enriched from DevicePolicyManager + PackageManager
 * at repository level.
 */
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

/** Work-profile provisioning state machine. Pure logic, unit-tested. */
sealed interface ProvisioningState {
    data object Checking : ProvisioningState
    data object NotSetup : ProvisioningState
    data object ProfileExistsNotOwner : ProvisioningState
    data object Active : ProvisioningState
    data class Blocked(val reason: ProvisionReason) : ProvisioningState
}

enum class ProvisionReason {
    USER_CANCELLED,
    ALREADY_OWNED_BY_ANOTHER_APP,
    POLICY_BLOCKED,
    UNKNOWN
}
