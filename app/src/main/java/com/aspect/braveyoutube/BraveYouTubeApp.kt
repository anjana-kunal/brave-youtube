package com.aspect.braveyoutube

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aspect.braveyoutube.adblock.AdBlockEngine
import com.aspect.braveyoutube.data.repository.StreamExtractor

/**
 * Application class — initializes ad-block engine, NewPipe, and notification channels.
 */
class BraveYouTubeApp : Application() {

    lateinit var adBlockEngine: AdBlockEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        adBlockEngine = AdBlockEngine()
        StreamExtractor.init() // Initialize NewPipe Extractor
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "brave_yt_playback"
        lateinit var instance: BraveYouTubeApp
            private set
    }
}
