package com.kuro.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clones")
data class CloneRecord(
    @PrimaryKey val cloneId: String,
    val packageName: String,
    val cloneName: String,
    val colorTag: Long,
    val createdAt: Long,
    val engineName: String,
    val engineUserId: Int,
    val lastLaunched: Long? = null
)

@Entity(tableName = "activity")
data class ActivityRecord(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val cloneId: String,
    val cloneName: String,
    val type: String,
    val timestampMillis: Long,
    val detail: String
)

/** Step 5: persisted container config (live status stays in memory). */
@Entity(tableName = "containers")
data class ContainerRecord(
    @PrimaryKey val id: String,
    val name: String,
    val distroId: String,
    val type: String,
    val shell: String,
    val bridgeEnabled: Boolean,
    val keepAlive: Boolean,
    val wakeLock: Boolean,
    val interfaceMode: String,
    val createdAt: Long
)

/** Step 5: persisted session metadata (live fd/pid stay in memory). */
@Entity(tableName = "sessions")
data class SessionRecord(
    @PrimaryKey val id: String,
    val containerId: String,
    val pid: Int,
    val startedAt: Long,
    val status: String
)
