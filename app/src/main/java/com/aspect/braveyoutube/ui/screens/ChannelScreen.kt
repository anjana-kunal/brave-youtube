package com.aspect.braveyoutube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aspect.braveyoutube.ui.ChannelViewModel
import com.aspect.braveyoutube.ui.HomeViewModel
import com.aspect.braveyoutube.ui.components.LoadingSpinner
import com.aspect.braveyoutube.ui.components.VideoCard
import com.aspect.braveyoutube.ui.theme.YouTubeRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelId: String,
    viewModel: ChannelViewModel,
    homeViewModel: HomeViewModel,
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val channelDetail by viewModel.channelDetail.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val subscribedChannelIds by homeViewModel.subscribedChannelIds.collectAsState(initial = emptySet())
    val isSubscribed = subscribedChannelIds.contains(channelId)

    LaunchedEffect(channelId) {
        viewModel.loadChannelDetails(channelId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Cast */ }) {
                        Icon(Icons.Default.Cast, contentDescription = "Cast")
                    }
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { /* More */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading && channelDetail == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingSpinner(color = YouTubeRed)
                }
            } else if (error != null && channelDetail == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error ?: "Failed to load channel",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Header Image (Banner)
                    if (!channelDetail?.bannerUrl.isNullOrBlank()) {
                        item {
                            AsyncImage(
                                model = channelDetail?.bannerUrl,
                                contentDescription = "Channel Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 3f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }

                    // Avatar, Name, Handle, Subscribers, Video count
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!channelDetail?.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = channelDetail?.avatarUrl,
                                        contentDescription = "Channel Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = channelDetail?.name ?: "",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (!channelDetail?.handle.isNullOrBlank()) {
                                        Text(
                                            text = channelDetail!!.handle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = buildString {
                                            if (!channelDetail?.subscriberCount.isNullOrBlank()) {
                                                append("${channelDetail?.subscriberCount} subscribers")
                                            }
                                            if (videos.isNotEmpty()) {
                                                if (isNotEmpty()) append(" • ")
                                                append("${videos.size} videos")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!channelDetail?.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = channelDetail!!.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "...more",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { homeViewModel.toggleSubscribedChannel(channelId) },
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
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            ) {
                                if (isSubscribed) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Subscribed", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                } else {
                                    Text("Subscribe", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // Tabs
                    item {
                        ScrollableTabRow(
                            selectedTabIndex = 1,
                            edgePadding = 16.dp,
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                            divider = {
                                Divider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                    thickness = 1.dp
                                )
                            }
                        ) {
                            val tabs = listOf("Home", "Videos", "Live", "Playlists", "Posts")
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = index == 1,
                                    onClick = { /* TODO */ },
                                    text = {
                                        Text(
                                            text = title,
                                            fontWeight = if (index == 1) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 15.sp
                                        )
                                    },
                                    selectedContentColor = MaterialTheme.colorScheme.onBackground,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // List of videos
                    if (videos.isNotEmpty()) {
                        items(videos, key = { it.id }) { video ->
                            VideoCard(
                                video = video,
                                onClick = { onVideoClick(video.id) },
                                onChannelClick = { /* Already on channel */ }
                            )
                        }
                    } else if (isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                LoadingSpinner(color = YouTubeRed, size = 24.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
