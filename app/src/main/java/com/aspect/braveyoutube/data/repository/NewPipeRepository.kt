package com.aspect.braveyoutube.data.repository

import android.util.Log
import com.aspect.braveyoutube.adblock.AdBlockEngine
import com.aspect.braveyoutube.data.model.SearchResult
import com.aspect.braveyoutube.data.model.VideoDetail
import com.aspect.braveyoutube.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.Page
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Repository that uses NewPipe Extractor for ALL YouTube data.
 * 
 * ✅ NO API KEY required
 * ✅ NO quota limits  
 * ✅ UNLIMITED requests
 * ✅ Scrapes YouTube directly (same as NewPipe app with 10M+ installs)
 */
class NewPipeRepository(
    private val adBlockEngine: AdBlockEngine = AdBlockEngine()
) {
    companion object {
        private const val TAG = "NewPipeRepo"
    }

    init {
        StreamExtractor.init() // Ensures NewPipe is initialized
    }

    // ── Trending / Home Feed ──

    /** Get trending videos — NO API KEY, NO QUOTA */
    suspend fun getTrendingVideos(regionCode: String = "US"): SearchResult =
        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val kioskList = service.kioskList
                val defaultKioskId = kioskList.defaultKioskId // "Trending"

                val kioskInfo = KioskInfo.getInfo(
                    service,
                    kioskList.getListLinkHandlerFactoryByType(defaultKioskId)
                        .fromId(defaultKioskId, null)
                        .url
                )

                val videos = kioskInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideoItem() }
                    .filter { !adBlockEngine.isPromotedContent(it) }

                Log.d(TAG, "Trending: ${videos.size} videos")
                SearchResult(videos)
            } catch (e: Exception) {
                Log.e(TAG, "getTrendingVideos failed: ${e.message}", e)
                SearchResult(emptyList())
            }
        }

    // ── Search ──

    /** Search YouTube — NO API KEY, NO QUOTA */
    suspend fun searchVideos(query: String): SearchResult =
        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val searchInfo = SearchInfo.getInfo(
                    service,
                    service.searchQHFactory.fromQuery(query)
                )

                val videos = searchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideoItem() }
                    .filter { !adBlockEngine.isPromotedContent(it) }

                Log.d(TAG, "Search '$query': ${videos.size} results")
                SearchResult(videos)
            } catch (e: Exception) {
                Log.e(TAG, "searchVideos failed: ${e.message}", e)
                SearchResult(emptyList())
            }
        }

    // ── Video Details ──

    /** Get full video details — NO API KEY, NO QUOTA */
    suspend fun getVideoDetail(videoId: String): VideoDetail? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://www.youtube.com/watch?v=$videoId"
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)

                VideoDetail(
                    id = videoId,
                    title = info.name ?: "",
                    channelName = info.uploaderName ?: "",
                    channelId = extractChannelId(info.uploaderUrl),
                    channelAvatarUrl = info.uploaderAvatars?.firstOrNull()?.url ?: "",
                    description = info.description?.content ?: "",
                    viewCount = info.viewCount,
                    likeCount = info.likeCount,
                    publishedAt = info.uploadDate?.offsetDateTime()?.toString()?.take(10) ?: "",
                    duration = formatDuration(info.duration)
                )
            } catch (e: Exception) {
                Log.e(TAG, "getVideoDetail failed: ${e.message}", e)
                null
            }
        }

    // ── Related Videos ──

    /** Get related videos for a video — NO API KEY, NO QUOTA */
    suspend fun getRelatedVideos(videoId: String): List<VideoItem> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://www.youtube.com/watch?v=$videoId"
                val info = StreamInfo.getInfo(ServiceList.YouTube, url)

                val videos = info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideoItem() }
                    .filter { !adBlockEngine.isPromotedContent(it) }

                Log.d(TAG, "Related videos for $videoId: ${videos.size}")
                videos.take(20)
            } catch (e: Exception) {
                Log.e(TAG, "getRelatedVideos failed: ${e.message}", e)
                emptyList()
            }
        }

    // ── Channel Videos ──

    /** Get recent videos from a channel — NO API KEY, NO QUOTA */
    suspend fun getChannelVideos(channelUrl: String, maxResults: Int = 10): List<VideoItem> =
        withContext(Dispatchers.IO) {
            try {
                val channelInfo = ChannelInfo.getInfo(ServiceList.YouTube, channelUrl)
                val tabs = channelInfo.tabs

                // Get the "Videos" tab
                val videosTab = tabs.firstOrNull { it.contentFilters.contains("videos") }
                    ?: tabs.firstOrNull()

                if (videosTab != null) {
                    val tabInfo = ChannelTabInfo.getInfo(ServiceList.YouTube, videosTab)
                    tabInfo.relatedItems
                        .filterIsInstance<StreamInfoItem>()
                        .take(maxResults)
                        .map { it.toVideoItem() }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "getChannelVideos failed: ${e.message}", e)
                emptyList()
            }
        }

    // ── Channel Info ──

    /** Get channel name and avatar from a channel URL — NO API KEY, NO QUOTA */
    suspend fun getChannelInfoData(channelUrl: String): ChannelInfoResult? =
        withContext(Dispatchers.IO) {
            try {
                val info = ChannelInfo.getInfo(ServiceList.YouTube, channelUrl)
                ChannelInfoResult(
                    id = extractChannelId(info.url),
                    name = info.name ?: "",
                    avatarUrl = info.avatars?.firstOrNull()?.url ?: "",
                    subscriberCount = info.subscriberCount
                )
            } catch (e: Exception) {
                Log.e(TAG, "getChannelInfoData failed for $channelUrl: ${e.message}", e)
                null
            }
        }

    data class ChannelInfoResult(
        val id: String,
        val name: String,
        val avatarUrl: String,
        val subscriberCount: Long = -1
    )

    // ── Shorts ──

    /** Search for YouTube Shorts — NO API KEY, NO QUOTA */
    suspend fun getShorts(regionCode: String = "US"): List<VideoItem> =
        withContext(Dispatchers.IO) {
            try {
                val service = ServiceList.YouTube
                val searchInfo = SearchInfo.getInfo(
                    service,
                    service.searchQHFactory.fromQuery("#shorts")
                )

                val shorts = searchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .filter { it.duration in 1..60 || it.name?.lowercase()?.contains("shorts") == true }
                    .map { it.toVideoItem().copy(isShort = true) }

                Log.d(TAG, "Shorts: ${shorts.size} results")
                shorts.take(30)
            } catch (e: Exception) {
                Log.e(TAG, "getShorts failed: ${e.message}", e)
                emptyList()
            }
        }

    // ── Mappers ──

    private fun StreamInfoItem.toVideoItem(): VideoItem {
        val videoId = try {
            url?.substringAfterLast("v=")?.substringBefore("&") ?: ""
        } catch (_: Exception) { "" }

        return VideoItem(
            id = videoId,
            title = name ?: "",
            channelName = uploaderName ?: "",
            channelId = extractChannelId(uploaderUrl),
            thumbnailUrl = if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/hq720.jpg" else (thumbnails?.firstOrNull()?.url ?: ""),
            channelAvatarUrl = try { uploaderAvatars.firstOrNull()?.url ?: "" } catch (_: Exception) { "" },
            duration = formatDuration(duration),
            viewCount = formatViewCount(viewCount),
            publishedAt = formatUploadDate(uploadDate, textualUploadDate),
            description = "",
            isShort = (duration > 0 && duration < 61) ||
                (name?.lowercase()?.contains("#shorts") == true)
        )
    }

    private fun extractChannelId(url: String?): String {
        if (url.isNullOrBlank()) return ""
        // https://www.youtube.com/channel/UCxxxxx
        return url.substringAfterLast("/channel/", "")
            .substringBefore("/")
            .substringBefore("?")
            .ifBlank {
                // https://www.youtube.com/@handle
                url.substringAfterLast("/@", "")
                    .substringBefore("/")
                    .substringBefore("?")
            }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun formatViewCount(count: Long): String {
        if (count < 0) return ""
        return when {
            count >= 1_000_000_000 -> "%.1fB".format(count / 1_000_000_000.0)
            count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
            count >= 1_000 -> "%.1fK".format(count / 1_000.0)
            else -> count.toString()
        }
    }

    private fun formatUploadDate(
        uploadDate: org.schabi.newpipe.extractor.localization.DateWrapper?,
        textualDate: String?
    ): String {
        // Prefer textual like "2 days ago"
        if (!textualDate.isNullOrBlank()) return textualDate
        // Fall back to date wrapper
        try {
            val date = uploadDate?.offsetDateTime()
            if (date != null) return date.toString().take(10)
        } catch (_: Exception) { }
        return ""
    }
}
