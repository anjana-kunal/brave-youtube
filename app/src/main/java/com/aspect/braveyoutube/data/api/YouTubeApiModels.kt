package com.aspect.braveyoutube.data.api

import com.google.gson.annotations.SerializedName

/**
 * Retrofit response models for YouTube Data API v3.
 */

// ── Search endpoint response ──────────────────────────────────
data class YouTubeSearchResponse(
    @SerializedName("items") val items: List<YouTubeSearchItem> = emptyList(),
    @SerializedName("nextPageToken") val nextPageToken: String? = null
)

data class YouTubeSearchItem(
    @SerializedName("id") val id: YouTubeResourceId,
    @SerializedName("snippet") val snippet: YouTubeSnippet
)

data class YouTubeResourceId(
    @SerializedName("kind") val kind: String = "",
    @SerializedName("videoId") val videoId: String? = null,
    @SerializedName("channelId") val channelId: String? = null
)

// ── Videos endpoint response ──────────────────────────────────
data class YouTubeVideosResponse(
    @SerializedName("items") val items: List<YouTubeVideoItem> = emptyList(),
    @SerializedName("nextPageToken") val nextPageToken: String? = null
)

data class YouTubeVideoItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("snippet") val snippet: YouTubeSnippet = YouTubeSnippet(),
    @SerializedName("contentDetails") val contentDetails: YouTubeContentDetails? = null,
    @SerializedName("statistics") val statistics: YouTubeStatistics? = null
)

// ── Shared models ──────────────────────────────────────────────
data class YouTubeSnippet(
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("channelTitle") val channelTitle: String = "",
    @SerializedName("channelId") val channelId: String = "",
    @SerializedName("publishedAt") val publishedAt: String = "",
    @SerializedName("thumbnails") val thumbnails: YouTubeThumbnails? = null,
    @SerializedName("categoryId") val categoryId: String? = null
)

data class YouTubeThumbnails(
    @SerializedName("default") val default: YouTubeThumbnail? = null,
    @SerializedName("medium") val medium: YouTubeThumbnail? = null,
    @SerializedName("high") val high: YouTubeThumbnail? = null,
    @SerializedName("maxres") val maxres: YouTubeThumbnail? = null
)

data class YouTubeThumbnail(
    @SerializedName("url") val url: String = "",
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0
)

data class YouTubeContentDetails(
    @SerializedName("duration") val duration: String = ""  // ISO 8601 e.g. "PT4M13S"
)

data class YouTubeStatistics(
    @SerializedName("viewCount") val viewCount: String = "0",
    @SerializedName("likeCount") val likeCount: String = "0",
    @SerializedName("commentCount") val commentCount: String = "0"
)

// ── Subscriptions endpoint response ───────────────────────────
data class YouTubeSubscriptionResponse(
    @SerializedName("items") val items: List<YouTubeSubscriptionItem> = emptyList(),
    @SerializedName("nextPageToken") val nextPageToken: String? = null
)

data class YouTubeSubscriptionItem(
    @SerializedName("snippet") val snippet: YouTubeSubscriptionSnippet
)

data class YouTubeSubscriptionSnippet(
    @SerializedName("title") val title: String = "",
    @SerializedName("resourceId") val resourceId: YouTubeResourceId = YouTubeResourceId(),
    @SerializedName("thumbnails") val thumbnails: YouTubeThumbnails? = null
)

// ── Channels endpoint response ────────────────────────────────
data class YouTubeChannelResponse(
    @SerializedName("items") val items: List<YouTubeChannelItem> = emptyList()
)

data class YouTubeChannelItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("snippet") val snippet: YouTubeSnippet = YouTubeSnippet(),
    @SerializedName("contentDetails") val contentDetails: YouTubeChannelContentDetails? = null
)

data class YouTubeChannelContentDetails(
    @SerializedName("relatedPlaylists") val relatedPlaylists: YouTubeRelatedPlaylists? = null
)

data class YouTubeRelatedPlaylists(
    @SerializedName("uploads") val uploads: String = "",
    @SerializedName("likes") val likes: String = ""
)

// ── Playlist items response ───────────────────────────────────
data class YouTubePlaylistItemsResponse(
    @SerializedName("items") val items: List<YouTubePlaylistItem> = emptyList(),
    @SerializedName("nextPageToken") val nextPageToken: String? = null
)

data class YouTubePlaylistItem(
    @SerializedName("snippet") val snippet: YouTubePlaylistItemSnippet
)

data class YouTubePlaylistItemSnippet(
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("channelTitle") val channelTitle: String = "",
    @SerializedName("channelId") val channelId: String = "",
    @SerializedName("publishedAt") val publishedAt: String = "",
    @SerializedName("thumbnails") val thumbnails: YouTubeThumbnails? = null,
    @SerializedName("resourceId") val resourceId: YouTubeResourceId = YouTubeResourceId()
)
