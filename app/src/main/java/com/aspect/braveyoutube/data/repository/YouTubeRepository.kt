package com.aspect.braveyoutube.data.repository

import android.util.Log
import com.aspect.braveyoutube.BuildConfig
import com.aspect.braveyoutube.adblock.AdBlockEngine
import com.aspect.braveyoutube.data.api.RetrofitClient
import com.aspect.braveyoutube.data.api.YouTubePlaylistItem
import com.aspect.braveyoutube.data.api.YouTubeSearchItem
import com.aspect.braveyoutube.data.api.YouTubeVideoItem
import com.aspect.braveyoutube.data.model.SearchResult
import com.aspect.braveyoutube.data.model.SubscriptionChannel
import com.aspect.braveyoutube.data.model.VideoDetail
import com.aspect.braveyoutube.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * Repository that mediates between the YouTube API and the UI layer.
 * Applies ad-block filtering to results before returning them.
 */
class YouTubeRepository(
    private val adBlockEngine: AdBlockEngine = AdBlockEngine()
) {
    private val api = RetrofitClient.apiService
    private val apiKey = BuildConfig.YOUTUBE_API_KEY

    /** Search YouTube and filter out ad/promoted content */
    suspend fun searchVideos(query: String, pageToken: String? = null): SearchResult =
        withContext(Dispatchers.IO) {
            try {
                val response = api.searchVideos(
                    query = query,
                    pageToken = pageToken,
                    apiKey = apiKey
                )
                val videos = response.items
                    .filter { it.id.videoId != null }
                    .map { it.toVideoItem() }
                    .filter { !adBlockEngine.isPromotedContent(it) }

                SearchResult(videos, response.nextPageToken)
            } catch (e: Exception) {
                SearchResult(emptyList())
            }
        }

    /** Get trending/popular videos */
    suspend fun getTrendingVideos(categoryId: String? = null, pageToken: String? = null, regionCode: String = "US"): SearchResult =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getTrendingVideos(
                    categoryId = categoryId,
                    pageToken = pageToken,
                    regionCode = regionCode,
                    apiKey = apiKey
                )
                val videos = response.items
                    .map { it.toVideoItem() }
                    .filter { !adBlockEngine.isPromotedContent(it) }

                SearchResult(videos, response.nextPageToken)
            } catch (e: Exception) {
                SearchResult(emptyList())
            }
        }

    /** Get details for a specific video */
    suspend fun getVideoDetail(videoId: String): VideoDetail? =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getVideoDetails(
                    videoId = videoId,
                    apiKey = apiKey
                )
                response.items.firstOrNull()?.toVideoDetail()
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Get related videos using keyword search (relatedToVideoId is deprecated).
     * Accepts pre-fetched title + channelId to avoid redundant API calls.
     */
    suspend fun getRelatedVideos(
        videoId: String,
        title: String = "",
        channelId: String = ""
    ): List<VideoItem> =
        withContext(Dispatchers.IO) {
            try {
                val allVideos = mutableListOf<VideoItem>()

                // Step 1: Search by keywords from the title
                val keywords = title
                    .replace(Regex("[^\\w\\s]"), "")
                    .split("\\s+".toRegex())
                    .filter { it.length > 3 }
                    .take(4)
                    .joinToString(" ")
                if (keywords.isNotBlank()) {
                    try {
                        val searchResult = api.searchVideos(
                            query = keywords,
                            maxResults = 15,
                            apiKey = apiKey
                        )
                        allVideos += searchResult.items
                            .filter { it.id.videoId != null && it.id.videoId != videoId }
                            .map { it.toVideoItem() }
                    } catch (_: Exception) { }
                }

                // Step 2: Also get more videos from the same channel
                if (channelId.isNotBlank()) {
                    try {
                        val channelResult = api.searchChannel(
                            channelId = channelId,
                            maxResults = 8,
                            apiKey = apiKey
                        )
                        allVideos += channelResult.items
                            .filter { it.id.videoId != null && it.id.videoId != videoId }
                            .map { it.toVideoItem() }
                    } catch (_: Exception) { }
                }

                // Fallback: if both were empty, search by title directly
                if (allVideos.isEmpty() && title.isNotBlank()) {
                    try {
                        val fallback = api.searchVideos(
                            query = title.take(60),
                            maxResults = 10,
                            apiKey = apiKey
                        )
                        allVideos += fallback.items
                            .filter { it.id.videoId != null && it.id.videoId != videoId }
                            .map { it.toVideoItem() }
                    } catch (_: Exception) { }
                }

                // Merge, deduplicate, filter
                val filtered = allVideos
                    .distinctBy { it.id }
                    .filter { !adBlockEngine.isPromotedContent(it) }
                    .take(20)

                // Step 3: Enrich with view counts + duration via batch video details
                if (filtered.isNotEmpty()) {
                    try {
                        val ids = filtered.map { it.id }.joinToString(",")
                        val detailsResponse = api.getVideoDetails(videoId = ids, apiKey = apiKey)
                        val detailsMap = detailsResponse.items.associate { it.id to it }
                        filtered.map { video ->
                            val detail = detailsMap[video.id]
                            if (detail != null) {
                                video.copy(
                                    duration = detail.contentDetails?.duration?.toReadableDuration() ?: video.duration,
                                    viewCount = detail.statistics?.viewCount?.formatCount() ?: video.viewCount
                                )
                            } else video
                        }
                    } catch (_: Exception) {
                        filtered // Return without enrichment if details fail
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("YTRepo", "getRelatedVideos failed: ${e.message}")
                emptyList()
            }
        }

    // ── Authenticated (OAuth) methods ─────────────────────────

    /** Get user's subscriptions list */
    suspend fun getMySubscriptions(accessToken: String): List<SubscriptionChannel> =
        withContext(Dispatchers.IO) {
            try {
                val allSubs = mutableListOf<SubscriptionChannel>()
                var pageToken: String? = null
                // Fetch up to 3 pages (150 subscriptions)
                repeat(3) {
                    val response = api.getMySubscriptions(
                        authHeader = "Bearer $accessToken",
                        pageToken = pageToken,
                        apiKey = apiKey
                    )
                    allSubs += response.items.map { sub ->
                        SubscriptionChannel(
                            channelId = sub.snippet.resourceId.channelId ?: "",
                            title = sub.snippet.title,
                            thumbnailUrl = sub.snippet.thumbnails?.medium?.url
                                ?: sub.snippet.thumbnails?.default?.url ?: ""
                        )
                    }
                    pageToken = response.nextPageToken
                    if (pageToken == null) return@repeat
                }
                allSubs.filter { it.channelId.isNotBlank() }
            } catch (e: Exception) {
                Log.e("YTRepo", "getMySubscriptions failed: ${e.message}")
                emptyList()
            }
        }

    /** Get recent videos from subscribed channels for personalized feed */
    suspend fun getSubscriptionFeed(accessToken: String, maxChannels: Int = 15): SearchResult =
        withContext(Dispatchers.IO) {
            try {
                val subs = getMySubscriptions(accessToken)
                if (subs.isEmpty()) return@withContext SearchResult(emptyList())

                // Pick random subset of channels for variety
                val selectedSubs = subs.shuffled().take(maxChannels)

                // Fetch recent videos from each channel in parallel
                val allVideos = supervisorScope {
                    selectedSubs.map { sub ->
                        async {
                            try {
                                val response = api.searchChannel(
                                    channelId = sub.channelId,
                                    maxResults = 3,
                                    apiKey = apiKey
                                )
                                response.items
                                    .filter { it.id.videoId != null }
                                    .map { it.toVideoItem() }
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll().flatten()
                }

                val filtered = allVideos
                    .filter { !adBlockEngine.isPromotedContent(it) }
                    .distinctBy { it.id }
                    .shuffled()

                SearchResult(filtered)
            } catch (e: Exception) {
                Log.e("YTRepo", "getSubscriptionFeed failed: ${e.message}")
                SearchResult(emptyList())
            }
        }

    /** Get user's liked videos */
    suspend fun getMyLikedVideos(accessToken: String): List<VideoItem> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getMyLikedVideos(
                    authHeader = "Bearer $accessToken",
                    apiKey = apiKey
                )
                response.items
                    .map { it.toVideoItem() }
                    .filter { !adBlockEngine.isPromotedContent(it) }
            } catch (e: Exception) {
                Log.e("YTRepo", "getMyLikedVideos failed: ${e.message}")
                emptyList()
            }
        }
}

// ── Extension mappers ────────────────────────────────────────────

private fun YouTubeSearchItem.toVideoItem(): VideoItem {
    return VideoItem(
        id = id.videoId ?: "",
        title = snippet.title,
        channelName = snippet.channelTitle,
        channelId = snippet.channelId,
        thumbnailUrl = if (!id.videoId.isNullOrBlank()) "https://i.ytimg.com/vi/${id.videoId}/hq720.jpg" else (snippet.thumbnails?.high?.url
            ?: snippet.thumbnails?.medium?.url
            ?: snippet.thumbnails?.default?.url ?: ""),
        publishedAt = snippet.publishedAt,
        description = snippet.description,
        isShort = isLikelyShort(snippet.title, snippet.description)
    )
}

private fun YouTubeVideoItem.toVideoItem(): VideoItem {
    return VideoItem(
        id = id,
        title = snippet.title,
        channelName = snippet.channelTitle,
        channelId = snippet.channelId,
        thumbnailUrl = if (id.isNotBlank()) "https://i.ytimg.com/vi/$id/hq720.jpg" else (snippet.thumbnails?.high?.url
            ?: snippet.thumbnails?.medium?.url
            ?: snippet.thumbnails?.default?.url ?: ""),
        duration = contentDetails?.duration?.toReadableDuration() ?: "",
        viewCount = statistics?.viewCount?.formatCount() ?: "",
        publishedAt = snippet.publishedAt,
        description = snippet.description,
        isShort = isLikelyShort(snippet.title, snippet.description)
            || (contentDetails?.duration?.isShortDuration() == true)
    )
}

private fun YouTubeVideoItem.toVideoDetail(): VideoDetail {
    return VideoDetail(
        id = id,
        title = snippet.title,
        channelName = snippet.channelTitle,
        channelId = snippet.channelId,
        description = snippet.description,
        viewCount = statistics?.viewCount?.toLongOrNull() ?: 0,
        likeCount = statistics?.likeCount?.toLongOrNull() ?: 0,
        publishedAt = snippet.publishedAt,
        duration = contentDetails?.duration?.toReadableDuration() ?: ""
    )
}

/** Convert ISO 8601 duration "PT4M13S" to "4:13" */
private fun String.toReadableDuration(): String {
    val pattern = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
    val matcher = pattern.matcher(this)
    if (!matcher.matches()) return this

    val hours = matcher.group(1)?.toIntOrNull() ?: 0
    val minutes = matcher.group(2)?.toIntOrNull() ?: 0
    val seconds = matcher.group(3)?.toIntOrNull() ?: 0

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Check if duration is < 60 seconds (likely a Short) */
private fun String.isShortDuration(): Boolean {
    val pattern = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
    val matcher = pattern.matcher(this)
    if (!matcher.matches()) return false
    val hours = matcher.group(1)?.toIntOrNull() ?: 0
    val minutes = matcher.group(2)?.toIntOrNull() ?: 0
    val seconds = matcher.group(3)?.toIntOrNull() ?: 0
    return hours == 0 && minutes == 0 && seconds < 61
}

/** Format "1234567" to "1.2M" */
private fun String.formatCount(): String {
    val count = this.toLongOrNull() ?: return this
    return when {
        count >= 1_000_000_000 -> "%.1fB".format(count / 1_000_000_000.0)
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

private fun isLikelyShort(title: String, description: String): Boolean {
    val lower = "$title $description".lowercase()
    return lower.contains("#shorts") || lower.contains("#short")
}
