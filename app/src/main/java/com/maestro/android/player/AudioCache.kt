package com.maestro.android.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Process-wide handle to the permanent audio cache.
 *
 * Every played track is stored here forever (NoOpCacheEvictor), under filesDir
 * (which the OS never wipes) rather than cacheDir. SimpleCache forbids two
 * instances over the same directory in one process, so both [PlaybackService]
 * (which writes during playback) and [PlayerController] (which reads to decide
 * what can play offline) must share this single instance.
 */
@OptIn(UnstableApi::class)
object AudioCache {

    private const val DIR_NAME = "audio_cache"

    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: run {
                val app = context.applicationContext
                val dir = File(app.filesDir, DIR_NAME)
                SimpleCache(dir, NoOpCacheEvictor(), StandaloneDatabaseProvider(app))
                    .also { cache = it }
            }
        }
    }

    /** IDs of tracks whose audio is completely present (no gaps) — playable with no internet. */
    fun fullyCachedTrackIds(context: Context): Set<String> {
        val c = get(context)
        val ids = mutableSetOf<String>()
        for (key in c.keys) {
            val length = ContentMetadata.getContentLength(c.getContentMetadata(key))
            if (length > 0 && c.getCachedBytes(key, 0, length) >= length) {
                ids.add(key)
            }
        }
        return ids
    }
}
