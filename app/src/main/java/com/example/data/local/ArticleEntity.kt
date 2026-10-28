package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    indices = [
        Index("feedId"),
        Index("isRead"),
        Index("isStarred"),
        Index("publishedAt")
    ]
)
data class ArticleEntity(
    @PrimaryKey
    val id: String,
    val feedId: String,
    val feedTitle: String,
    val title: String,
    val author: String? = null,
    val publishedAt: Long = 0L,
    val summaryHtml: String = "",
    val contentHtml: String = "",
    val link: String? = null,
    val imageUrl: String? = null,
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)
