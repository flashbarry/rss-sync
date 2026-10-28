package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    suspend fun getPendingActions(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun getPendingActionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getPendingActionCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAction(action: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteActionById(id: Long)

    @Query("DELETE FROM sync_queue")
    suspend fun clearQueue()
}
