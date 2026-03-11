package com.aspect.braveyoutube.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aspect.braveyoutube.BraveYouTubeApp
import com.aspect.braveyoutube.data.PreferencesManager
import com.aspect.braveyoutube.data.model.FeedCategory
import com.aspect.braveyoutube.data.model.HistoryItem
import com.aspect.braveyoutube.data.model.SubscriptionChannel
import com.aspect.braveyoutube.data.model.VideoItem
import com.aspect.braveyoutube.data.repository.NewPipeRepository
import com.aspect.braveyoutube.data.repository.YouTubeRepository
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.Locale

/**
 * ViewModel for the Home / Feed screen.
 * Uses NewPipe Extractor for all data — NO API quota limits.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BraveYouTubeApp
    // NewPipe-based repo — unlimited, no API key
    private val newPipeRepo = NewPipeRepository(app.adBlockEngine)
    // Keep old repo only for authenticated features (subscriptions, liked videos)
    private val repo = YouTubeRepository(app.adBlockEngine)
    val prefs = PreferencesManager(application)

    // Use device region so users see local trending content
    private val regionCode: String = Locale.getDefault().country.ifBlank { "US" }

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<FeedCategory?>(null)
    val selectedCategory: StateFlow<FeedCategory?> = _selectedCategory.asStateFlow()

    private val _isSearchMode = MutableStateFlow(false)
    val isSearchMode: StateFlow<Boolean> = _isSearchMode.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Shields stats
    val adsBlocked: Int get() = (getApplication<BraveYouTubeApp>()).adBlockEngine.totalBlocked

    // Preferences as state
    val shieldsEnabled = prefs.shieldsEnabled.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val blockAds = prefs.blockAds.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val blockShorts = prefs.blockShorts.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val backgroundPlayback = prefs.backgroundPlayback.stateIn(viewModelScope, SharingStarted.Lazily, true)

    // User profile state (Google Sign-In)
    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow("")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userPhotoUrl = MutableStateFlow("")
    val userPhotoUrl: StateFlow<String> = _userPhotoUrl.asStateFlow()

    // Reactive sign-in state — Compose will recompose when this changes
    val isSignedIn: StateFlow<Boolean> = _userEmail
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // OAuth access token for YouTube API (in-memory only, not persisted)
    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    // Subscriptions
    private val _subscriptions = MutableStateFlow<List<SubscriptionChannel>>(emptyList())
    val subscriptions: StateFlow<List<SubscriptionChannel>> = _subscriptions.asStateFlow()

    // Liked videos
    private val _likedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val likedVideos: StateFlow<List<VideoItem>> = _likedVideos.asStateFlow()

    // Watch history
    private val _watchHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val watchHistory: StateFlow<List<HistoryItem>> = _watchHistory.asStateFlow()

    // Search history
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    // Liked video IDs (persisted)
    private val _likedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val likedVideoIds: StateFlow<Set<String>> = _likedVideoIds.asStateFlow()

    // Subscribed channel IDs (persisted)
    private val _subscribedChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val subscribedChannelIds: StateFlow<Set<String>> = _subscribedChannelIds.asStateFlow()

    // Local subscriptions (fetched via NewPipe from in-app subscribed channels)
    private val _localSubscriptions = MutableStateFlow<List<SubscriptionChannel>>(emptyList())
    val localSubscriptions: StateFlow<List<SubscriptionChannel>> = _localSubscriptions.asStateFlow()

    // Shorts
    private val _shortsVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val shortsVideos: StateFlow<List<VideoItem>> = _shortsVideos.asStateFlow()

    private val _shortsLoading = MutableStateFlow(false)
    val shortsLoading: StateFlow<Boolean> = _shortsLoading.asStateFlow()

    private var nextPageToken: String? = null
    private var hasLoadedPersonalizedFeed = false

    init {
        loadSmartFeed()  // Start with personalized feed
        loadSavedProfile()
        loadWatchHistory()
        loadSearchHistory()
        loadLikedAndSubscribed()
        // Try to get token for already-signed-in users
        tryRefreshToken()
    }

    private fun loadSavedProfile() {
        viewModelScope.launch {
            prefs.userName.collect { _userName.value = it }
        }
        viewModelScope.launch {
            prefs.userEmail.collect { _userEmail.value = it }
        }
        viewModelScope.launch {
            prefs.userPhotoUrl.collect { _userPhotoUrl.value = it }
        }
    }

    private fun loadWatchHistory() {
        viewModelScope.launch {
            prefs.watchHistory.collect { _watchHistory.value = it }
        }
    }

    private fun loadSearchHistory() {
        viewModelScope.launch {
            prefs.searchHistory.collect { _searchHistory.value = it }
        }
    }

    private fun loadLikedAndSubscribed() {
        viewModelScope.launch {
            prefs.likedVideoIds.collect { _likedVideoIds.value = it }
        }
        viewModelScope.launch {
            prefs.subscribedChannelIds.collect {
                _subscribedChannelIds.value = it
                // Auto-load local subscriptions whenever the set changes
                if (it.isNotEmpty()) {
                    loadLocalSubscriptions(it)
                }
            }
        }
    }

    /** Load Shorts videos via NewPipe */
    fun loadShorts() {
        if (_shortsVideos.value.isNotEmpty()) return // Already loaded
        viewModelScope.launch {
            _shortsLoading.value = true
            try {
                val shorts = newPipeRepo.getShorts(regionCode = regionCode)
                _shortsVideos.value = shorts
                Log.d("HomeVM", "Loaded ${shorts.size} shorts")
            } catch (e: Exception) {
                Log.e("HomeVM", "loadShorts failed: ${e.message}")
            } finally {
                _shortsLoading.value = false
            }
        }
    }

    /** Load channel info for in-app subscribed channels via NewPipe */
    fun loadLocalSubscriptions(channelIds: Set<String> = _subscribedChannelIds.value) {
        if (channelIds.isEmpty()) {
            _localSubscriptions.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val channelJobs = channelIds.take(20).map { chId ->
                    async(Dispatchers.IO) {
                        try {
                            val url = if (chId.startsWith("UC")) {
                                "https://www.youtube.com/channel/$chId"
                            } else {
                                "https://www.youtube.com/@$chId"
                            }
                            val info = newPipeRepo.getChannelInfoData(url)
                            if (info != null) {
                                SubscriptionChannel(
                                    channelId = chId,
                                    title = info.name.ifBlank { chId },
                                    thumbnailUrl = info.avatarUrl
                                )
                            } else null
                        } catch (e: Exception) {
                            Log.e("HomeVM", "Channel info fetch failed for $chId: ${e.message}")
                            null
                        }
                    }
                }
                
                val channels = channelJobs.awaitAll().filterNotNull()
                _localSubscriptions.value = channels
                Log.d("HomeVM", "Loaded ${channels.size} local subscriptions")
            } catch (e: Exception) {
                Log.e("HomeVM", "loadLocalSubscriptions failed: ${e.message}")
            }
        }
    }

    fun addSearchQuery(query: String) {
        viewModelScope.launch { prefs.addSearchQuery(query) }
    }

    fun removeSearchQuery(query: String) {
        viewModelScope.launch { prefs.removeSearchQuery(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { prefs.clearSearchHistory() }
    }

    fun toggleLikedVideo(videoId: String) {
        viewModelScope.launch {
            prefs.toggleLikedVideo(videoId)
        }
    }

    fun toggleSubscribedChannel(channelId: String) {
        viewModelScope.launch {
            prefs.toggleSubscribedChannel(channelId)
        }
    }

    /** Try to obtain an OAuth access token for the already-signed-in Google account */
    private fun tryRefreshToken() {
        viewModelScope.launch {
            try {
                val context = getApplication<BraveYouTubeApp>()
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account?.account != null) {
                    val token = withContext(Dispatchers.IO) {
                        GoogleAuthUtil.getToken(
                            context,
                            account.account!!,
                            "oauth2:https://www.googleapis.com/auth/youtube.readonly"
                        )
                    }
                    _accessToken.value = token
                    Log.d("HomeVM", "Got OAuth token for ${account.email}")
                    // Load personalized data
                    loadPersonalizedData()
                }
            } catch (e: Exception) {
                Log.e("HomeVM", "Token refresh failed: ${e.message}")
                // Not critical — user can still use the app with trending feed
            }
        }
    }

    /** Called after sign-in to fetch token and load personalized content */
    fun onSignInComplete() {
        tryRefreshToken()
    }

    /** Load subscriptions, liked videos, and personalized home feed */
    private fun loadPersonalizedData() {
        val token = _accessToken.value ?: return

        viewModelScope.launch {
            try {
                val subs = repo.getMySubscriptions(token)
                _subscriptions.value = subs
                Log.d("HomeVM", "Loaded ${subs.size} subscriptions")
            } catch (e: Exception) {
                Log.e("HomeVM", "Subscriptions failed: ${e.message}")
            }
        }

        viewModelScope.launch {
            try {
                val liked = repo.getMyLikedVideos(token)
                _likedVideos.value = liked
                Log.d("HomeVM", "Loaded ${liked.size} liked videos")
            } catch (e: Exception) {
                Log.e("HomeVM", "Liked videos failed: ${e.message}")
            }
        }

        // Load personalized home feed (mix subscription videos with trending)
        if (!hasLoadedPersonalizedFeed) {
            loadPersonalizedFeed()
        }
    }

    /** Build a personalized home feed — subscriptions first, fallback to trending */
    private fun loadPersonalizedFeed() {
        val token = _accessToken.value ?: return
        hasLoadedPersonalizedFeed = true

        viewModelScope.launch {
            try {
                val subFeed = repo.getSubscriptionFeed(token, maxChannels = 20)
                if (subFeed.videos.isNotEmpty()) {
                    _videos.value = subFeed.videos
                    Log.d("HomeVM", "Personalized feed: ${subFeed.videos.size} videos from subscriptions")
                } else {
                    Log.d("HomeVM", "Sub feed empty, keeping trending")
                    if (_videos.value.isEmpty()) {
                        val trending = newPipeRepo.getTrendingVideos(regionCode = regionCode)
                        _videos.value = trending.videos
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeVM", "Personalized feed failed: ${e.message}")
                if (_videos.value.isEmpty()) {
                    try {
                        val trending = newPipeRepo.getTrendingVideos(regionCode = regionCode)
                        _videos.value = trending.videos
                    } catch (_: Exception) { }
                }
            }
        }
    }

    fun addToWatchHistory(videoId: String, title: String, channelName: String, thumbnailUrl: String, channelId: String = "") {
        viewModelScope.launch {
            prefs.addToHistory(
                HistoryItem(
                    videoId = videoId,
                    title = title,
                    channelName = channelName,
                    channelId = channelId,
                    thumbnailUrl = thumbnailUrl
                )
            )
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            prefs.clearHistory()
        }
    }

    /**
     * Smart personalized feed algorithm:
     * 1. Fetch trending videos (baseline)
     * 2. Fetch videos from channels the user has watched/subscribed to
     * 3. Fetch videos based on search history topics
     * 4. Merge & interleave so the feed feels personalized
     *
     * Mix ratio: ~40% from subscribed/watched channels, ~30% trending, ~30% topic-based
     */
    fun loadSmartFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            _isSearchMode.value = false
            _error.value = null

            try {
                // Baseline: trending videos
                val trendingResult = newPipeRepo.getTrendingVideos(regionCode = regionCode)
                val trendingVideos = trendingResult.videos

                // Collect user signals
                val history = _watchHistory.value
                val subscribedIds = _subscribedChannelIds.value
                val searchQueries = _searchHistory.value

                // If no user data yet, just show trending
                if (history.isEmpty() && subscribedIds.isEmpty() && searchQueries.isEmpty()) {
                    _videos.value = trendingVideos
                    Log.d("HomeVM", "SmartFeed: No user data, showing ${trendingVideos.size} trending")
                    _isLoading.value = false
                    return@launch
                }

                val personalizedVideos = mutableListOf<VideoItem>()

                // ── 1. Videos from channels the user watches / subscribes to ──
                val channelUrls = mutableSetOf<String>()

                // Add subscribed channels
                for (chId in subscribedIds.take(5)) {
                    val url = if (chId.startsWith("UC") || chId.startsWith("@")) {
                        if (chId.startsWith("@")) "https://www.youtube.com/@$chId"
                        else "https://www.youtube.com/channel/$chId"
                    } else {
                        "https://www.youtube.com/@$chId"
                    }
                    channelUrls.add(url)
                }

                // Add channels from watch history (recently watched channels)
                for (item in history.take(15)) {
                    val chId = item.channelId
                    if (!chId.isNullOrBlank()) {
                        val url = if (chId.startsWith("UC")) {
                            "https://www.youtube.com/channel/$chId"
                        } else {
                            "https://www.youtube.com/@$chId"
                        }
                        channelUrls.add(url)
                    }
                }

                // Fetch videos from these channels (in parallel, up to 5 channels)
                val channelJobs = channelUrls.take(5).map { url ->
                    async(Dispatchers.IO) {
                        try {
                            newPipeRepo.getChannelVideos(url, maxResults = 5)
                        } catch (e: Exception) {
                            Log.e("HomeVM", "Channel fetch failed for $url: ${e.message}")
                            emptyList()
                        }
                    }
                }
                for (job in channelJobs) {
                    personalizedVideos.addAll(job.await())
                }

                // ── 2. Topic-based videos from search history ──
                val topicVideos = mutableListOf<VideoItem>()
                val topicQueries = searchQueries.take(3) // Use top 3 recent searches as topics
                val topicJobs = topicQueries.map { query ->
                    async(Dispatchers.IO) {
                        try {
                            newPipeRepo.searchVideos(query).videos.take(5)
                        } catch (e: Exception) {
                            Log.e("HomeVM", "Topic search '$query' failed: ${e.message}")
                            emptyList()
                        }
                    }
                }
                for (job in topicJobs) {
                    topicVideos.addAll(job.await())
                }

                // ── 3. Merge and interleave ──
                val seenIds = mutableSetOf<String>()
                val finalFeed = mutableListOf<VideoItem>()

                // Helper to add unique videos
                fun addUnique(videos: List<VideoItem>, maxCount: Int) {
                    var added = 0
                    for (v in videos) {
                        if (!v.id.isNullOrBlank() && seenIds.add(v.id)) {
                            finalFeed.add(v)
                            added++
                            if (added >= maxCount) break
                        }
                    }
                }

                // Shuffle personalized and topic for variety
                personalizedVideos.shuffle()
                topicVideos.shuffle()

                // Interleave: 2 personalized, 2 trending, 1 topic, repeat
                val pIter = personalizedVideos.iterator()
                val tIter = trendingVideos.iterator()
                val sIter = topicVideos.iterator()

                var round = 0
                while (finalFeed.size < 50 && (pIter.hasNext() || tIter.hasNext() || sIter.hasNext())) {
                    // 2 from personalized (channel-based)
                    repeat(2) {
                        if (pIter.hasNext()) {
                            val v = pIter.next()
                            if (!v.id.isNullOrBlank() && seenIds.add(v.id)) finalFeed.add(v)
                        }
                    }
                    // 2 from trending
                    repeat(2) {
                        if (tIter.hasNext()) {
                            val v = tIter.next()
                            if (!v.id.isNullOrBlank() && seenIds.add(v.id)) finalFeed.add(v)
                        }
                    }
                    // 1 from topic search
                    if (sIter.hasNext()) {
                        val v = sIter.next()
                        if (!v.id.isNullOrBlank() && seenIds.add(v.id)) finalFeed.add(v)
                    }
                    round++
                }

                // If we still have few results, pad with remaining trending
                while (tIter.hasNext() && finalFeed.size < 50) {
                    val v = tIter.next()
                    if (!v.id.isNullOrBlank() && seenIds.add(v.id)) finalFeed.add(v)
                }

                _videos.value = finalFeed
                Log.d("HomeVM", "SmartFeed: ${finalFeed.size} videos (${personalizedVideos.size} channel, ${topicVideos.size} topic, ${trendingVideos.size} trending)")

            } catch (e: Exception) {
                _error.value = "Failed to load feed: ${e.message}"
                Log.e("HomeVM", "loadSmartFeed failed: ${e.message}")
                // Fallback to plain trending
                try {
                    val fallback = newPipeRepo.getTrendingVideos(regionCode = regionCode)
                    _videos.value = fallback.videos
                } catch (_: Exception) { }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Search Suggestions ──
    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val suggestClient = OkHttpClient()
    private var suggestJob: Job? = null

    /** Fetch YouTube autocomplete suggestions with debouncing */
    fun fetchSuggestions(query: String) {
        suggestJob?.cancel()
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250) // debounce 250ms
            try {
                val suggestions = withContext(Dispatchers.IO) {
                    val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=${
                        java.net.URLEncoder.encode(query, "UTF-8")
                    }"
                    val request = Request.Builder().url(url).build()
                    val response = suggestClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    parseYouTubeSuggestions(body)
                }
                _searchSuggestions.value = suggestions
            } catch (_: Exception) {
                _searchSuggestions.value = emptyList()
            }
        }
    }

    fun clearSuggestions() {
        _searchSuggestions.value = emptyList()
    }

    /** Parse the JSONP response from YouTube suggest API */
    private fun parseYouTubeSuggestions(response: String): List<String> {
        return try {
            // Response is like: window.google.ac.h(["query",[["suggestion1"],["suggestion2"],...]])
            val jsonStr = response.substringAfter("(").substringBeforeLast(")")
            val jsonArr = JSONArray(jsonStr)
            val suggestionsArr = jsonArr.getJSONArray(1)
            val results = mutableListOf<String>()
            for (i in 0 until suggestionsArr.length().coerceAtMost(8)) {
                val item = suggestionsArr.getJSONArray(i)
                results.add(item.getString(0))
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Load trending videos. When categoryId is null ("All" tab),
     * mix videos from trending + a random category so the feed
     * feels fresh each time the app is opened.
     */
    fun loadTrending(categoryId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _isSearchMode.value = false
            _error.value = null
            try {
                // Use NewPipe Extractor — NO API quota, unlimited
                val result = newPipeRepo.getTrendingVideos(regionCode = regionCode)
                if (result.videos.isNotEmpty()) {
                    _videos.value = result.videos
                    Log.d("HomeVM", "Trending via NewPipe: ${result.videos.size} videos")
                } else {
                    _error.value = "No trending videos found"
                }
            } catch (e: Exception) {
                _error.value = "Failed to load videos: ${e.message}"
                Log.e("HomeVM", "loadTrending failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Pull-to-refresh: reload the current feed */
    fun refreshFeed() {
        hasLoadedPersonalizedFeed = false
        loadSmartFeed()
    }

    fun searchVideos(query: String) {
        if (query.isBlank()) return
        _searchQuery.value = query
        viewModelScope.launch {
            _isLoading.value = true
            _isSearchMode.value = true
            _error.value = null
            try {
                // Use NewPipe Extractor — NO quota limits
                val result = newPipeRepo.searchVideos(query)
                _videos.value = result.videos
                Log.d("HomeVM", "Search via NewPipe: ${result.videos.size} results")
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        // NewPipe Extractor handles pagination internally
        // For now, just reload to get fresh content
    }

    fun selectCategory(category: FeedCategory?) {
        _selectedCategory.value = category
        if (category == null || category == FeedCategory.TRENDING) {
            loadSmartFeed()
        } else {
            // Use NewPipe search for category-specific content
            viewModelScope.launch {
                _isLoading.value = true
                _isSearchMode.value = false
                _error.value = null
                try {
                    val result = newPipeRepo.searchVideos("${category.displayName} trending")
                    _videos.value = result.videos
                    Log.d("HomeVM", "Category '${category.displayName}': ${result.videos.size} videos")
                } catch (e: Exception) {
                    _error.value = "Failed to load ${category.displayName}: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    // ── User Profile (Google Sign-In) ──

    fun setUserProfile(name: String, email: String, photoUrl: String) {
        _userName.value = name
        _userEmail.value = email
        _userPhotoUrl.value = photoUrl
        viewModelScope.launch {
            prefs.setUserProfile(name, email, photoUrl)
        }
    }

    fun clearUserProfile() {
        _userName.value = ""
        _userEmail.value = ""
        _userPhotoUrl.value = ""
        _accessToken.value = null
        _subscriptions.value = emptyList()
        _likedVideos.value = emptyList()
        hasLoadedPersonalizedFeed = false
        viewModelScope.launch {
            prefs.clearUserProfile()
        }
        // Reload feed (will still use watch history if available)
        loadSmartFeed()
    }

    // ── Toggles ──

    fun toggleShields(enabled: Boolean) = viewModelScope.launch { prefs.setShieldsEnabled(enabled) }
    fun toggleBlockAds(enabled: Boolean) = viewModelScope.launch { prefs.setBlockAds(enabled) }
    fun toggleBlockShorts(enabled: Boolean) = viewModelScope.launch { prefs.setBlockShorts(enabled) }
    fun toggleBgPlayback(enabled: Boolean) = viewModelScope.launch { prefs.setBackgroundPlayback(enabled) }
}
