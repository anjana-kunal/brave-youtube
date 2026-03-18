package com.aspect.braveyoutube.data.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Retrofit service interface for YouTube Data API v3.
 * Provides native API access — no WebView needed.
 */
interface YouTubeApiService {

    @GET("search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 20,
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String? = null
    ): YouTubeSearchResponse

    @GET("videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("id") videoId: String,
        @Query("key") apiKey: String? = null
    ): YouTubeVideosResponse

    @GET("videos")
    suspend fun getTrendingVideos(
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("chart") chart: String = "mostPopular",
        @Query("regionCode") regionCode: String = "US",
        @Query("maxResults") maxResults: Int = 25,
        @Query("videoCategoryId") categoryId: String? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String? = null
    ): YouTubeVideosResponse

    @GET("search")
    suspend fun getRelatedVideos(
        @Query("part") part: String = "snippet",
        @Query("relatedToVideoId") videoId: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 15,
        @Query("key") apiKey: String? = null
    ): YouTubeSearchResponse

    // ── Authenticated endpoints (require OAuth access token) ──

    /** Get user's subscriptions */
    @GET("subscriptions")
    suspend fun getMySubscriptions(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String? = null
    ): YouTubeSubscriptionResponse

    /** Get channel details (for upload playlist ID) */
    @GET("channels")
    suspend fun getChannelDetails(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("id") channelId: String,
        @Query("key") apiKey: String? = null
    ): YouTubeChannelResponse

    /** Get videos from a playlist (channel uploads, liked videos) */
    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 10,
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String? = null
    ): YouTubePlaylistItemsResponse

    /** Get user's liked videos */
    @GET("videos")
    suspend fun getMyLikedVideos(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("myRating") myRating: String = "like",
        @Query("maxResults") maxResults: Int = 20,
        @Query("key") apiKey: String? = null
    ): YouTubeVideosResponse

    /** Search within a specific channel */
    @GET("search")
    suspend fun searchChannel(
        @Query("part") part: String = "snippet",
        @Query("channelId") channelId: String,
        @Query("order") order: String = "date",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 5,
        @Query("key") apiKey: String? = null
    ): YouTubeSearchResponse
}
