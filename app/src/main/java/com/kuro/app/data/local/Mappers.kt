package com.kuro.app.data.local

import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.CloneStatus

fun CloneRecord.toProfile(sourceLabel: String): CloneProfile = CloneProfile(
    id = cloneId,
    name = cloneName,
    sourceAppId = packageName,
    sourceAppName = sourceLabel,
    status = CloneStatus.ACTIVE,
    createdAtMillis = createdAt,
    lastLaunchedMillis = lastLaunched,
    labelColorArgb = colorTag,
    iconLetter = cloneName.firstOrNull()?.uppercase() ?: "?"
)

fun CloneProfile.toRecord(engineName: String): CloneRecord = CloneRecord(
    cloneId = id,
    packageName = sourceAppId,
    cloneName = name,
    colorTag = labelColorArgb,
    createdAt = createdAtMillis,
    engineName = engineName,
    lastLaunched = lastLaunchedMillis
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
