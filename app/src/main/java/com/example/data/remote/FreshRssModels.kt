package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubscriptionListResponse(
    @Json(name = "subscriptions") val subscriptions: List<SubscriptionDto>? = null
)

@JsonClass(generateAdapter = true)
data class SubscriptionDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "categories") val categories: List<CategoryDto>? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "htmlUrl") val htmlUrl: String? = null,
    @Json(name = "iconUrl") val iconUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class CategoryDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "label") val label: String? = null
)

@JsonClass(generateAdapter = true)
data class UnreadCountResponse(
    @Json(name = "max") val max: Int? = null,
    @Json(name = "unreadcounts") val unreadcounts: List<UnreadCountDto>? = null
)

@JsonClass(generateAdapter = true)
data class UnreadCountDto(
    @Json(name = "id") val id: String,
    @Json(name = "count") val count: Int,
    @Json(name = "newestItemTimestampUsec") val newestItemTimestampUsec: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamContentsResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "items") val items: List<StreamItemDto>? = null,
    @Json(name = "continuation") val continuation: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamItemDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String? = null,
    @Json(name = "published") val published: Long? = null,
    @Json(name = "updated") val updated: Long? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "alternate") val alternate: List<LinkDto>? = null,
    @Json(name = "canonical") val canonical: List<LinkDto>? = null,
    @Json(name = "summary") val summary: ContentDto? = null,
    @Json(name = "content") val content: ContentDto? = null,
    @Json(name = "categories") val categories: List<String>? = null,
    @Json(name = "origin") val origin: OriginDto? = null
)

@JsonClass(generateAdapter = true)
data class LinkDto(
    @Json(name = "href") val href: String? = null
)

@JsonClass(generateAdapter = true)
data class ContentDto(
    @Json(name = "content") val content: String? = null,
    @Json(name = "direction") val direction: String? = null
)

@JsonClass(generateAdapter = true)
data class OriginDto(
    @Json(name = "streamId") val streamId: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "htmlUrl") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class UserInfoResponse(
    @Json(name = "userId") val userId: String? = null,
    @Json(name = "userName") val userName: String? = null,
    @Json(name = "userEmail") val userEmail: String? = null
)

@JsonClass(generateAdapter = true)
data class QuickAddResponse(
    @Json(name = "query") val query: String? = null,
    @Json(name = "numResults") val numResults: Int? = null,
    @Json(name = "streamId") val streamId: String? = null,
    @Json(name = "streamName") val streamName: String? = null
)
