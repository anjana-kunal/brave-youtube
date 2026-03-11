package com.aspect.braveyoutube.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Extracts playable video stream URLs from YouTube using the NewPipe Extractor library.
 *
 * NewPipe Extractor handles all of YouTube's protections:
 * - Signature cipher decryption
 * - n-parameter throttling bypass
 * - Multiple client type fallbacks (Android, iOS, Web)
 *
 * This is the same library used by the NewPipe app (10M+ installs) to play YouTube videos
 * without any proxy servers or third-party APIs.
 */
object StreamExtractor {

    private const val TAG = "StreamExtractor"
    private var initialized = false

    /** Represents a selectable video quality option. */
    data class QualityOption(
        val label: String,          // "Auto", "1080p", "720p", etc.
        val videoUrl: String,
        val audioUrl: String? = null, // non-null for video-only streams that need separate audio
        val height: Int = 0,
        val isHls: Boolean = false
    )

    data class StreamInfo(
        val videoUrl: String?,
        val audioUrl: String?,
        val title: String = "",
        val duration: Long = 0,
        val qualities: List<QualityOption> = emptyList()
    )

    /**
     * Initialize NewPipe Extractor with our custom HTTP downloader.
     * Must be called before any extraction.
     */
    @Synchronized
    fun init() {
        if (!initialized) {
            NewPipe.init(NewPipeDownloader.instance)
            YoutubeParsingHelper.setConsentAccepted(true)
            initialized = true
            Log.d(TAG, "NewPipe Extractor initialized")
        }
    }

    suspend fun extractStream(videoId: String): StreamInfo = withContext(Dispatchers.IO) {
        init()

        Log.d(TAG, "Extracting stream for videoId: $videoId")

        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(url)
            extractor.fetchPage()

            val title = try { extractor.name ?: "" } catch (_: Exception) { "" }
            val duration = try { extractor.length } catch (_: Exception) { 0L }

            Log.d(TAG, "Video: '$title', duration: ${duration}s")

            // ── Collect all available streams ──
            val hlsUrl = try { extractor.hlsUrl } catch (_: Exception) { null }

            val muxedStreams: List<VideoStream> = try {
                extractor.videoStreams?.filter { !it.isVideoOnly } ?: emptyList()
            } catch (_: Exception) { emptyList() }

            val videoOnlyStreams: List<VideoStream> = try {
                extractor.videoOnlyStreams ?: emptyList()
            } catch (_: Exception) { emptyList() }

            val audioStreams = try {
                extractor.audioStreams ?: emptyList()
            } catch (_: Exception) { emptyList() }

            Log.d(TAG, "Streams: HLS=${!hlsUrl.isNullOrBlank()}, muxed=${muxedStreams.size}, videoOnly=${videoOnlyStreams.size}, audio=${audioStreams.size}")

            // ── Best audio URL (for pairing with video-only streams) ──
            var bestAudioUrl: String? = null
            var bestAudioBitrate = 0
            for (stream in audioStreams) {
                val bitrate = try { stream.averageBitrate } catch (_: Exception) { 0 }
                val streamUrl = try { stream.content } catch (_: Exception) { null }
                if (streamUrl != null && bitrate > bestAudioBitrate) {
                    bestAudioUrl = streamUrl
                    bestAudioBitrate = bitrate
                }
            }

            // ── Build quality options list ──
            val qualities = mutableListOf<QualityOption>()
            val seenHeights = mutableSetOf<Int>()

            // HLS → "Auto" quality (ExoPlayer handles adaptive bitrate)
            if (!hlsUrl.isNullOrBlank()) {
                qualities.add(QualityOption("Auto", hlsUrl, null, 0, true))
            }

            // Muxed streams (video + audio in one URL)
            for (stream in muxedStreams) {
                val h = parseHeight(stream)
                val u = try { stream.content } catch (_: Exception) { null }
                if (u != null && h > 0 && seenHeights.add(h)) {
                    qualities.add(QualityOption("${h}p", u, null, h, false))
                }
            }

            // Video-only streams (paired with best audio)
            for (stream in videoOnlyStreams) {
                val h = parseHeight(stream)
                val u = try { stream.content } catch (_: Exception) { null }
                if (u != null && h > 0 && seenHeights.add(h)) {
                    qualities.add(QualityOption("${h}p", u, bestAudioUrl, h, false))
                }
            }

            // Sort: Auto first, then by height descending
            val sortedQualities = qualities.sortedWith(
                compareByDescending<QualityOption> { it.isHls }
                    .thenByDescending { it.height }
            )

            Log.d(TAG, "Quality options: ${sortedQualities.map { it.label }}")

            // ── Pick default stream (HLS > best muxed ≤720p > any) ──
            val defaultVideoUrl: String?
            val defaultAudioUrl: String?

            if (!hlsUrl.isNullOrBlank()) {
                defaultVideoUrl = hlsUrl
                defaultAudioUrl = null
                Log.d(TAG, "Default: HLS")
            } else {
                // Best muxed ≤ 720p
                val bestMuxed = muxedStreams
                    .mapNotNull { s ->
                        val h = parseHeight(s)
                        val u = try { s.content } catch (_: Exception) { null }
                        if (u != null && h in 1..720) Pair(u, h) else null
                    }
                    .maxByOrNull { it.second }

                if (bestMuxed != null) {
                    defaultVideoUrl = bestMuxed.first
                    defaultAudioUrl = null
                    Log.d(TAG, "Default: muxed ${bestMuxed.second}p")
                } else {
                    // Video-only ≤ 720p + audio
                    val bestVO = videoOnlyStreams
                        .mapNotNull { s ->
                            val h = parseHeight(s)
                            val u = try { s.content } catch (_: Exception) { null }
                            if (u != null && h in 1..720) Pair(u, h) else null
                        }
                        .maxByOrNull { it.second }

                    if (bestVO != null) {
                        defaultVideoUrl = bestVO.first
                        defaultAudioUrl = bestAudioUrl
                        Log.d(TAG, "Default: video-only ${bestVO.second}p + audio")
                    } else {
                        // Any available stream
                        val anyMuxed = muxedStreams.firstNotNullOfOrNull { s ->
                            try { s.content } catch (_: Exception) { null }
                        }
                        val anyVO = videoOnlyStreams.firstNotNullOfOrNull { s ->
                            try { s.content } catch (_: Exception) { null }
                        }

                        defaultVideoUrl = anyMuxed ?: anyVO
                        defaultAudioUrl = if (anyMuxed != null) null else bestAudioUrl
                        Log.d(TAG, "Default: fallback (muxed=${ anyMuxed != null}, vo=${ anyVO != null})")
                    }
                }
            }

            return@withContext StreamInfo(
                videoUrl = defaultVideoUrl,
                audioUrl = defaultAudioUrl,
                title = title,
                duration = duration,
                qualities = sortedQualities
            )
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            return@withContext StreamInfo(videoUrl = null, audioUrl = null)
        }
    }

    private fun parseHeight(stream: VideoStream): Int {
        return try {
            stream.resolution?.replace("p.*".toRegex(), "")?.toIntOrNull() ?: 0
        } catch (_: Exception) { 0 }
    }
}
