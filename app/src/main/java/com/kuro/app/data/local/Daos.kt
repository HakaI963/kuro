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

    @Query("SELECT * FROM clones WHERE cloneId = :cloneId LIMIT 1")
    suspend fun get(cloneId: String): CloneRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CloneRecord)

    @Query("UPDATE clones SET lastLaunched = :timestamp WHERE cloneId = :cloneId")
    suspend fun setLastLaunched(cloneId: String, timestamp: Long)

    @Query("DELETE FROM clones WHERE cloneId = :cloneId")
    suspend fun delete(cloneId: String)
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

@Dao
interface ContainerDao {
    @Query("SELECT * FROM containers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ContainerRecord>>

    @Query("SELECT * FROM containers WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ContainerRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: ContainerRecord)

    @Query("DELETE FROM containers WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SessionRecord)

    @Query("UPDATE sessions SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("DELETE FROM sessions WHERE containerId = :containerId")
    suspend fun deleteForContainer(containerId: String)
}
