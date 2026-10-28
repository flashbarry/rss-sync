package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isRead = 0 ORDER BY publishedAt DESC")
    fun getUnreadArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isStarred = 1 ORDER BY publishedAt DESC")
    fun getStarredArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY publishedAt DESC")
    fun getArticlesByFeed(feedId: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId AND isRead = 0 ORDER BY publishedAt DESC")
    fun getUnreadArticlesByFeed(feedId: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId AND isStarred = 1 ORDER BY publishedAt DESC")
    fun getStarredArticlesByFeed(feedId: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    fun getArticleById(id: String): Flow<ArticleEntity?>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getArticleByIdDirect(id: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :articleId")
    suspend fun setReadState(articleId: String, isRead: Boolean)

    @Query("UPDATE articles SET isStarred = :isStarred WHERE id = :articleId")
    suspend fun setStarredState(articleId: String, isStarred: Boolean)

    @Query("UPDATE articles SET isRead = 1 WHERE feedId = :feedId")
    suspend fun markAllAsReadInFeed(feedId: String)

    @Query("UPDATE articles SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM articles WHERE isRead = 1 AND isStarred = 0 AND publishedAt < :olderThanTimestamp")
    suspend fun deleteOldReadArticles(olderThanTimestamp: Long): Int

    @Query("DELETE FROM articles")
    suspend fun deleteAllArticles()

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun getArticleCount(): Int

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    suspend fun getUnreadCount(): Int
}
