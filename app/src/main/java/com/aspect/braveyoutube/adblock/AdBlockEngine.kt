package com.aspect.braveyoutube.adblock

import android.util.Log
import com.aspect.braveyoutube.data.model.VideoItem

/**
 * Native Ad-Block Engine inspired by Brave Browser's adblock-rust.
 *
 * Since this is a NATIVE app (not a WebView), ad blocking works fundamentally differently:
 * 
 * WebView app:  Intercept HTTP requests → block ad URLs → inject CSS/JS
 * Native app:   Filter API results → skip promoted content → ExoPlayer plays ad-free streams
 *
 * Because we use ExoPlayer to play direct video streams, YouTube's client-side
 * ad insertion (pre-roll, mid-roll) is completely bypassed — the ads never load
 * since we never run YouTube's ad-serving JavaScript at all.
 *
 * This engine handles:
 * 1. Filtering promoted/sponsored videos from search & feed results
 * 2. Detecting and optionally hiding Shorts
 * 3. Blocking tracking URLs in network requests
 * 4. Tracking block statistics (like Brave Shields counter)
 */
class AdBlockEngine {

    // ── Stats (like Brave's Shields counter) ─────────────────────
    var adsBlocked: Int = 0
        private set
    var trackersBlocked: Int = 0
        private set
    var promotedContentFiltered: Int = 0
        private set

    // ── Blocked domains for any secondary network requests ───────
    private val blockedDomains = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "static.doubleclick.net",
        "tpc.googlesyndication.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "facebook.com",
        "connect.facebook.net",
        "analytics.twitter.com",
        "scorecardresearch.com",
        "quantserve.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "adsrvr.org",
        "moatads.com",
        "rubiconproject.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "criteo.net"
    )

    // ── Keywords that indicate promoted/ad content in titles/descriptions
    private val promotedKeywords = listOf(
        "sponsored", "#ad", "paid promotion", "paid partnership",
        "advertisement", "promoted"
    )

    // ── Channel IDs known to be ad/spam channels ─────────────────
    private val blockedChannelIds = mutableSetOf<String>()

    /**
     * Check if a video item looks like promoted/sponsored content.
     * In a native app, this is the primary "ad-blocking" — we filter
     * results before displaying them instead of blocking HTTP requests.
     */
    fun isPromotedContent(video: VideoItem): Boolean {
        // Already flagged
        if (video.isAd) {
            promotedContentFiltered++
            return true
        }

        // Check title/description for ad keywords
        val combinedText = "${video.title} ${video.description}".lowercase()
        for (keyword in promotedKeywords) {
            if (combinedText.contains(keyword.lowercase())) {
                promotedContentFiltered++
                Log.d(TAG, "Filtered promoted content: ${video.title}")
                return true
            }
        }

        // Check against blocked channels
        if (blockedChannelIds.contains(video.channelId)) {
            promotedContentFiltered++
            return true
        }

        return false
    }

    /**
     * Check if a network URL should be blocked (for any auxiliary requests).
     * With a native player, most ad URLs are never even encountered,
     * but this catches any tracking pixels or analytics calls.
     */
    fun shouldBlockUrl(url: String): Boolean {
        val host = try {
            android.net.Uri.parse(url).host?.lowercase() ?: return false
        } catch (_: Exception) {
            return false
        }

        // Check exact match
        if (blockedDomains.contains(host)) {
            trackersBlocked++
            return true
        }

        // Check parent domain match
        for (blocked in blockedDomains) {
            if (host.endsWith(".$blocked")) {
                trackersBlocked++
                return true
            }
        }

        // YouTube ad-specific URL patterns
        val adUrlPatterns = listOf(
            "/pagead/", "/ptracking", "/api/stats/ads",
            "/get_midroll_", "/youtubei/v1/player/ad_break"
        )
        for (pattern in adUrlPatterns) {
            if (url.contains(pattern)) {
                adsBlocked++
                return true
            }
        }

        return false
    }

    /**
     * Add a channel to the block list (user can block specific channels).
     */
    fun blockChannel(channelId: String) {
        blockedChannelIds.add(channelId)
    }

    fun unblockChannel(channelId: String) {
        blockedChannelIds.remove(channelId)
    }

    fun resetStats() {
        adsBlocked = 0
        trackersBlocked = 0
        promotedContentFiltered = 0
    }

    /**
     * Get total number of items blocked/filtered.
     */
    val totalBlocked: Int
        get() = adsBlocked + trackersBlocked + promotedContentFiltered

    companion object {
        private const val TAG = "BraveAdBlock"
    }
}
