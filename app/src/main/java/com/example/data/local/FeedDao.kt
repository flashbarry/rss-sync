package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY category ASC, title ASC")
    fun getAllFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE id = :id LIMIT 1")
    suspend fun getFeedById(id: String): FeedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeeds(feeds: List<FeedEntity>)

    @Query("UPDATE feeds SET unreadCount = :count WHERE id = :feedId")
    suspend fun updateUnreadCount(feedId: String, count: Int)

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun deleteFeed(id: String)

    @Query("DELETE FROM feeds")
    suspend fun deleteAllFeeds()
}
