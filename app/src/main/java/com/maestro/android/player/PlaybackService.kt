package com.maestro.android.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.maestro.android.MainActivity
import com.maestro.android.data.model.Track
import kotlinx.coroutines.*
import java.io.File

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private lateinit var controller: PlayerController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionJob: Job? = null

    companion object {
        private var cache: SimpleCache? = null
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        controller = PlayerController.getInstance(this)

        // Persistent audio store: every played track is kept on disk forever.
        // Lives under filesDir (not cacheDir, which the OS can wipe) and uses
        // NoOpCacheEvictor so SimpleCache never drops a span on its own.
        if (cache == null) {
            val audioDir = File(filesDir, "audio_cache")
            val databaseProvider = StandaloneDatabaseProvider(this)
            cache = SimpleCache(audioDir, NoOpCacheEvictor(), databaseProvider)
        }

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache!!)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val p = exoPlayer
                    val pos = p?.currentPosition ?: 0L
                    val playerDur = p?.duration ?: C.TIME_UNSET
                    val expectedDur = controller.state.value.duration
                    val effectiveDur =
                        if (playerDur != C.TIME_UNSET && playerDur > 0) playerDur else expectedDur
                    if (effectiveDur > 0 && pos < effectiveDur - 5_000) {
                        // Stream truncated before reaching end — record real cutoff, then recover with a fresh URL.
                        controller.updatePosition(pos, effectiveDur)
                        controller.onPlaybackError(null)
                    } else {
                        controller.onTrackEnded()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    positionJob?.cancel()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                controller.onPlaybackError(error)
            }
        })

        player.volume = controller.state.value.volume

        val sessionActivityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()
        exoPlayer = player

        controller.onPlayUrl = { url, track, startPositionMs -> playUrl(url, track, startPositionMs) }
        controller.onPause = { player.pause() }
        controller.onResume = { player.play() }
        controller.onStop = {
            player.stop()
            player.clearMediaItems()
            stopSelf()
        }
        controller.onVolumeChange = { v -> player.volume = v }
        controller.onClearCacheForTrack = { trackId, positionMs, durationMs ->
            evictCacheTail(trackId, positionMs, durationMs)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    @OptIn(UnstableApi::class)
    private fun evictCacheTail(trackId: String, positionMs: Long, durationMs: Long) {
        val c = cache ?: return
        val contentLength = ContentMetadata.getContentLength(c.getContentMetadata(trackId))
        // Below 10s of progress (or unknown content length / duration) we can't safely
        // map ms → bytes, so blow away the whole resource and let it re-download.
        if (contentLength == C.LENGTH_UNSET.toLong() || durationMs <= 0L || positionMs < 10_000L) {
            c.removeResource(trackId)
            return
        }
        val cutoffMs = positionMs - 10_000L
        val cutoffBytes = (cutoffMs.toDouble() / durationMs * contentLength).toLong()
        for (span in c.getCachedSpans(trackId).toList()) {
            val spanLen = if (span.length == C.LENGTH_UNSET.toLong()) 0L else span.length
            if (span.position + spanLen > cutoffBytes) {
                c.removeSpan(span)
            }
        }
    }

    private fun playUrl(url: String, track: Track, startPositionMs: Long) {
        val player = exoPlayer ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setCustomCacheKey(track.id)
            .setMediaMetadata(metadata)
            .build()
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val player = exoPlayer ?: break
                if (player.isPlaying) {
                    controller.updatePosition(player.currentPosition, player.duration.coerceAtLeast(0))
                }
                delay(500)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        positionJob?.cancel()
        scope.cancel()
        controller.onPlayUrl = null
        controller.onPause = null
        controller.onResume = null
        controller.onStop = null
        controller.onVolumeChange = null
        controller.onClearCacheForTrack = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }
}
