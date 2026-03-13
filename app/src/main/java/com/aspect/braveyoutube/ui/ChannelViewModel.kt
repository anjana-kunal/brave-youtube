package com.aspect.braveyoutube.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aspect.braveyoutube.data.model.ChannelDetail
import com.aspect.braveyoutube.data.model.VideoItem
import com.aspect.braveyoutube.data.repository.NewPipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChannelViewModel(
    private val newPipeRepository: NewPipeRepository = NewPipeRepository()
) : ViewModel() {

    private val _channelDetail = MutableStateFlow<ChannelDetail?>(null)
    val channelDetail: StateFlow<ChannelDetail?> = _channelDetail.asStateFlow()

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadChannelDetails(channelUrlOrId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // NewPipeRepository needs functions to get channel info and videos
                val detail = newPipeRepository.getChannelDetails(channelUrlOrId)
                if (detail != null) {
                    _channelDetail.value = detail
                    val channelVideos = newPipeRepository.getChannelVideos(channelUrlOrId)
                    _videos.value = channelVideos
                } else {
                    _error.value = "Failed to load channel details"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "An unknown error occurred"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
