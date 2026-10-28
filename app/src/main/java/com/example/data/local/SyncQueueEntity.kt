package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val itemId: String,
    val action: String, // MARK_READ, MARK_UNREAD, STAR, UNSTAR, MARK_FEED_READ
    val timestamp: Long = System.currentTimeMillis()
)
