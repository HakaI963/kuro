package com.kuro.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CloneRecord::class, ActivityRecord::class], version = 3, exportSchema = false)
abstract class KuroDatabase : RoomDatabase() {
    abstract fun cloneDao(): CloneDao
    abstract fun activityDao(): ActivityDao
}
