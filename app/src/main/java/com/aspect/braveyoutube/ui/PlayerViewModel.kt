package com.aspect.braveyoutube.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aspect.braveyoutube.BraveYouTubeApp
import com.aspect.braveyoutube.data.model.VideoDetail
import com.aspect.braveyoutube.data.model.VideoItem
import com.aspect.braveyoutube.data.repository.NewPipeRepository
import com.aspect.braveyoutube.data.repository.StreamExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * ViewModel for the Video Player screen.
 * Uses NewPipe Extractor — NO API quota limits.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = NewPipeRepository((application as BraveYouTubeApp).adBlockEngine)

    private val _videoDetail = MutableStateFlow<VideoDetail?>(null)
    val videoDetail: StateFlow<VideoDetail?> = _videoDetail.asStateFlow()

    private val _streamInfo = MutableStateFlow<StreamExtractor.StreamInfo?>(null)
    val streamInfo: StateFlow<StreamExtractor.StreamInfo?> = _streamInfo.asStateFlow()

    private val _relatedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val relatedVideos: StateFlow<List<VideoItem>> = _relatedVideos.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadVideo(videoId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _relatedVideos.value = emptyList()

            try {
                supervisorScope {
                    // Fetch detail + stream in parallel
                    val detailJob = async {
                        try { repo.getVideoDetail(videoId) } catch (e: Exception) {
                            Log.e("PlayerVM", "Detail fetch failed: ${e.message}")
                            null
                        }
                    }
                    val streamJob = async {
                        try { StreamExtractor.extractStream(videoId) } catch (e: Exception) {
                            Log.e("PlayerVM", "Stream extraction failed: ${e.message}")
                            null
                        }
                    }

                    val detail = detailJob.await()
                    _videoDetail.value = detail
                    _streamInfo.value = streamJob.await()

                    val stream = _streamInfo.value
                    if (stream?.videoUrl == null) {
                        _error.value = "Could not extract video stream. Try another video."
                        Log.e("PlayerVM", "Stream URL is null for $videoId")
                    }
                }

                // Load related videos via NewPipe (no quota, gets them from the page itself)
                launch {
                    try {
                        val related = repo.getRelatedVideos(videoId)
                        _relatedVideos.value = related
                        Log.d("PlayerVM", "Loaded ${related.size} related videos via NewPipe")
                    } catch (e: Exception) {
                        Log.e("PlayerVM", "Related videos failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load video: ${e.message}"
                Log.e("PlayerVM", "loadVideo failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
