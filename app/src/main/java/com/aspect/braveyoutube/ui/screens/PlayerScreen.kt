package com.aspect.braveyoutube.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.media3.common.Player
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.aspect.braveyoutube.data.repository.StreamExtractor
import com.aspect.braveyoutube.ui.HomeViewModel
import com.aspect.braveyoutube.ui.PlayerViewModel
import com.aspect.braveyoutube.ui.components.LoadingSpinner
import com.aspect.braveyoutube.ui.theme.YouTubeRed
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoId: String,
    exoPlayer: ExoPlayer,
    viewModel: PlayerViewModel = viewModel(),
    homeViewModel: HomeViewModel? = null,
    isStreamAlreadyLoaded: Boolean = false,
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit = {},
    onVideoInfoLoaded: (title: String, channel: String) -> Unit = { _, _ -> },
    onStreamLoaded: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as Activity
    val videoDetail by viewModel.videoDetail.collectAsState()
    val streamInfo by viewModel.streamInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val relatedVideos by viewModel.relatedVideos.collectAsState()
    val configuration = LocalConfiguration.current

    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var selectedQualityLabel by remember { mutableStateOf<String?>(null) }
    var isDisliked by remember { mutableStateOf(false) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    val autoplayEnabled by homeViewModel?.autoplayEnabled?.collectAsState() ?: remember { mutableStateOf(true) }
    val currentRelatedVideos by rememberUpdatedState(relatedVideos)
    val currentAutoplay by rememberUpdatedState(autoplayEnabled)
    val currentOnVideoClick by rememberUpdatedState(onVideoClick)
    val shortsData by (homeViewModel?.shortsVideos ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()
    val currentShorts by rememberUpdatedState(shortsData)
    val currentVideoId by rememberUpdatedState(videoId)

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (currentAutoplay) {
                        currentRelatedVideos.firstOrNull()?.let { nextVideo ->
                            currentOnVideoClick(nextVideo.id)
                        }
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Persisted like & subscribe state
    val likedVideoIds by homeViewModel?.likedVideoIds?.collectAsState() ?: remember { mutableStateOf(emptySet()) }
    val subscribedChannelIds by homeViewModel?.subscribedChannelIds?.collectAsState() ?: remember { mutableStateOf(emptySet()) }
    val isLiked = likedVideoIds.contains(videoId)
    val isSubscribed = videoDetail?.channelId?.let { subscribedChannelIds.contains(it) } ?: false

    // Double-tap seek state
    var seekDirection by remember { mutableIntStateOf(0) } // -1 = back, 1 = forward, 0 = none
    var seekTrigger by remember { mutableLongStateOf(0L) }

    // Auto-dismiss seek animation
    LaunchedEffect(seekTrigger) {
        if (seekDirection != 0) {
            delay(700)
            seekDirection = 0
        }
    }

    // Available quality options from stream info
    val qualities = streamInfo?.qualities ?: emptyList()

    // Play default stream when URL is available
    LaunchedEffect(streamInfo) {
        streamInfo?.let { info ->
            info.videoUrl?.let { url ->
                if (!isStreamAlreadyLoaded) {
                    playStream(exoPlayer, url, info.audioUrl)
                    onStreamLoaded(videoId)
                }
                // Mark current quality
                selectedQualityLabel = when {
                    info.qualities.any { it.isHls && it.videoUrl == url } -> "Auto"
                    else -> info.qualities.firstOrNull { it.videoUrl == url }?.label
                }
            }
        }
    }

    // Load video on first composition
    LaunchedEffect(videoId) {
        viewModel.loadVideo(videoId)
    }

    // Record to watch history and update mini-player info
    LaunchedEffect(videoDetail) {
        videoDetail?.let { detail ->
            homeViewModel?.addToWatchHistory(
                videoId = detail.id,
                title = detail.title,
                channelName = detail.channelName,
                thumbnailUrl = "https://img.youtube.com/vi/${detail.id}/hq720.jpg",
                channelId = detail.channelId
            )
            onVideoInfoLoaded(detail.title, detail.channelName)
        }
    }

    // Handle fullscreen toggle
    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            activity.requestedOrientation = if (videoAspectRatio < 1.0f) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-enter portrait fullscreen for Shorts
    LaunchedEffect(videoAspectRatio) {
        if (videoAspectRatio < 1.0f && !isFullscreen) {
            toggleFullscreen()
        }
    }

    // Handle quality selection
    fun onQualitySelected(quality: StreamExtractor.QualityOption) {
        val currentPos = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.isPlaying
        playStream(exoPlayer, quality.videoUrl, quality.audioUrl)
        exoPlayer.seekTo(currentPos)
        exoPlayer.playWhenReady = wasPlaying
        selectedQualityLabel = quality.label
        showQualitySheet = false
    }

    // Back press: exit fullscreen or minimize to mini-player
    BackHandler {
        if (isFullscreen) {
            toggleFullscreen()
        } else {
            onBackClick()
        }
    }

    // Cleanup on dispose (don't release player — shared for mini-player)
    DisposableEffect(Unit) {
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ── FULLSCREEN MODE ──
    if (isFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    val playerView = PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowPreviousButton(false)
                        setShowNextButton(false)
                        setShowFastForwardButton(false)
                        setShowRewindButton(false)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    // Wrap in FrameLayout with double-tap gesture detection
                    object : FrameLayout(ctx) {
                        private val gestureDetector = GestureDetector(ctx,
                            object : GestureDetector.SimpleOnGestureListener() {
                                override fun onDoubleTap(e: MotionEvent): Boolean {
                                    if (e.x < width / 2) {
                                        exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                        seekDirection = -1
                                    } else {
                                        exoPlayer.seekTo(exoPlayer.currentPosition + 10000)
                                        seekDirection = 1
                                    }
                                    seekTrigger = System.currentTimeMillis()
                                    return true
                                }

                                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                                    if (videoAspectRatio >= 1.0f) return false
                                    if (e1 == null) return false
                                    val deltaY = e2.y - e1.y
                                    val deltaX = e2.x - e1.x
                                    if (kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX)) {
                                        if (deltaY < -100 && velocityY < -100) {
                                            // Swipe Up -> Next
                                            val idx = currentShorts.indexOfFirst { it.id == currentVideoId }
                                            if (idx != -1 && idx + 1 < currentShorts.size) {
                                                currentOnVideoClick(currentShorts[idx + 1].id)
                                            } else if (currentRelatedVideos.isNotEmpty()) {
                                                currentOnVideoClick(currentRelatedVideos.first().id)
                                            }
                                            return true
                                        } else if (deltaY > 100 && velocityY > 100) {
                                            // Swipe Down -> Prev
                                            val idx = currentShorts.indexOfFirst { it.id == currentVideoId }
                                            if (idx > 0) {
                                                currentOnVideoClick(currentShorts[idx - 1].id)
                                            }
                                            return true
                                        }
                                    }
                                    return false
                                }
                            }
                        )
                        init {
                            addView(playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                        }
                        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                            gestureDetector.onTouchEvent(ev)
                            return super.dispatchTouchEvent(ev)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Seek feedback animation
            SeekFeedbackOverlay(seekDirection)

            // Top overlay: back + quality + exit fullscreen
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { toggleFullscreen() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Autoplay toggle
                    Text(
                        text = if (autoplayEnabled) "Auto ON" else "Auto OFF",
                        color = if (autoplayEnabled) YouTubeRed else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { homeViewModel?.toggleAutoplay(!autoplayEnabled) }
                            .padding(horizontal = 8.dp)
                    )
                    // Quality button
                    IconButton(onClick = { showQualitySheet = true }) {
                        Icon(Icons.Default.Tune, "Quality", tint = Color.White)
                    }
                    // Exit fullscreen
                    IconButton(onClick = { toggleFullscreen() }) {
                        Icon(Icons.Default.FullscreenExit, "Exit Fullscreen", tint = Color.White)
                    }
                }
            }
        }
    } else {
        // ── NORMAL MODE ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            // ── Video Player ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = configuration.screenHeightDp.dp * 0.65f)
                    .aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = false)
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val playerView = PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            setShowPreviousButton(false)
                            setShowNextButton(false)
                            setShowFastForwardButton(false)
                            setShowRewindButton(false)
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        // Wrap in FrameLayout with double-tap gesture detection
                        object : FrameLayout(ctx) {
                            private val gestureDetector = GestureDetector(ctx,
                                object : GestureDetector.SimpleOnGestureListener() {
                                    override fun onDoubleTap(e: MotionEvent): Boolean {
                                        if (e.x < width / 2) {
                                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                            seekDirection = -1
                                        } else {
                                            exoPlayer.seekTo(exoPlayer.currentPosition + 10000)
                                            seekDirection = 1
                                        }
                                        seekTrigger = System.currentTimeMillis()
                                        return true
                                    }

                                    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                                        if (videoAspectRatio >= 1.0f) return false
                                        if (e1 == null) return false
                                        val deltaY = e2.y - e1.y
                                        val deltaX = e2.x - e1.x
                                        if (kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX)) {
                                            if (deltaY < -100 && velocityY < -100) {
                                                // Swipe Up -> Next
                                                val idx = currentShorts.indexOfFirst { it.id == currentVideoId }
                                                if (idx != -1 && idx + 1 < currentShorts.size) {
                                                    currentOnVideoClick(currentShorts[idx + 1].id)
                                                } else if (currentRelatedVideos.isNotEmpty()) {
                                                    currentOnVideoClick(currentRelatedVideos.first().id)
                                                }
                                                return true
                                            } else if (deltaY > 100 && velocityY > 100) {
                                                // Swipe Down -> Prev
                                                val idx = currentShorts.indexOfFirst { it.id == currentVideoId }
                                                if (idx > 0) {
                                                    currentOnVideoClick(currentShorts[idx - 1].id)
                                                }
                                                return true
                                            }
                                        }
                                        return false
                                    }
                                }
                            )
                            init {
                                addView(playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                            }
                            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                                gestureDetector.onTouchEvent(ev)
                                return super.dispatchTouchEvent(ev)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Seek feedback animation
                SeekFeedbackOverlay(seekDirection)

                // Top-left: back button
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Top-right: quality + fullscreen buttons
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Autoplay toggle
                    Text(
                        text = if (autoplayEnabled) "Auto ON" else "Auto OFF",
                        color = if (autoplayEnabled) YouTubeRed else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { homeViewModel?.toggleAutoplay(!autoplayEnabled) }
                            .padding(end = 4.dp)
                    )
                    
                    if (qualities.size > 1) {
                        IconButton(onClick = { showQualitySheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Quality",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(onClick = { toggleFullscreen() }) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Loading overlay
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingSpinner(color = Color.White)
                    }
                }
            }

            // ── Content below player ──
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Title ──
                item {
                    Text(
                        text = videoDetail?.title ?: "Loading...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)
                    )
                }

                // ── View count + date (tap for description) ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildString {
                                videoDetail?.let { d ->
                                    if (d.viewCount > 0) {
                                        append(formatViewCount(d.viewCount))
                                        append(" views")
                                    }
                                    if (d.publishedAt.isNotBlank()) {
                                        if (d.viewCount > 0) append("  ·  ")
                                        append(d.publishedAt.take(10))
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (isDescriptionExpanded) " ...less" else " ...more",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 12.sp
                        )
                    }
                }

                // ── Description (expandable) ──
                if (isDescriptionExpanded && videoDetail?.description?.isNotBlank() == true) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = videoDetail!!.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // ── Channel Row (YouTube-style) ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                videoDetail?.channelId?.let { onChannelClick(it) } 
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Channel avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!videoDetail?.channelAvatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = videoDetail?.channelAvatarUrl,
                                    contentDescription = videoDetail?.channelName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(
                                    text = videoDetail?.channelName?.firstOrNull()?.uppercase() ?: "?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Channel name
                        Text(
                            text = videoDetail?.channelName ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Subscribe button (pill) — toggles in-app subscription
                        Button(
                            onClick = {
                                videoDetail?.channelId?.let { chId ->
                                    homeViewModel?.toggleSubscribedChannel(chId)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSubscribed)
                                    MaterialTheme.colorScheme.surfaceVariant
                                else
                                    MaterialTheme.colorScheme.onBackground,
                                contentColor = if (isSubscribed)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.background
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (isSubscribed) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Subscribed", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            } else {
                                Text("Subscribe", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // ── Action Buttons (YouTube-style horizontal scrolling pills) ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Like / Dislike combined pill
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.height(36.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            homeViewModel?.toggleLikedVideo(videoId)
                                            isDisliked = false
                                        }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                        "Like",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isLiked) YouTubeRed else MaterialTheme.colorScheme.onBackground
                                    )
                                    videoDetail?.let {
                                        if (it.likeCount > 0) {
                                            Text(
                                                formatCompactNumber(it.likeCount),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(20.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                )
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            isDisliked = !isDisliked
                                            if (isDisliked && isLiked) {
                                                homeViewModel?.toggleLikedVideo(videoId)
                                            }
                                        }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isDisliked) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                                        "Dislike",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isDisliked) YouTubeRed else MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }

                        // Share pill
                        ActionPill(Icons.Default.Share, "Share") {
                            val shareIntent = Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "https://youtu.be/$videoId")
                                }, "Share"
                            )
                            context.startActivity(shareIntent)
                        }
                        // Download pill
                        ActionPill(Icons.Default.Download, "Download")
                        // Save pill
                        ActionPill(Icons.AutoMirrored.Filled.PlaylistAdd, "Save")
                    }
                }

                // Divider
                item {
                    Divider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // ── Comments Preview ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* Open comments */ }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Comments",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.UnfoldMore,
                            contentDescription = "Expand",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "Add a comment...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }

                // ── Related Videos (full-width cards like real YouTube) ──
                if (relatedVideos.isNotEmpty()) {
                    items(relatedVideos, key = { "rel_${it.id}" }) { video ->
                        RelatedVideoCard(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            onChannelClick = { onChannelClick(video.channelId) }
                        )
                    }
                } else if (!isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingSpinner(color = YouTubeRed, size = 24.dp)
                        }
                    }
                }

                if (error != null) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // Bottom spacing
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // ── Quality Picker Bottom Sheet ──
    if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = "Quality",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                qualities.forEach { quality ->
                    val isSelected = quality.label == selectedQualityLabel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQualitySelected(quality) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (quality.isHls) Icons.Default.AutoAwesome
                                else Icons.Default.HighQuality,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (isSelected) YouTubeRed
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = quality.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) YouTubeRed
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (quality.audioUrl != null) {
                                    Text(
                                        text = "HD • Video + Audio",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (quality.isHls) {
                                    Text(
                                        text = "Adaptive quality",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = YouTubeRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun playStream(player: ExoPlayer, videoUrl: String, audioUrl: String?) {
    if (audioUrl != null) {
        val dsFactory = DefaultHttpDataSource.Factory()
        val videoSource = ProgressiveMediaSource.Factory(dsFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl))
        val audioSource = ProgressiveMediaSource.Factory(dsFactory)
            .createMediaSource(MediaItem.fromUri(audioUrl))
        player.setMediaSource(MergingMediaSource(videoSource, audioSource))
    } else {
        player.setMediaItem(MediaItem.fromUri(videoUrl))
    }
    player.prepare()
}

/** YouTube-style double-tap seek feedback overlay */
@Composable
private fun SeekFeedbackOverlay(seekDirection: Int) {
    AnimatedVisibility(
        visible = seekDirection != 0,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val alignment = if (seekDirection == -1) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier
                    .align(alignment)
                    .padding(horizontal = 40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (seekDirection == -1) Icons.Default.FastRewind else Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "10 seconds",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon, label,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/** YouTube-style related video card — full-width thumbnail with info row below */
@Composable
private fun RelatedVideoCard(
    video: com.aspect.braveyoutube.data.model.VideoItem,
    onClick: () -> Unit,
    onChannelClick: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Full-width thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Duration badge
            if (video.duration.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = video.duration,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Info row below thumbnail
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Channel avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onChannelClick(video.channelId) },
                contentAlignment = Alignment.Center
            ) {
                if (video.channelAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = video.channelAvatarUrl,
                        contentDescription = video.channelName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = video.channelName.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(video.channelName)
                        if (video.viewCount.isNotBlank()) append(" · ${video.viewCount} views")
                        if (video.publishedAt.isNotBlank()) append(" · ${video.publishedAt.take(10)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onChannelClick(video.channelId) }
                )
            }

            // 3-dot menu
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatViewCount(count: Long): String {
    return NumberFormat.getNumberInstance(Locale.US).format(count)
}

private fun formatCompactNumber(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
