package com.aspect.braveyoutube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aspect.braveyoutube.ui.HomeViewModel
import com.aspect.braveyoutube.ui.components.*
import com.aspect.braveyoutube.ui.theme.YouTubeRed

@Composable
fun SearchScreen(
    viewModel: HomeViewModel = viewModel(),
    onVideoClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val videos by viewModel.videos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val suggestions by viewModel.searchSuggestions.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    var query by remember { mutableStateOf("") }
    var hasSearched by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // YouTube-style search bar with autocomplete suggestions + history
        YouTubeSearchBar(
            query = query,
            onQueryChange = { newQuery ->
                query = newQuery
                hasSearched = false
                viewModel.fetchSuggestions(newQuery)
            },
            onSearch = { searchQuery ->
                viewModel.searchVideos(searchQuery)
                viewModel.addSearchQuery(searchQuery)
                viewModel.clearSuggestions()
                hasSearched = true
            },
            onBackClick = {
                viewModel.clearSuggestions()
                onBackClick()
            },
            suggestions = if (!hasSearched) suggestions else emptyList(),
            onSuggestionClick = { suggestion ->
                query = suggestion
                viewModel.searchVideos(suggestion)
                viewModel.addSearchQuery(suggestion)
                viewModel.clearSuggestions()
                hasSearched = true
            },
            searchHistory = searchHistory,
            onHistoryClick = { historyItem ->
                query = historyItem
                viewModel.searchVideos(historyItem)
                viewModel.addSearchQuery(historyItem)
                viewModel.clearSuggestions()
                hasSearched = true
            },
            onHistoryRemove = { item ->
                viewModel.removeSearchQuery(item)
            }
        )

        // Results (only shown after search, when suggestions are hidden)
        if (hasSearched || suggestions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingSpinner(color = YouTubeRed)
                        }
                    }
                    !hasSearched -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Search for videos, channels, and more",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    videos.isEmpty() && hasSearched -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No results found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(videos, key = { it.id }) { video ->
                                SmallVideoCard(
                                    video = video,
                                    onClick = { onVideoClick(video.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
