package com.maestro.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.maestro.android.data.remote.NewPipeOkHttpDownloader
import com.maestro.android.mcp.McpServer
import com.maestro.android.player.PlayerController
import org.schabi.newpipe.extractor.NewPipe
import java.io.File

class MaestroApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeOkHttpDownloader.getInstance())
        purgeStaleAudioCacheOnce()
        PlayerController.getInstance(this)
        createNotificationChannel()
        McpServer.start()
    }

    // Caches written before the WAKE_MODE_NETWORK fix were frequently truncated
    // by doze (CPU/WiFi suspended mid-download). Replaying those tracks stops at
    // the truncation point. Wipe once so the new wake-locked playback can rewrite
    // them cleanly.
    private fun purgeStaleAudioCacheOnce() {
        val prefs = getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_AUDIO_CACHE_PURGED_V1, false)) return
        File(filesDir, "audio_cache").deleteRecursively()
        prefs.edit().putBoolean(KEY_AUDIO_CACHE_PURGED_V1, true).apply()
    }

    // Persistent thumbnail cache so cover art shows offline. Coil's default disk
    // cache lives in cacheDir (which the OS can wipe); pin it under filesDir and
    // ignore cache headers so thumbnails are kept for the saved library.
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(filesDir, "thumbnail_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
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
        private const val MIGRATION_PREFS = "maestro_migration"
        private const val KEY_AUDIO_CACHE_PURGED_V1 = "audio_cache_purged_v1"
    }
}
