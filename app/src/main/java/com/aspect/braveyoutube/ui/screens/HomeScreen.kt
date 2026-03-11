package com.aspect.braveyoutube.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aspect.braveyoutube.data.model.FeedCategory
import com.aspect.braveyoutube.data.model.SubscriptionChannel
import com.aspect.braveyoutube.ui.HomeViewModel
import com.aspect.braveyoutube.ui.components.*
import com.aspect.braveyoutube.ui.theme.YouTubeRed
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onVideoClick: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val error by viewModel.error.collectAsState()
    val shieldsEnabled by viewModel.shieldsEnabled.collectAsState()
    val blockAds by viewModel.blockAds.collectAsState()
    val blockShorts by viewModel.blockShorts.collectAsState()
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val userPhotoUrl by viewModel.userPhotoUrl.collectAsState()
    val isSignedIn by viewModel.isSignedIn.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val subscriptionsList by viewModel.subscriptions.collectAsState()
    val localSubscriptions by viewModel.localSubscriptions.collectAsState()
    val subscribedChannelIds by viewModel.subscribedChannelIds.collectAsState()
    val shortsVideos by viewModel.shortsVideos.collectAsState()
    val shortsLoading by viewModel.shortsLoading.collectAsState()
    val likedVideos by viewModel.likedVideos.collectAsState()
    val isDark = isSystemInDarkTheme()

    var showShieldsPanel by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var showHistoryScreen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // ── Google Sign-In setup with YouTube scope ──
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/youtube.readonly"))
            .build()
    }
    val signInClient = remember { GoogleSignIn.getClient(activity, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            viewModel.setUserProfile(
                name = account.displayName ?: "",
                email = account.email ?: "",
                photoUrl = account.photoUrl?.toString() ?: ""
            )
            // Fetch OAuth token + load personalized feed
            viewModel.onSignInComplete()
            // Auto-navigate to Library/profile tab after sign-in
            selectedTab = 3
            Toast.makeText(context, "Signed in as ${account.displayName}", Toast.LENGTH_SHORT).show()
        } catch (e: ApiException) {
            // Show error feedback instead of silently failing
            val msg = when (e.statusCode) {
                12501 -> "Sign-in cancelled"
                12500 -> "Sign-in failed. Check Google Play Services."
                10 -> "Developer error. Check app configuration."
                else -> "Sign-in failed (code: ${e.statusCode})"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // Check if already signed in on first composition
    LaunchedEffect(Unit) {
        val existing = GoogleSignIn.getLastSignedInAccount(context)
        if (existing != null && userEmail.isBlank()) {
            viewModel.setUserProfile(
                name = existing.displayName ?: "",
                email = existing.email ?: "",
                photoUrl = existing.photoUrl?.toString() ?: ""
            )
        }
    }

    fun launchSignIn() {
        signInLauncher.launch(signInClient.signInIntent)
    }

    fun signOut() {
        signInClient.signOut().addOnCompleteListener {
            viewModel.clearUserProfile()
            selectedTab = 0
            Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
        }
    }

    // Infinite scroll detection
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoading) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // YouTube logo
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .background(YouTubeRed, RoundedCornerShape(8.dp))
                            .padding(4.dp)
                            .size(18.dp)
                    )
                    Text(
                        text = "YouTube",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 6.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Cast
                    IconButton(onClick = { /* Cast */ }) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = "Cast",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // Notifications
                    IconButton(onClick = { /* Notifications */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // Search
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // Profile avatar
                    Box(modifier = Modifier.padding(start = 4.dp)) {
                        IconButton(
                            onClick = {
                                if (isSignedIn) {
                                    selectedTab = 3 // Go to Library/You
                                } else {
                                    launchSignIn()
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            if (userPhotoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = userPhotoUrl,
                                    contentDescription = "Profile",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                )
                            } else if (userName.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(YouTubeRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = userName.first().uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Sign In",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            YouTubeBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (selectedTab) {
            0 -> {
                // ── Home tab ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Category chips
                    CategoryTabs(
                        categories = FeedCategory.entries,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { viewModel.selectCategory(it) },
                        isDarkTheme = isDark
                    )

                    // Video feed
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            isLoading && videos.isEmpty() -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoadingSpinner(color = YouTubeRed)
                                }
                            }
                            error != null && videos.isEmpty() -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Connect to the internet",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "You're offline. Check your connection.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedButton(onClick = { viewModel.refreshFeed() }) {
                                        Text("Retry")
                                    }
                                }
                            }
                            else -> {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    // Removed pull-to-refresh hint text to look like native app

                                    items(videos, key = { it.id }) { video ->
                                        VideoCard(
                                            video = video,
                                            onClick = { onVideoClick(video.id) }
                                        )
                                    }

                                    if (isLoading && videos.isNotEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                LoadingSpinner(size = 28.dp, color = YouTubeRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // ── Shorts tab ──
                LaunchedEffect(Unit) { viewModel.loadShorts() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when {
                        shortsLoading && shortsVideos.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingSpinner(color = YouTubeRed)
                            }
                        }
                        shortsVideos.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("No Shorts found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        else -> {
                            val config = LocalConfiguration.current
                            val columns = if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp, start = 8.dp, end = 8.dp)
                            ) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        "Shorts",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                    )
                                }
                                items(shortsVideos, key = { it.id }) { video ->
                                    ShortVideoCard(
                                        video = video,
                                        onClick = { onVideoClick(video.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                // ── Subscriptions tab ──
                // Merge OAuth subs + local in-app subscribed channels
                val allSubscriptions = remember(subscriptionsList, localSubscriptions) {
                    val merged = mutableListOf<SubscriptionChannel>()
                    val seenIds = mutableSetOf<String>()
                    // OAuth subscriptions first
                    for (ch in subscriptionsList) {
                        if (seenIds.add(ch.channelId)) merged.add(ch)
                    }
                    // Then local in-app subscriptions
                    for (ch in localSubscriptions) {
                        if (seenIds.add(ch.channelId)) merged.add(ch)
                    }
                    merged
                }

                if (allSubscriptions.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            Text(
                                "Subscriptions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(allSubscriptions, key = { it.channelId }) { channel ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(72.dp)
                                    ) {
                                        if (channel.thumbnailUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = channel.thumbnailUrl,
                                                contentDescription = channel.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(YouTubeRed),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = channel.title.firstOrNull()?.uppercase() ?: "?",
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 20.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = channel.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Total: ${allSubscriptions.size} channels",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        // Full list of subscriptions
                        items(allSubscriptions, key = { "list_${it.channelId}" }) { channel ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { /* Could navigate to channel */ }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (channel.thumbnailUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = channel.thumbnailUrl,
                                        contentDescription = channel.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(YouTubeRed),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = channel.title.firstOrNull()?.uppercase() ?: "?",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = channel.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else if (subscribedChannelIds.isNotEmpty()) {
                    // Have in-app subscriptions but still loading channel info
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingSpinner(color = YouTubeRed)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Loading ${subscribedChannelIds.size} subscriptions...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // No subscriptions at all
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Subscriptions, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No Subscriptions", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Subscribe to channels from the\nvideo player to see them here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            3 -> {
                // ── Library tab ──
                if (showHistoryScreen) {
                    // Full history screen
                    WatchHistoryScreen(
                        history = watchHistory,
                        onVideoClick = onVideoClick,
                        onClearHistory = { viewModel.clearWatchHistory() },
                        onBack = { showHistoryScreen = false }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(16.dp)) }

                        if (isSignedIn) {
                            // Signed-in profile card
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (userPhotoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = userPhotoUrl,
                                            contentDescription = "Profile",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(YouTubeRed),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = userName.firstOrNull()?.uppercase() ?: "?",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = userName.ifBlank { "User" },
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = userEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            item {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // ── Recent Watch History Preview ──
                            if (watchHistory.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.onBackground
                                            )
                                            Text(
                                                "History",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                        TextButton(onClick = { showHistoryScreen = true }) {
                                            Text(
                                                "View all",
                                                color = YouTubeRed,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }

                                // Horizontal scrollable history thumbnails
                                item {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(watchHistory.take(10), key = { "${it.videoId}_${it.watchedAt}" }) { item ->
                                            HistoryThumbnailCard(
                                                item = item,
                                                onClick = { onVideoClick(item.videoId) }
                                            )
                                        }
                                    }
                                }

                                item { Spacer(modifier = Modifier.height(8.dp)) }
                            }

                            // ── Liked Videos Preview ──
                            if (likedVideos.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ThumbUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            "Liked videos",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            "${likedVideos.size} videos",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                item {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(likedVideos.take(10), key = { "liked_${it.id}" }) { video ->
                                            Column(
                                                modifier = Modifier
                                                    .width(160.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onVideoClick(video.id) }
                                            ) {
                                                AsyncImage(
                                                    model = video.thumbnailUrl,
                                                    contentDescription = video.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(90.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = video.title,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    lineHeight = 16.sp
                                                )
                                                Text(
                                                    text = video.channelName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(8.dp)) }
                            }

                            item {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            // Library menu items
                            item {
                                LibraryMenuItem(
                                    icon = Icons.Outlined.History,
                                    title = "History",
                                    subtitle = "${watchHistory.size} videos",
                                    onClick = { showHistoryScreen = true }
                                )
                            }
                            item {
                                LibraryMenuItem(
                                    icon = Icons.Outlined.FileDownload,
                                    title = "Downloads",
                                    subtitle = "Saved videos"
                                )
                            }
                            item {
                                LibraryMenuItem(
                                    icon = Icons.Default.PlayArrow,
                                    title = "Your videos",
                                    subtitle = "Videos you've uploaded"
                                )
                            }
                            item {
                                LibraryMenuItem(
                                    icon = Icons.Outlined.WatchLater,
                                    title = "Watch later",
                                    subtitle = "Videos saved for later"
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Sign out button
                            item {
                                OutlinedButton(
                                    onClick = { signOut() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sign out", fontWeight = FontWeight.Medium)
                                }
                            }
                        } else {
                            // Not signed in
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(80.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Enjoy your favorite videos",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Sign in to access your account,\nsubscriptions, playlists, and more",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = { launchSignIn() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = YouTubeRed,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            // Still show history for non-signed-in users
                            if (watchHistory.isNotEmpty()) {
                                item {
                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.onBackground
                                            )
                                            Text(
                                                "Recent history",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                        TextButton(onClick = { showHistoryScreen = true }) {
                                            Text("View all", color = YouTubeRed, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        }
                                    }
                                }
                                item {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(watchHistory.take(10), key = { "${it.videoId}_${it.watchedAt}" }) { item ->
                                            HistoryThumbnailCard(
                                                item = item,
                                                onClick = { onVideoClick(item.videoId) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Shields bottom sheet
    if (showShieldsPanel) {
        ShieldsPanel(
            shieldsEnabled = shieldsEnabled,
            blockAds = blockAds,
            blockShorts = blockShorts,
            backgroundPlayback = backgroundPlayback,
            adsBlocked = viewModel.adsBlocked,
            onToggleShields = { viewModel.toggleShields(it) },
            onToggleBlockAds = { viewModel.toggleBlockAds(it) },
            onToggleBlockShorts = { viewModel.toggleBlockShorts(it) },
            onToggleBgPlayback = { viewModel.toggleBgPlayback(it) },
            onDismiss = { showShieldsPanel = false }
        )
    }
}

@Composable
private fun LibraryMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Compact thumbnail card for history items in horizontal scroll */
@Composable
private fun HistoryThumbnailCard(
    item: com.aspect.braveyoutube.data.model.HistoryItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        Text(
            text = item.channelName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Full-screen watch history list */
@Composable
private fun WatchHistoryScreen(
    history: List<com.aspect.braveyoutube.data.model.HistoryItem>,
    onVideoClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                "Watch history",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (history.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No watch history yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Videos you watch will appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history, key = { "${it.videoId}_${it.watchedAt}" }) { item ->
                    HistoryListItem(
                        item = item,
                        onClick = { onVideoClick(item.videoId) }
                    )
                }
            }
        }
    }

    // Clear history confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear watch history?") },
            text = { Text("This will remove all your watch history. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    showClearDialog = false
                }) {
                    Text("Clear", color = YouTubeRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Individual history item for the full history list */
@Composable
private fun HistoryListItem(
    item: com.aspect.braveyoutube.data.model.HistoryItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Thumbnail
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        // Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
            Text(
                text = item.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Time ago
            Text(
                text = formatTimeAgo(item.watchedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Format timestamp as "X minutes/hours/days ago" */
private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        else -> "${days / 30}mo ago"
    }
}
