package com.aspect.braveyoutube.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aspect.braveyoutube.BraveYouTubeApp
import com.aspect.braveyoutube.MainActivity
import com.aspect.braveyoutube.R

/**
 * Foreground service for background audio playback via ExoPlayer.
 */
class BackgroundPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundPlayback()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startForegroundPlayback() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BackgroundPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, BraveYouTubeApp.PLAYBACK_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.now_playing))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.aspect.braveyoutube.START_PLAYBACK"
        const val ACTION_STOP = "com.aspect.braveyoutube.STOP_PLAYBACK"
        const val NOTIFICATION_ID = 1001
    }
}
