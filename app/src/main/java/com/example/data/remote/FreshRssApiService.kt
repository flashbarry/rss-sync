package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class FreshRssApiService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val subscriptionAdapter = moshi.adapter(SubscriptionListResponse::class.java)
    private val unreadCountAdapter = moshi.adapter(UnreadCountResponse::class.java)
    private val streamContentsAdapter = moshi.adapter(StreamContentsResponse::class.java)
    private val userInfoAdapter = moshi.adapter(UserInfoResponse::class.java)
    private val quickAddAdapter = moshi.adapter(QuickAddResponse::class.java)

    /**
     * Normalizes the server URL to ensure it points to the FreshRSS Google Reader API.
     */
    fun normalizeApiBase(inputUrl: String): String {
        var trimmed = inputUrl.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        trimmed = trimmed.trimEnd('/')

        return if (trimmed.endsWith("/api/greader.php")) {
            trimmed
        } else if (trimmed.endsWith("/api/greader.php/")) {
            trimmed.trimEnd('/')
        } else {
            "$trimmed/api/greader.php"
        }
    }

    /**
     * FreshRSS Google Reader ClientLogin.
     * Takes server URL, username, and FreshRSS API password.
     */
    suspend fun login(
        serverUrl: String,
        username: String,
        apiPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiBase = normalizeApiBase(serverUrl)
        val loginUrl = "$apiBase/accounts/ClientLogin"

        val formBody = FormBody.Builder()
            .add("Email", username)
            .add("Passwd", apiPassword)
            .add("accountType", "HOSTED_OR_GOOGLE")
            .add("service", "reader")
            .add("source", "FreshFeed-Android")
            .build()

        val request = Request.Builder()
            .url(loginUrl)
            .post(formBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    401, 403 -> "认证失败：请检查用户名与 API 密码（需在 FreshRSS 个人设置中启用 API 并设置 API 密码）"
                    404 -> "未找到 Google Reader API 接口 (HTTP 404)。请确认 VPS 上的 FreshRSS 已开启 API 支持。"
                    502, 503, 504 -> "VPS 服务器网关错误 (${response.code})，请检查服务器反向代理或 PHP-FPM 状态。"
                    else -> "服务器返回错误代码 HTTP ${response.code}: $responseBody"
                }
                return@withContext Result.failure(IOException(errorMsg))
            }

            // Extract Auth=token from response text
            val authPattern = Pattern.compile("Auth=([^\r\n]+)")
            val matcher = authPattern.matcher(responseBody)
            if (matcher.find()) {
                val token = matcher.group(1)?.trim() ?: ""
                Result.success(token)
            } else {
                Result.failure(IOException("未在服务器响应中找到 Auth 令牌。返回内容: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(IOException("连接 VPS 失败: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Fetches user info to verify active session.
     */
    suspend fun getUserInfo(
        apiBase: String,
        authToken: String
    ): Result<UserInfoResponse> = withContext(Dispatchers.IO) {
        val url = "$apiBase/reader/api/0/user-info?output=json"
        executeGetJson(url, authToken, userInfoAdapter)
    }

    /**
     * Fetches list of subscriptions from FreshRSS.
     */
    suspend fun getSubscriptions(
        apiBase: String,
        authToken: String
    ): Result<SubscriptionListResponse> = withContext(Dispatchers.IO) {
        val url = "$apiBase/reader/api/0/subscription/list?output=json"
        executeGetJson(url, authToken, subscriptionAdapter)
    }

    /**
     * Fetches unread counts for all streams.
     */
    suspend fun getUnreadCounts(
        apiBase: String,
        authToken: String
    ): Result<UnreadCountResponse> = withContext(Dispatchers.IO) {
        val url = "$apiBase/reader/api/0/unread-count?output=json"
        executeGetJson(url, authToken, unreadCountAdapter)
    }

    /**
     * Fetches articles from a stream (e.g. "user/-/state/com.google/reading-list").
     */
    suspend fun getStreamContents(
        apiBase: String,
        authToken: String,
        streamId: String = "user/-/state/com.google/reading-list",
        count: Int = 100,
        excludeRead: Boolean = false,
        continuation: String? = null
    ): Result<StreamContentsResponse> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("$apiBase/reader/api/0/stream/contents/")
        sb.append(java.net.URLEncoder.encode(streamId, "UTF-8"))
        sb.append("?output=json&n=").append(count)
        if (excludeRead) {
            sb.append("&xt=user/-/state/com.google/read")
        }
        if (!continuation.isNullOrEmpty()) {
            sb.append("&c=").append(java.net.URLEncoder.encode(continuation, "UTF-8"))
        }

        executeGetJson(sb.toString(), authToken, streamContentsAdapter)
    }

    /**
     * Fetches an edit token needed for write operations.
     */
    suspend fun getActionToken(
        apiBase: String,
        authToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = "$apiBase/reader/api/0/token"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "GoogleLogin auth=$authToken")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val token = response.body?.string()?.trim() ?: ""
                Result.success(token)
            } else {
                Result.failure(IOException("获取操作 Token 失败: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark an item as read, unread, starred, or unstarred.
     */
    suspend fun editTag(
        apiBase: String,
        authToken: String,
        actionToken: String,
        itemId: String,
        addTag: String? = null,
        removeTag: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = "$apiBase/reader/api/0/edit-tag"
        val formBuilder = FormBody.Builder()
            .add("i", itemId)
            .add("T", actionToken)

        addTag?.let { formBuilder.add("a", it) }
        removeTag?.let { formBuilder.add("r", it) }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "GoogleLogin auth=$authToken")
            .post(formBuilder.build())
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(IOException("修改标签失败: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark all items in a stream or feed as read.
     */
    suspend fun markAllAsRead(
        apiBase: String,
        authToken: String,
        actionToken: String,
        streamId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = "$apiBase/reader/api/0/mark-all-as-read"
        val timestamp = (System.currentTimeMillis() * 1000).toString()

        val formBody = FormBody.Builder()
            .add("s", streamId)
            .add("ts", timestamp)
            .add("T", actionToken)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "GoogleLogin auth=$authToken")
            .post(formBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Quick add a new subscription by feed URL.
     */
    suspend fun quickAdd(
        apiBase: String,
        authToken: String,
        actionToken: String,
        feedUrl: String
    ): Result<QuickAddResponse> = withContext(Dispatchers.IO) {
        val url = "$apiBase/reader/api/0/subscription/quickadd?output=json"
        val formBody = FormBody.Builder()
            .add("quickadd", feedUrl)
            .add("T", actionToken)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "GoogleLogin auth=$authToken")
            .post(formBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val parsed = quickAddAdapter.fromJson(body)
                if (parsed != null) Result.success(parsed)
                else Result.failure(IOException("解析订阅结果失败"))
            } else {
                Result.failure(IOException("添加订阅失败: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun <T> executeGetJson(
        url: String,
        authToken: String,
        adapter: com.squareup.moshi.JsonAdapter<T>
    ): Result<T> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "GoogleLogin auth=$authToken")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val parsed = adapter.fromJson(body)
                if (parsed != null) {
                    Result.success(parsed)
                } else {
                    Result.failure(IOException("解析 JSON 失败: $body"))
                }
            } else {
                Result.failure(IOException("请求错误 HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
