package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.ArticleEntity
import com.example.data.local.FeedEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.preferences.PreferencesManager
import com.example.data.remote.FreshRssApiService
import com.example.data.remote.StreamItemDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class SyncResult(
    val success: Boolean,
    val newArticlesCount: Int = 0,
    val pendingSyncedCount: Int = 0,
    val errorMessage: String? = null
)

class RssRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val feedDao = db.feedDao()
    private val articleDao = db.articleDao()
    private val syncQueueDao = db.syncQueueDao()
    val preferences = PreferencesManager(context)
    val api = FreshRssApiService()

    // Flows for UI consumption
    val allFeeds: Flow<List<FeedEntity>> = feedDao.getAllFeeds()
    val allArticles: Flow<List<ArticleEntity>> = articleDao.getAllArticles()
    val unreadArticles: Flow<List<ArticleEntity>> = articleDao.getUnreadArticles()
    val starredArticles: Flow<List<ArticleEntity>> = articleDao.getStarredArticles()
    val pendingActionCount: Flow<Int> = syncQueueDao.getPendingActionCount()

    fun getArticlesByFeed(feedId: String): Flow<List<ArticleEntity>> =
        articleDao.getArticlesByFeed(feedId)

    fun getUnreadArticlesByFeed(feedId: String): Flow<List<ArticleEntity>> =
        articleDao.getUnreadArticlesByFeed(feedId)

    fun getStarredArticlesByFeed(feedId: String): Flow<List<ArticleEntity>> =
        articleDao.getStarredArticlesByFeed(feedId)

    fun getArticleById(id: String): Flow<ArticleEntity?> =
        articleDao.getArticleById(id)

    /**
     * Connect and authenticate with FreshRSS VPS.
     */
    suspend fun loginToVps(
        serverUrl: String,
        username: String,
        apiPassword: String
    ): Result<String> {
        val result = api.login(serverUrl, username, apiPassword)
        if (result.isSuccess) {
            val token = result.getOrNull() ?: ""
            preferences.serverUrl = serverUrl
            preferences.username = username
            preferences.authToken = token
            preferences.isLoggedIn = true
            preferences.isDemoMode = false

            // Trigger initial sync from VPS
            sync(force = true)
        }
        return result
    }

    /**
     * Start Demo Mode with pre-populated RSS content.
     */
    suspend fun startDemoMode() {
        preferences.isDemoMode = true
        preferences.isLoggedIn = true
        preferences.username = "Demo User (本地演示)"
        preferences.serverUrl = "https://demo.freshrss.org"
        preferences.authToken = "demo-token"
        seedDemoData()
    }

    /**
     * Log out and clear session.
     */
    suspend fun logout() {
        preferences.clearAuth()
        withContext(Dispatchers.IO) {
            syncQueueDao.clearQueue()
        }
    }

    /**
     * Perform full two-way synchronization:
     * 1. Push pending offline changes (mark read, star, etc.) to VPS.
     * 2. Pull latest subscriptions and unread counts from VPS.
     * 3. Pull latest stream articles (with full offline caching).
     */
    suspend fun sync(force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        if (preferences.isDemoMode) {
            preferences.lastSyncTimestamp = System.currentTimeMillis()
            val total = articleDao.getArticleCount()
            if (total == 0) {
                seedDemoData()
            }
            return@withContext SyncResult(success = true, newArticlesCount = total)
        }

        val token = preferences.authToken
        val serverUrl = preferences.serverUrl
        if (token.isNullOrEmpty() || serverUrl.isEmpty()) {
            return@withContext SyncResult(
                success = false,
                errorMessage = "未登录 FreshRSS VPS，请先配置服务器连接"
            )
        }

        val apiBase = api.normalizeApiBase(serverUrl)

        try {
            // Step 1: Push queued offline actions
            val pushedCount = pushPendingQueue(apiBase, token)

            // Step 2: Pull Subscriptions and Unread Counts
            val subResult = api.getSubscriptions(apiBase, token)
            if (subResult.isFailure) {
                return@withContext SyncResult(
                    success = false,
                    errorMessage = "拉取订阅列表失败: ${subResult.exceptionOrNull()?.message}"
                )
            }

            val unreadCountMap = mutableMapOf<String, Int>()
            val unreadResult = api.getUnreadCounts(apiBase, token)
            unreadResult.getOrNull()?.unreadcounts?.forEach {
                unreadCountMap[it.id] = it.count
            }

            val subs = subResult.getOrNull()?.subscriptions ?: emptyList()
            val feedEntities = subs.map { sub ->
                val categoryName = sub.categories?.firstOrNull()?.label ?: "订阅源"
                FeedEntity(
                    id = sub.id,
                    title = sub.title.ifEmpty { "未命名源" },
                    url = sub.url ?: "",
                    htmlUrl = sub.htmlUrl,
                    iconUrl = sub.iconUrl,
                    category = categoryName,
                    unreadCount = unreadCountMap[sub.id] ?: 0
                )
            }
            feedDao.insertFeeds(feedEntities)

            // Step 3: Pull Articles from Reading List
            val streamResult = api.getStreamContents(
                apiBase = apiBase,
                authToken = token,
                streamId = "user/-/state/com.google/reading-list",
                count = 100,
                excludeRead = false
            )

            if (streamResult.isFailure) {
                return@withContext SyncResult(
                    success = false,
                    errorMessage = "拉取文章失败: ${streamResult.exceptionOrNull()?.message}"
                )
            }

            val items = streamResult.getOrNull()?.items ?: emptyList()
            val pendingActions = syncQueueDao.getPendingActions().associateBy { it.itemId }

            val articleEntities = items.map { item ->
                mapDtoToEntity(item, pendingActions)
            }

            articleDao.insertArticles(articleEntities)

            // Also pull Starred articles to ensure offline availability
            val starredResult = api.getStreamContents(
                apiBase = apiBase,
                authToken = token,
                streamId = "user/-/state/com.google/starred",
                count = 50,
                excludeRead = false
            )
            val starredItems = starredResult.getOrNull()?.items ?: emptyList()
            if (starredItems.isNotEmpty()) {
                val starredEntities = starredItems.map { item ->
                    mapDtoToEntity(item, pendingActions)
                }
                articleDao.insertArticles(starredEntities)
            }

            preferences.lastSyncTimestamp = System.currentTimeMillis()

            SyncResult(
                success = true,
                newArticlesCount = articleEntities.size,
                pendingSyncedCount = pushedCount
            )
        } catch (e: Exception) {
            SyncResult(
                success = false,
                errorMessage = "同步异常: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    /**
     * Push all queued offline actions to the VPS server.
     */
    private suspend fun pushPendingQueue(apiBase: String, token: String): Int {
        val pendingList = syncQueueDao.getPendingActions()
        if (pendingList.isEmpty()) return 0

        val tokenResult = api.getActionToken(apiBase, token)
        val actionToken = tokenResult.getOrNull() ?: return 0

        var successCount = 0
        for (item in pendingList) {
            val result = when (item.action) {
                "MARK_READ" -> api.editTag(
                    apiBase = apiBase,
                    authToken = token,
                    actionToken = actionToken,
                    itemId = item.itemId,
                    addTag = "user/-/state/com.google/read"
                )
                "MARK_UNREAD" -> api.editTag(
                    apiBase = apiBase,
                    authToken = token,
                    actionToken = actionToken,
                    itemId = item.itemId,
                    removeTag = "user/-/state/com.google/read"
                )
                "STAR" -> api.editTag(
                    apiBase = apiBase,
                    authToken = token,
                    actionToken = actionToken,
                    itemId = item.itemId,
                    addTag = "user/-/state/com.google/starred"
                )
                "UNSTAR" -> api.editTag(
                    apiBase = apiBase,
                    authToken = token,
                    actionToken = actionToken,
                    itemId = item.itemId,
                    removeTag = "user/-/state/com.google/starred"
                )
                "MARK_FEED_READ" -> api.markAllAsRead(
                    apiBase = apiBase,
                    authToken = token,
                    actionToken = actionToken,
                    streamId = item.itemId
                )
                else -> Result.success(true)
            }

            if (result.isSuccess) {
                syncQueueDao.deleteActionById(item.id)
                successCount++
            }
        }
        return successCount
    }

    private fun mapDtoToEntity(
        dto: StreamItemDto,
        pendingActions: Map<String, SyncQueueEntity>
    ): ArticleEntity {
        val categories = dto.categories ?: emptyList()
        var isRead = categories.contains("user/-/state/com.google/read")
        var isStarred = categories.contains("user/-/state/com.google/starred")

        // If local offline action hasn't been synced yet, honor local intention
        val pending = pendingActions[dto.id]
        if (pending != null) {
            when (pending.action) {
                "MARK_READ" -> isRead = true
                "MARK_UNREAD" -> isRead = false
                "STAR" -> isStarred = true
                "UNSTAR" -> isStarred = false
            }
        }

        val content = dto.content?.content ?: dto.summary?.content ?: ""
        val summary = dto.summary?.content ?: content.take(300)
        val imageUrl = extractFirstImageUrl(content).ifEmpty { extractFirstImageUrl(summary) }
        val link = dto.alternate?.firstOrNull()?.href ?: dto.canonical?.firstOrNull()?.href

        val feedId = dto.origin?.streamId ?: "unknown"
        val feedTitle = dto.origin?.title ?: "RSS Feed"

        // published timestamp can be in seconds or milliseconds
        var publishedMs = (dto.published ?: (System.currentTimeMillis() / 1000))
        if (publishedMs < 1000000000000L) {
            publishedMs *= 1000L
        }

        return ArticleEntity(
            id = dto.id,
            feedId = feedId,
            feedTitle = feedTitle,
            title = dto.title?.trim()?.ifEmpty { "无标题文章" } ?: "无标题文章",
            author = dto.author,
            publishedAt = publishedMs,
            summaryHtml = summary,
            contentHtml = content,
            link = link,
            imageUrl = imageUrl.ifEmpty { null },
            isRead = isRead,
            isStarred = isStarred
        )
    }

    private fun extractFirstImageUrl(html: String): String {
        if (html.isEmpty()) return ""
        val pattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        return if (matcher.find()) {
            matcher.group(1)?.trim() ?: ""
        } else ""
    }

    /**
     * Mark an article as read or unread (offline immediate + sync queue).
     */
    suspend fun setArticleRead(articleId: String, isRead: Boolean) = withContext(Dispatchers.IO) {
        articleDao.setReadState(articleId, isRead)
        if (preferences.isLoggedIn && !preferences.isDemoMode) {
            val action = if (isRead) "MARK_READ" else "MARK_UNREAD"
            syncQueueDao.enqueueAction(SyncQueueEntity(itemId = articleId, action = action))
            // Attempt instant background sync
            pushPendingInBackground()
        }
    }

    /**
     * Toggle star on an article (offline immediate + sync queue).
     */
    suspend fun setArticleStarred(articleId: String, isStarred: Boolean) = withContext(Dispatchers.IO) {
        articleDao.setStarredState(articleId, isStarred)
        if (preferences.isLoggedIn && !preferences.isDemoMode) {
            val action = if (isStarred) "STAR" else "UNSTAR"
            syncQueueDao.enqueueAction(SyncQueueEntity(itemId = articleId, action = action))
            pushPendingInBackground()
        }
    }

    /**
     * Mark all articles in a feed as read.
     */
    suspend fun markFeedAsRead(feedId: String) = withContext(Dispatchers.IO) {
        articleDao.markAllAsReadInFeed(feedId)
        feedDao.updateUnreadCount(feedId, 0)
        if (preferences.isLoggedIn && !preferences.isDemoMode) {
            syncQueueDao.enqueueAction(SyncQueueEntity(itemId = feedId, action = "MARK_FEED_READ"))
            pushPendingInBackground()
        }
    }

    /**
     * Mark all articles as read everywhere.
     */
    suspend fun markAllAsRead() = withContext(Dispatchers.IO) {
        articleDao.markAllAsRead()
        if (preferences.isLoggedIn && !preferences.isDemoMode) {
            syncQueueDao.enqueueAction(SyncQueueEntity(itemId = "user/-/state/com.google/reading-list", action = "MARK_FEED_READ"))
            pushPendingInBackground()
        }
    }

    /**
     * Add a new RSS subscription URL.
     */
    suspend fun addSubscription(feedUrl: String): Result<String> = withContext(Dispatchers.IO) {
        if (preferences.isDemoMode) {
            val id = "feed/$feedUrl"
            val title = feedUrl.substringAfter("://").substringBefore("/")
            feedDao.insertFeeds(listOf(FeedEntity(id = id, title = title, url = feedUrl, category = "自定义订阅")))
            return@withContext Result.success("成功添加订阅源: $title (演示模式)")
        }

        val token = preferences.authToken
        val serverUrl = preferences.serverUrl
        if (token.isNullOrEmpty() || serverUrl.isEmpty()) {
            return@withContext Result.failure(Exception("请先登录 FreshRSS VPS"))
        }

        val apiBase = api.normalizeApiBase(serverUrl)
        val tokenRes = api.getActionToken(apiBase, token)
        val actionToken = tokenRes.getOrNull()
            ?: return@withContext Result.failure(Exception("无法获取操作 Token"))

        val quickAddRes = api.quickAdd(apiBase, token, actionToken, feedUrl)
        if (quickAddRes.isSuccess) {
            sync(force = true)
            Result.success("订阅成功: ${quickAddRes.getOrNull()?.streamName ?: feedUrl}")
        } else {
            Result.failure(quickAddRes.exceptionOrNull() ?: Exception("添加订阅失败"))
        }
    }

    /**
     * Delete read articles older than given days (preserves starred articles).
     */
    suspend fun clearOldArticles(days: Int = 30): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
        articleDao.deleteOldReadArticles(cutoff)
    }

    private suspend fun pushPendingInBackground() {
        val token = preferences.authToken
        val serverUrl = preferences.serverUrl
        if (!token.isNullOrEmpty() && serverUrl.isNotEmpty()) {
            val apiBase = api.normalizeApiBase(serverUrl)
            try {
                pushPendingQueue(apiBase, token)
            } catch (_: Exception) {
                // Ignore background network failure, actions remain in SyncQueue
            }
        }
    }

    /**
     * Seeds rich demo feeds and articles for instant local offline preview.
     */
    suspend fun seedDemoData() = withContext(Dispatchers.IO) {
        val feeds = listOf(
            FeedEntity(
                id = "feed/tech_crunch",
                title = "TechCrunch 科技前沿",
                url = "https://techcrunch.com/feed/",
                category = "科技资讯",
                unreadCount = 3
            ),
            FeedEntity(
                id = "feed/sspai",
                title = "少数派 (sspai)",
                url = "https://sspai.com/feed",
                category = "效率生活",
                unreadCount = 2
            ),
            FeedEntity(
                id = "feed/v2ex",
                title = "V2EX 热门讨论",
                url = "https://www.v2ex.com/index.xml",
                category = "开发者社区",
                unreadCount = 1
            ),
            FeedEntity(
                id = "feed/hacker_news",
                title = "Hacker News Top",
                url = "https://news.ycombinator.com/rss",
                category = "开发者社区",
                unreadCount = 2
            )
        )
        feedDao.insertFeeds(feeds)

        val now = System.currentTimeMillis()
        val articles = listOf(
            ArticleEntity(
                id = "demo_art_1",
                feedId = "feed/tech_crunch",
                feedTitle = "TechCrunch 科技前沿",
                title = "新一代轻量级开源 RSS 生态的崛起与自建 VPS 趋势",
                author = "Alex Rivera",
                publishedAt = now - 1800000,
                summaryHtml = "<p>在算法推荐充斥的互联网时代，越来越多的极客与独立阅读者选择通过 FreshRSS、Miniflux 与 Google Reader API 重新掌握信息的主动权。</p>",
                contentHtml = """
                    <h3>为什么我们需要自建 RSS 聚合器？</h3>
                    <p>中心化社交平台不断调整算法，使得高价值的内容往往被噪音淹没。FreshRSS 作为一款优秀的自托管开源 RSS 聚合服务，不仅提供了轻量高效的后端，还完整支持了行业标准的 <b>Google Reader API</b> 与 <b>Fever API</b>。</p>
                    <p>配合现代移动端客户端，用户可以在 VPS 上随时同步几百个订阅源，并在离线模式下无网络阅读全文，实现真正跨设备的多端无缝阅读。</p>
                    <blockquote style="padding: 10px; border-left: 4px solid #E65100; background: #FFF3E0;">
                    “掌握你的阅读源头，就是掌握你获取认知的第一步。”
                    </blockquote>
                    <h4>离线阅读的核心优势</h4>
                    <ul>
                    <li><b>随时随地阅读：</b>即使在地铁、飞行模式或网络不稳定环境下，缓存的文章和插图均可毫秒级加载。</li>
                    <li><b>离线状态双向同步：</b>离线标记的已读、星标在网络恢复后自动提交到 FreshRSS 服务器。</li>
                    <li><b>零隐私泄露：</b>数据始终保存在你自己的 VPS 上，完全没有第三方追踪。</li>
                    </ul>
                """.trimIndent(),
                link = "https://techcrunch.com",
                imageUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=800&auto=format&fit=crop",
                isRead = false,
                isStarred = true
            ),
            ArticleEntity(
                id = "demo_art_2",
                feedId = "feed/sspai",
                feedTitle = "少数派 (sspai)",
                title = "手把手教你配置 FreshRSS 的 Google Reader API 移动端同步",
                author = "纪伯伦",
                publishedAt = now - 7200000,
                summaryHtml = "<p>详细讲解如何在 FreshRSS 设置中开启 API 功能并配置专用的 API 密码，配合 FreshFeed APP 体验如丝般顺滑的阅读同步。</p>",
                contentHtml = """
                    <p>很多小伙伴自建了 FreshRSS 后，不知道如何在 Android 手机端进行多端同步。其实只需简单三步：</p>
                    <ol>
                    <li><b>进入 FreshRSS 设置：</b>登录网页端，点击右上角齿轮 -> 用户管理 -> 个人信息。</li>
                    <li><b>启用 API 访问：</b>勾选「允许 API 访问（用于移动应用等客户端）」。</li>
                    <li><b>设置 API 密码：</b>在「API 密码」输入框中设定一个专用密码，并点击提交保存。</li>
                    </ol>
                    <p>随后打开本客户端，输入你的 VPS 地址（如 <code>https://rss.example.com</code>）以及你的用户名和刚刚设定的 API 密码，即可瞬间同步所有订阅列表和未读文章！</p>
                """.trimIndent(),
                link = "https://sspai.com",
                imageUrl = "https://images.unsplash.com/photo-1517842645767-c639042777db?w=800&auto=format&fit=crop",
                isRead = false,
                isStarred = false
            ),
            ArticleEntity(
                id = "demo_art_3",
                feedId = "feed/hacker_news",
                feedTitle = "Hacker News Top",
                title = "Show HN: Modern Kotlin Jetpack Compose RSS Engine with SQLite Cache",
                author = "kotlin_dev",
                publishedAt = now - 14400000,
                summaryHtml = "<p>A high-performance Android RSS reader engine built with Kotlin Coroutines, Room offline caching, and Material Design 3.</p>",
                contentHtml = """
                    <p>Today we are showcasing our open client design for self-hosted RSS servers:</p>
                    <p>Features include:</p>
                    <ul>
                    <li>Full Google Reader API compliance for FreshRSS and Nextcloud News</li>
                    <li>Offline-first reactive Room database layer with instant UI responsiveness</li>
                    <li>Automatic sync queue for offline read/starred actions</li>
                    <li>Comfortable typography with dynamic font size controls</li>
                    </ul>
                    <p>Feel free to test on your own FreshRSS VPS instance!</p>
                """.trimIndent(),
                link = "https://news.ycombinator.com",
                imageUrl = "https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=800&auto=format&fit=crop",
                isRead = false,
                isStarred = false
            ),
            ArticleEntity(
                id = "demo_art_4",
                feedId = "feed/v2ex",
                feedTitle = "V2EX 热门讨论",
                title = "大家平时自建 RSS 服务都选择哪款 VPS 机器？",
                author = "livid_fan",
                publishedAt = now - 28800000,
                summaryHtml = "<p>讨论 1C1G 配置的轻量 VPS 部署 FreshRSS + Docker 的资源消耗以及稳定性分享。</p>",
                contentHtml = """
                    <p>如题，目前使用一台 1 核 1G 内存的 VPS，通过 Docker 跑 FreshRSS 官方镜像，内存占用稳定在 60MB 左右，SQLite 存储数万篇离线文章依然秒开。</p>
                    <p>配合定时 CRON 任务更新订阅源，体验非常丝滑，配合手机端简直是数字断舍离利器。</p>
                """.trimIndent(),
                link = "https://v2ex.com",
                imageUrl = null,
                isRead = true,
                isStarred = true
            )
        )
        articleDao.insertArticles(articles)
    }
}
