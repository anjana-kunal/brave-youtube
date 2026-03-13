package com.aspect.braveyoutube.ui.navigation

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.aspect.braveyoutube.ui.HomeViewModel
import com.aspect.braveyoutube.ui.screens.HomeScreen
import com.aspect.braveyoutube.ui.screens.PlayerScreen
import com.aspect.braveyoutube.ui.screens.SearchScreen
import kotlin.math.roundToInt

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLAYER = "player/{videoId}"
    const val CHANNEL = "channel/{channelId}"
    fun player(videoId: String) = "player/$videoId"
    fun channel(channelId: String) = "channel/$channelId"
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AppNavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel()

    // Shared ExoPlayer — persists across navigation for mini-player
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }

    // Mini-player state
    var miniPlayerVideoId by remember { mutableStateOf<String?>(null) }
    var miniPlayerTitle by remember { mutableStateOf("") }
    var miniPlayerChannel by remember { mutableStateOf("") }
    var miniPlayerThumbnail by remember { mutableStateOf("") }
    var isMiniPlayerVisible by remember { mutableStateOf(false) }
    var isMiniPlayerPlaying by remember { mutableStateOf(true) }
    var streamLoadedForVideoId by remember { mutableStateOf("") }

    // Release ExoPlayer on destroy
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // Track current route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isOnPlayerScreen = navBackStackEntry?.destination?.route?.startsWith("player") == true

    Box(modifier = Modifier.fillMaxSize()) {
        // NO bottom padding — mini-player floats OVER content
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onVideoClick = { videoId ->
                        if (isMiniPlayerVisible) {
                            exoPlayer.stop()
                            streamLoadedForVideoId = ""
                        }
                        isMiniPlayerVisible = false
                        navController.navigate(Routes.player(videoId))
                    },
                    onSearchClick = {
                        navController.navigate(Routes.SEARCH)
                    },
                    onChannelClick = { channelId ->
                        navController.navigate(Routes.channel(channelId))
                    }
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    viewModel = homeViewModel,
                    onVideoClick = { videoId ->
                        if (isMiniPlayerVisible) {
                            exoPlayer.stop()
                            streamLoadedForVideoId = ""
                        }
                        isMiniPlayerVisible = false
                        navController.navigate(Routes.player(videoId))
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType })
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
                PlayerScreen(
                    videoId = videoId,
                    exoPlayer = exoPlayer,
                    homeViewModel = homeViewModel,
                    isStreamAlreadyLoaded = (streamLoadedForVideoId == videoId),
                    onBackClick = {
                        miniPlayerVideoId = videoId
                        miniPlayerThumbnail = "https://img.youtube.com/vi/$videoId/hq720.jpg"
                        isMiniPlayerVisible = true
                        isMiniPlayerPlaying = exoPlayer.isPlaying
                        navController.popBackStack()
                    },
                    onVideoClick = { newVideoId ->
                        exoPlayer.stop()
                        streamLoadedForVideoId = ""
                        navController.navigate(Routes.player(newVideoId)) {
                            popUpTo(Routes.PLAYER) { inclusive = true }
                        }
                    },
                    onVideoInfoLoaded = { title, channel ->
                        miniPlayerTitle = title
                        miniPlayerChannel = channel
                    },
                    onStreamLoaded = { id ->
                        streamLoadedForVideoId = id
                    },
                    onChannelClick = { channelId ->
                        navController.navigate(Routes.channel(channelId))
                    }
                )
            }

            composable(
                route = Routes.CHANNEL,
                arguments = listOf(navArgument("channelId") { type = NavType.StringType })
            ) { backStackEntry ->
                val channelId = backStackEntry.arguments?.getString("channelId") ?: return@composable
                
                // Using viewModel() to scope it to the backStackEntry 
                val channelViewModel: com.aspect.braveyoutube.ui.ChannelViewModel = viewModel()
                
                com.aspect.braveyoutube.ui.screens.ChannelScreen(
                    channelId = channelId,
                    viewModel = channelViewModel,
                    homeViewModel = homeViewModel,
                    onBackClick = { navController.popBackStack() },
                    onVideoClick = { videoId ->
                         navController.navigate(Routes.player(videoId))
                    }
                )
            }
        }

        // ── Floating Draggable Mini Player (YouTube PiP style) ──
        if (isMiniPlayerVisible && !isOnPlayerScreen && miniPlayerVideoId != null) {
            FloatingMiniPlayer(
                title = miniPlayerTitle,
                channelName = miniPlayerChannel,
                isPlaying = isMiniPlayerPlaying,
                exoPlayer = exoPlayer,
                onPlayPauseClick = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        isMiniPlayerPlaying = false
                    } else {
                        exoPlayer.play()
                        isMiniPlayerPlaying = true
                    }
                },
                onClick = {
                    isMiniPlayerVisible = false
                    navController.navigate(Routes.player(miniPlayerVideoId!!))
                },
                onClose = {
                    exoPlayer.stop()
                    isMiniPlayerVisible = false
                    miniPlayerVideoId = null
                    streamLoadedForVideoId = ""
                }
            )
        }
    }
}

/**
 * YouTube-style floating PiP mini-player.
 * - Shows actual playing video (not just thumbnail)
 * - Freely draggable anywhere on screen
 * - Snaps to nearest corner when released
 * - Play/pause + close overlay buttons
 * - Tap to expand back to full player
 */
@UnstableApi
@Composable
private fun FloatingMiniPlayer(
    title: String,
    channelName: String,
    isPlaying: Boolean,
    exoPlayer: ExoPlayer,
    onPlayPauseClick: () -> Unit,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Mini-player size
    val miniPlayerWidth = with(density) { 180.dp.toPx() }
    val miniPlayerHeight = with(density) { 120.dp.toPx() } // slightly taller to fit title
    val margin = with(density) { 12.dp.toPx() }

    // Position state — start at bottom-right
    var offsetX by remember { mutableFloatStateOf(screenWidthPx - miniPlayerWidth - margin) }
    var offsetY by remember { mutableFloatStateOf(screenHeightPx - miniPlayerHeight - margin - with(density) { 60.dp.toPx() }) }

    // Track playback progress
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        while (true) {
            val duration = exoPlayer.duration.coerceAtLeast(1L)
            val position = exoPlayer.currentPosition
            progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            kotlinx.coroutines.delay(500)
        }
    }

    // Show controls overlay on tap
    var showControls by remember { mutableStateOf(true) }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            kotlinx.coroutines.delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(180.dp)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidthPx - miniPlayerWidth)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeightPx - miniPlayerHeight)
                    },
                    onDragEnd = {
                        // Snap to nearest corner
                        val centerX = offsetX + miniPlayerWidth / 2
                        val centerY = offsetY + miniPlayerHeight / 2
                        val targetX = if (centerX < screenWidthPx / 2) margin
                        else screenWidthPx - miniPlayerWidth - margin
                        val targetY = if (centerY < screenHeightPx / 2) margin
                        else screenHeightPx - miniPlayerHeight - margin - with(density) { 60.dp.toPx() }
                        offsetX = targetX
                        offsetY = targetY.coerceAtLeast(margin)
                    }
                )
            }
    ) {
        // Actual video playback via PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .clickable { showControls = !showControls }
        )

        // Controls overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                // Play/Pause in center
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Close button top-right
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Progress bar at bottom of video area
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter),
            color = Color(0xFFFF0000),
            trackColor = Color.White.copy(alpha = 0.3f),
        )

        // Title bar below video
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xFF212121))
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                fontSize = 11.sp
            )
        }
    }
}
