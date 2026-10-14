package com.kuro.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CloneRecord::class, ActivityRecord::class, ContainerRecord::class, SessionRecord::class],
    version = 4,
    exportSchema = false
)
abstract class KuroDatabase : RoomDatabase() {
    abstract fun cloneDao(): CloneDao
    abstract fun activityDao(): ActivityDao
    abstract fun containerDao(): ContainerDao
    abstract fun sessionDao(): SessionDao
}
