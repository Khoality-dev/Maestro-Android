package com.maestro.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.maestro.android.mcp.McpServer
import com.maestro.android.player.PlayerController

class MaestroApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PlayerController.getInstance(this)
        createNotificationChannel()
        McpServer.start()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Music playback controls"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "maestro_playback"
    }
}
