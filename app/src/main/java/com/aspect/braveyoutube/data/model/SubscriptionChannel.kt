package com.aspect.braveyoutube.data.model

/**
 * Represents a channel the user is subscribed to on YouTube.
 */
data class SubscriptionChannel(
    val channelId: String,
    val title: String,
    val thumbnailUrl: String = ""
)
