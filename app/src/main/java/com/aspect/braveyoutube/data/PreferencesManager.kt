package com.aspect.braveyoutube.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.aspect.braveyoutube.data.model.HistoryItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "brave_yt_prefs")

/**
 * Manages user preferences using DataStore.
 */
class PreferencesManager(private val context: Context) {

    private val gson = Gson()

    val shieldsEnabled: Flow<Boolean> = context.dataStore.data.map { it[SHIELDS_ENABLED] ?: true }
    val blockAds: Flow<Boolean> = context.dataStore.data.map { it[BLOCK_ADS] ?: true }
    val blockTrackers: Flow<Boolean> = context.dataStore.data.map { it[BLOCK_TRACKERS] ?: true }
    val blockShorts: Flow<Boolean> = context.dataStore.data.map { it[BLOCK_SHORTS] ?: false }
    val backgroundPlayback: Flow<Boolean> = context.dataStore.data.map { it[BG_PLAYBACK] ?: true }
    val autoplayEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTOPLAY] ?: true }

    suspend fun setShieldsEnabled(v: Boolean) { context.dataStore.edit { it[SHIELDS_ENABLED] = v } }
    suspend fun setBlockAds(v: Boolean) { context.dataStore.edit { it[BLOCK_ADS] = v } }
    suspend fun setBlockTrackers(v: Boolean) { context.dataStore.edit { it[BLOCK_TRACKERS] = v } }
    suspend fun setBlockShorts(v: Boolean) { context.dataStore.edit { it[BLOCK_SHORTS] = v } }
    suspend fun setBackgroundPlayback(v: Boolean) { context.dataStore.edit { it[BG_PLAYBACK] = v } }
    suspend fun setAutoplayEnabled(v: Boolean) { context.dataStore.edit { it[AUTOPLAY] = v } }

    // User profile
    val userName: Flow<String> = context.dataStore.data.map { it[USER_NAME] ?: "" }
    val userEmail: Flow<String> = context.dataStore.data.map { it[USER_EMAIL] ?: "" }
    val userPhotoUrl: Flow<String> = context.dataStore.data.map { it[USER_PHOTO] ?: "" }

    suspend fun setUserProfile(name: String, email: String, photoUrl: String) {
        context.dataStore.edit {
            it[USER_NAME] = name
            it[USER_EMAIL] = email
            it[USER_PHOTO] = photoUrl
        }
    }

    suspend fun clearUserProfile() {
        context.dataStore.edit {
            it.remove(USER_NAME)
            it.remove(USER_EMAIL)
            it.remove(USER_PHOTO)
        }
    }

    // ── Watch History ──

    val watchHistory: Flow<List<HistoryItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[WATCH_HISTORY] ?: "[]"
        try {
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            gson.fromJson<List<HistoryItem>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addToHistory(item: HistoryItem) {
        context.dataStore.edit { prefs ->
            val json = prefs[WATCH_HISTORY] ?: "[]"
            val list = try {
                val type = object : TypeToken<MutableList<HistoryItem>>() {}.type
                gson.fromJson<MutableList<HistoryItem>>(json, type) ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            // Remove existing entry for same video (move to top)
            list.removeAll { it.videoId == item.videoId }
            // Add at the beginning (most recent first)
            list.add(0, item)
            // Keep only the last 100 entries
            val trimmed = if (list.size > 100) list.take(100) else list
            prefs[WATCH_HISTORY] = gson.toJson(trimmed)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it[WATCH_HISTORY] = "[]" }
    }

    // ── Search History ──

    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val json = prefs[SEARCH_HISTORY] ?: "[]"
        try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addSearchQuery(query: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[SEARCH_HISTORY] ?: "[]"
            val list = try {
                val type = object : TypeToken<MutableList<String>>() {}.type
                gson.fromJson<MutableList<String>>(json, type) ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            list.remove(query)
            list.add(0, query)
            val trimmed = if (list.size > 20) list.take(20) else list
            prefs[SEARCH_HISTORY] = gson.toJson(trimmed)
        }
    }

    suspend fun removeSearchQuery(query: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[SEARCH_HISTORY] ?: "[]"
            val list = try {
                val type = object : TypeToken<MutableList<String>>() {}.type
                gson.fromJson<MutableList<String>>(json, type) ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            list.remove(query)
            prefs[SEARCH_HISTORY] = gson.toJson(list)
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { it[SEARCH_HISTORY] = "[]" }
    }

    // ── Liked Videos ──

    val likedVideoIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val json = prefs[LIKED_VIDEO_IDS] ?: "[]"
        try {
            val type = object : TypeToken<List<String>>() {}.type
            (gson.fromJson<List<String>>(json, type) ?: emptyList()).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun toggleLikedVideo(videoId: String): Boolean {
        var isNowLiked = false
        context.dataStore.edit { prefs ->
            val json = prefs[LIKED_VIDEO_IDS] ?: "[]"
            val list = try {
                val type = object : TypeToken<MutableList<String>>() {}.type
                gson.fromJson<MutableList<String>>(json, type) ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            if (list.contains(videoId)) {
                list.remove(videoId)
                isNowLiked = false
            } else {
                list.add(0, videoId)
                isNowLiked = true
            }
            prefs[LIKED_VIDEO_IDS] = gson.toJson(list)
        }
        return isNowLiked
    }

    // ── Subscribed Channels ──

    val subscribedChannelIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val json = prefs[SUBSCRIBED_CHANNEL_IDS] ?: "[]"
        try {
            val type = object : TypeToken<List<String>>() {}.type
            (gson.fromJson<List<String>>(json, type) ?: emptyList()).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun toggleSubscribedChannel(channelId: String): Boolean {
        var isNowSubscribed = false
        context.dataStore.edit { prefs ->
            val json = prefs[SUBSCRIBED_CHANNEL_IDS] ?: "[]"
            val list = try {
                val type = object : TypeToken<MutableList<String>>() {}.type
                gson.fromJson<MutableList<String>>(json, type) ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            if (list.contains(channelId)) {
                list.remove(channelId)
                isNowSubscribed = false
            } else {
                list.add(0, channelId)
                isNowSubscribed = true
            }
            prefs[SUBSCRIBED_CHANNEL_IDS] = gson.toJson(list)
        }
        return isNowSubscribed
    }

    companion object {
        private val SHIELDS_ENABLED = booleanPreferencesKey("shields_enabled")
        private val BLOCK_ADS = booleanPreferencesKey("block_ads")
        private val BLOCK_TRACKERS = booleanPreferencesKey("block_trackers")
        private val BLOCK_SHORTS = booleanPreferencesKey("block_shorts")
        private val BG_PLAYBACK = booleanPreferencesKey("bg_playback")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val USER_PHOTO = stringPreferencesKey("user_photo")
        private val WATCH_HISTORY = stringPreferencesKey("watch_history")
        private val SEARCH_HISTORY = stringPreferencesKey("search_history")
        private val LIKED_VIDEO_IDS = stringPreferencesKey("liked_video_ids")
        private val SUBSCRIBED_CHANNEL_IDS = stringPreferencesKey("subscribed_channel_ids")
        private val AUTOPLAY = booleanPreferencesKey("autoplay")
    }
}
