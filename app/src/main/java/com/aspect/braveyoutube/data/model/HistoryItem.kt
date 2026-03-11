package com.aspect.braveyoutube.data.model

/**
 * Represents a single watch history entry.
 */
data class HistoryItem(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String = "",
    val thumbnailUrl: String,
    val watchedAt: Long = System.currentTimeMillis()
)
