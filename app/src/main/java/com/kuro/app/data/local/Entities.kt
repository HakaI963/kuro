package com.kuro.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clones")
data class CloneRecord(
    @PrimaryKey val packageName: String,
    val cloneName: String,
    val colorTag: Long,
    val createdAt: Long,
    val frozen: Boolean,
    val hidden: Boolean,
    val lastLaunched: Long?,
    val isolationMode: String,
    val notificationsEnabled: Boolean,
    val separateData: Boolean,
    val biometricLock: Boolean
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
