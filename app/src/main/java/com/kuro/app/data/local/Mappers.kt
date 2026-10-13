package com.kuro.app.data.local

import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.CloneStatus
import com.kuro.app.domain.models.IsolationMode

fun CloneRecord.toProfile(
    sourceLabel: String,
    storageBytes: Long = 0L
): CloneProfile {
    val status = when {
        frozen -> CloneStatus.FROZEN
        hidden || isolationMode == IsolationMode.PRIVATE.name -> CloneStatus.PRIVATE
        else -> CloneStatus.ACTIVE
    }
    return CloneProfile(
        id = packageName,
        name = cloneName,
        sourceAppId = packageName,
        sourceAppName = sourceLabel,
        status = status,
        isolationMode = runCatching { IsolationMode.valueOf(isolationMode) }.getOrDefault(IsolationMode.STANDARD),
        createdAtMillis = createdAt,
        lastLaunchedMillis = lastLaunched,
        storageBytes = storageBytes,
        notificationsEnabled = notificationsEnabled,
        separateData = separateData,
        hiddenFromLauncher = hidden,
        biometricLock = biometricLock,
        labelColorArgb = colorTag,
        iconLetter = cloneName.firstOrNull()?.uppercase() ?: "?"
    )
}

fun CloneProfile.toRecord(): CloneRecord = CloneRecord(
    packageName = sourceAppId,
    cloneName = name,
    colorTag = labelColorArgb,
    createdAt = createdAtMillis,
    frozen = status == CloneStatus.FROZEN,
    hidden = hiddenFromLauncher,
    lastLaunched = lastLaunchedMillis,
    isolationMode = isolationMode.name,
    notificationsEnabled = notificationsEnabled,
    separateData = separateData,
    biometricLock = biometricLock
)

fun ActivityRecord.toEvent(): ActivityEvent = ActivityEvent(
    id = uid.toString(),
    cloneId = cloneId,
    cloneName = cloneName,
    type = runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.CREATED),
    timestampMillis = timestampMillis,
    detail = detail
)

fun ActivityEvent.toRecord(): ActivityRecord = ActivityRecord(
    cloneId = cloneId,
    cloneName = cloneName,
    type = type.name,
    timestampMillis = timestampMillis,
    detail = detail
)
