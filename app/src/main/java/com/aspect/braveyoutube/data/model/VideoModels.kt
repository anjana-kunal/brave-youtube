package com.aspect.braveyoutube.data.model

/**
 * Data models for YouTube video data.
 * These map to YouTube Data API v3 responses.
 */

data class VideoItem(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val channelAvatarUrl: String = "",
    val duration: String = "",
    val viewCount: String = "",
    val publishedAt: String = "",
    val description: String = "",
    val isAd: Boolean = false,       // Flagged by our ad-block engine
    val isShort: Boolean = false     // YouTube Shorts detection
)

data class SearchResult(
    val videos: List<VideoItem>,
    val nextPageToken: String? = null
)

data class VideoDetail(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val channelAvatarUrl: String = "",
    val description: String,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val publishedAt: String = "",
    val duration: String = "",
    val streamUrl: String = "",       // Direct stream URL extracted
    val relatedVideos: List<VideoItem> = emptyList()
)

data class ChannelInfo(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val subscriberCount: String = "",
    val isVerified: Boolean = false
)

/** Categories for the home feed */
enum class FeedCategory(val displayName: String, val apiParam: String) {
    TRENDING("Trending", "0"),
    MUSIC("Music", "10"),
    GAMING("Gaming", "20"),
    NEWS("News", "25"),
    SPORTS("Sports", "17"),
    ENTERTAINMENT("Entertainment", "24"),
    EDUCATION("Education", "27"),
    SCIENCE("Science & Tech", "28")
}
