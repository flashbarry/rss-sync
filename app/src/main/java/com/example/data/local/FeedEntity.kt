package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val url: String,
    val htmlUrl: String? = null,
    val iconUrl: String? = null,
    val category: String = "未分类",
    val unreadCount: Int = 0
)
