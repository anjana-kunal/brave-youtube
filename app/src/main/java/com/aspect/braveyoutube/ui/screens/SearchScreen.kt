package com.aspect.braveyoutube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val persistedQuery by viewModel.searchQuery.collectAsState()
    val isSearchMode by viewModel.isSearchMode.collectAsState()
    val suggestions by viewModel.searchSuggestions.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }

    LaunchedEffect(persistedQuery) {
        if (persistedQuery.isNotBlank() && query != persistedQuery) {
            query = persistedQuery
        }
    }

    val watchedIds = remember(watchHistory) { watchHistory.map { it.videoId }.toSet() }
    val filteredVideos = remember(videos, selectedFilter, watchedIds) {
        when (selectedFilter) {
            "Shorts" -> videos.filter { it.isShort || looksLikeShort(it.duration) }
            "Videos" -> videos.filterNot { it.isShort || looksLikeShort(it.duration) }
            "Watched" -> videos.filter { watchedIds.contains(it.id) }
            "Unwatched" -> videos.filterNot { watchedIds.contains(it.id) }
            else -> videos
        }
    }

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
                viewModel.updateSearchDraft(newQuery)
                viewModel.fetchSuggestions(newQuery)
            },
            onSearch = { searchQuery ->
                viewModel.searchVideos(searchQuery)
                viewModel.addSearchQuery(searchQuery)
                viewModel.clearSuggestions()
                selectedFilter = "All"
            },
            onBackClick = {
                viewModel.clearSuggestions()
                viewModel.setSearchMode(false)
                onBackClick()
            },
            suggestions = if (!isSearchMode) suggestions else emptyList(),
            onSuggestionClick = { suggestion ->
                query = suggestion
                viewModel.searchVideos(suggestion)
                viewModel.addSearchQuery(suggestion)
                viewModel.clearSuggestions()
                selectedFilter = "All"
            },
            searchHistory = searchHistory,
            onHistoryClick = { historyItem ->
                query = historyItem
                viewModel.searchVideos(historyItem)
                viewModel.addSearchQuery(historyItem)
                viewModel.clearSuggestions()
                selectedFilter = "All"
            },
            onHistoryRemove = { item ->
                viewModel.removeSearchQuery(item)
            }
        )

        // Results (only shown after search, when suggestions are hidden)
        if (isSearchMode || suggestions.isEmpty()) {
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
                    !isSearchMode -> {
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
                    filteredVideos.isEmpty() && isSearchMode -> {
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
                        Column(modifier = Modifier.fillMaxSize()) {
                            SearchFilterChips(
                                selected = selectedFilter,
                                onSelect = { selectedFilter = it }
                            )

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredVideos, key = { it.id }) { video ->
                                    VideoCard(
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
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchFilterChips(
    selected: String,
    onSelect: (String) -> Unit
) {
    val filters = listOf("All", "Shorts", "Videos", "Unwatched", "Watched")
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter) }
            )
        }
    }
}

private fun looksLikeShort(duration: String): Boolean {
    if (duration.isBlank()) return false
    val parts = duration.split(":").mapNotNull { it.toIntOrNull() }
    val seconds = when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> Int.MAX_VALUE
    }
    return seconds in 1..70
}
