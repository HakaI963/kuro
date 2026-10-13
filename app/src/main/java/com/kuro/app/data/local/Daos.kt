package com.kuro.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CloneDao {
    @Query("SELECT * FROM clones ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CloneRecord>>

    @Query("SELECT * FROM clones WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): CloneRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CloneRecord)

    @Query("DELETE FROM clones WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<ActivityRecord>>

    @Insert
    suspend fun insert(record: ActivityRecord)

    @Query("DELETE FROM activity WHERE timestampMillis < :before")
    suspend fun pruneBefore(before: Long)
}
