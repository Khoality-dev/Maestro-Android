package com.maestro.android.player

import android.content.Context
import android.util.Log
import com.maestro.android.data.datastore.AppDataStore
import com.maestro.android.data.datastore.PlayerStorage
import com.maestro.android.data.model.LoopMode
import com.maestro.android.data.model.PlaybackState
import com.maestro.android.data.model.PlayerState
import com.maestro.android.data.model.Track
import com.maestro.android.data.remote.MaestroApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlayerController(
    private val storage: PlayerStorage,
    apiFactory: () -> MaestroApi = { MaestroApi() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val clock: () -> Long = System::currentTimeMillis,
    // Reports which track IDs are fully downloaded to disk. Defaults to "nothing
    // cached" so unit tests need no Android cache; the real app wires in AudioCache.
    private val downloadedIdsProvider: () -> Set<String> = { emptySet() },
) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    val api: MaestroApi by lazy(apiFactory)

    var onPlayUrl: ((String, Track, Long) -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onVolumeChange: ((Float) -> Unit)? = null
    var onClearCacheForTrack: ((trackId: String, positionMs: Long, durationMs: Long) -> Unit)? = null

    private var lastRetryAtMs: Long = 0L

    init {
        scope.launch {
            val queue = storage.loadQueue()
            val history = storage.loadHistory()
            val saved = storage.loadSavedTracks()
            val volume = storage.loadVolume()
            val loopMode = storage.loadLoopMode()
            val autoplay = storage.loadAutoplaySimilar()
            val downloaded = withContext(Dispatchers.IO) { downloadedIdsProvider() }
            _state.update {
                it.copy(
                    queue = queue,
                    history = history,
                    savedTracks = saved,
                    volume = volume,
                    loopMode = loopMode,
                    autoplaySimilar = autoplay,
                    downloadedIds = downloaded
                )
            }
        }
    }

    /** Recompute which saved tracks are fully on disk. Safe to call repeatedly. */
    fun refreshDownloaded() {
        val ids = downloadedIdsProvider()
        _state.update { it.copy(downloadedIds = ids) }
    }

    suspend fun play(track: Track) {
        val current = _state.value.currentTrack
        if (current != null && _state.value.state != PlaybackState.STOPPED) {
            // Enqueue if already playing
            enqueue(track)
            return
        }
        startTrack(track)
    }

    suspend fun playNow(track: Track) {
        startTrack(track)
    }

    private suspend fun startTrack(track: Track) {
        addToLibrary(track)

        // Offline-first: if the whole track is already on disk, play straight from the
        // cache and skip the network extraction entirely. This makes saved songs work
        // with no internet, and makes replays of cached songs instant.
        val cachedIds = downloadedIdsProvider()
        if (track.id in cachedIds) {
            _state.update {
                it.copy(
                    state = PlaybackState.PLAYING,
                    currentTrack = track,
                    position = 0L,
                    duration = ((track.duration ?: 0.0) * 1000).toLong(),
                    downloadedIds = cachedIds
                )
            }
            addToHistory(track)
            // The cache key is track.id, so the upstream URI is never opened for a
            // fully-cached track — any stable URI works as the media item's address.
            val cacheUri = track.url.ifBlank { "https://www.youtube.com/watch?v=${track.id}" }
            onPlayUrl?.invoke(cacheUri, track, 0L)
            return
        }

        try {
            val extracted = api.extractStreamUrl(track.id)
            val updatedTrack = track.copy(
                duration = extracted.duration ?: track.duration,
                title = if (track.title.isEmpty()) extracted.title ?: track.title else track.title,
                artist = track.artist ?: extracted.artist
            )
            _state.update {
                it.copy(
                    state = PlaybackState.PLAYING,
                    currentTrack = updatedTrack,
                    position = 0L,
                    duration = ((updatedTrack.duration ?: 0.0) * 1000).toLong()
                )
            }
            addToHistory(updatedTrack)
            addToLibrary(updatedTrack)
            onPlayUrl?.invoke(extracted.streamUrl, updatedTrack, 0L)
        } catch (e: Exception) {
            Log.e("PlayerController", "Failed to start track: ${e.message}")
            // Skip to next if extraction fails
            skipToNext()
        }
    }

    fun onPlaybackError(error: Throwable?) {
        val track = _state.value.currentTrack ?: return
        val now = clock()
        // Guard against tight retry loops if the stream keeps failing.
        if (now - lastRetryAtMs < 5_000L) {
            Log.w("PlayerController", "Retry loop detected; skipping to next")
            scope.launch { skipToNext() }
            return
        }
        lastRetryAtMs = now
        val resumeFromMs = _state.value.position
        val durationMs = _state.value.duration
        Log.w("PlayerController", "Playback error (${error?.message}); refreshing stream URL for ${track.id} at ${resumeFromMs}ms")
        onClearCacheForTrack?.invoke(track.id, resumeFromMs, durationMs)
        scope.launch {
            try {
                val extracted = api.extractStreamUrl(track.id, refresh = true)
                onPlayUrl?.invoke(extracted.streamUrl, track, resumeFromMs)
            } catch (e: Exception) {
                Log.e("PlayerController", "Stream refresh failed: ${e.message}")
                skipToNext()
            }
        }
    }

    fun pause() {
        if (_state.value.state == PlaybackState.PLAYING) {
            _state.update { it.copy(state = PlaybackState.PAUSED) }
            onPause?.invoke()
        }
    }

    fun resume() {
        if (_state.value.state == PlaybackState.PAUSED) {
            _state.update { it.copy(state = PlaybackState.PLAYING) }
            onResume?.invoke()
        }
    }

    fun togglePlayPause() {
        when (_state.value.state) {
            PlaybackState.PLAYING -> pause()
            PlaybackState.PAUSED -> resume()
            PlaybackState.STOPPED -> {}
        }
    }

    suspend fun skipToNext() {
        val current = _state.value
        when (current.loopMode) {
            LoopMode.ONE -> {
                current.currentTrack?.let { startTrack(it) }
            }
            LoopMode.QUEUE -> {
                val track = current.currentTrack
                if (current.queue.isNotEmpty()) {
                    val next = current.queue.first()
                    _state.update { it.copy(queue = it.queue.drop(1)) }
                    if (track != null) enqueue(track)
                    startTrack(next)
                } else if (track != null) {
                    startTrack(track)
                }
            }
            LoopMode.OFF -> {
                if (current.queue.isNotEmpty()) {
                    val next = current.queue.first()
                    _state.update { it.copy(queue = it.queue.drop(1)) }
                    startTrack(next)
                } else if (current.autoplaySimilar && current.currentTrack != null) {
                    playSimilarRadio(current.currentTrack)
                } else {
                    stop()
                }
            }
        }
        persistQueue()
    }

    /**
     * Radio: continue from [seed] with similar songs. Plays the first recommendation
     * and queues the rest. Falls back to stopping if recommendations can't be fetched
     * (e.g. offline) so we never spin.
     */
    private suspend fun playSimilarRadio(seed: Track) {
        val related = try {
            api.getRelated(seed.id)
        } catch (e: Exception) {
            Log.w("PlayerController", "Radio fetch failed: ${e.message}")
            emptyList()
        }
        val recentIds = (_state.value.history.map { it.id } + seed.id).toSet()
        val fresh = related.filterNot { it.id in recentIds }
        if (fresh.isEmpty()) {
            stop()
            return
        }
        _state.update { it.copy(queue = it.queue + fresh.drop(1)) }
        startTrack(fresh.first())
    }

    /** Append songs similar to [seed] to the queue (explicit "Play similar" action). */
    suspend fun enqueueSimilar(seed: Track) {
        val related = try {
            api.getRelated(seed.id)
        } catch (e: Exception) {
            Log.w("PlayerController", "Similar fetch failed: ${e.message}")
            return
        }
        val existing = (_state.value.queue.map { it.id } +
            listOfNotNull(_state.value.currentTrack?.id)).toSet()
        val fresh = related.filterNot { it.id in existing }
        if (fresh.isEmpty()) return
        // Nothing playing yet → start the first one, queue the rest. Otherwise just queue.
        if (_state.value.currentTrack == null || _state.value.state == PlaybackState.STOPPED) {
            _state.update { it.copy(queue = it.queue + fresh.drop(1)) }
            startTrack(fresh.first())
        } else {
            _state.update { it.copy(queue = it.queue + fresh) }
        }
        persistQueue()
    }

    fun setAutoplaySimilar(enabled: Boolean) {
        _state.update { it.copy(autoplaySimilar = enabled) }
        scope.launch { storage.saveAutoplaySimilar(enabled) }
    }

    fun toggleAutoplaySimilar() = setAutoplaySimilar(!_state.value.autoplaySimilar)

    fun stop() {
        _state.update {
            it.copy(
                state = PlaybackState.STOPPED,
                currentTrack = null,
                queue = emptyList(),
                position = 0L,
                duration = 0L
            )
        }
        onStop?.invoke()
        scope.launch { persistQueue() }
    }

    suspend fun enqueue(track: Track) {
        _state.update { it.copy(queue = it.queue + track) }
        persistQueue()
    }

    fun removeFromQueue(index: Int) {
        _state.update { s ->
            if (index in s.queue.indices) {
                s.copy(queue = s.queue.toMutableList().apply { removeAt(index) })
            } else s
        }
        scope.launch { persistQueue() }
    }

    fun moveInQueue(from: Int, to: Int) {
        _state.update { s ->
            if (from in s.queue.indices && to in s.queue.indices) {
                val list = s.queue.toMutableList()
                val item = list.removeAt(from)
                list.add(to, item)
                s.copy(queue = list)
            } else s
        }
        scope.launch { persistQueue() }
    }

    fun clearQueue() {
        _state.update { it.copy(queue = emptyList()) }
        scope.launch { persistQueue() }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = clamped) }
        onVolumeChange?.invoke(clamped)
        scope.launch { storage.saveVolume(clamped) }
    }

    fun setLoopMode(mode: LoopMode) {
        _state.update { it.copy(loopMode = mode) }
        scope.launch { storage.saveLoopMode(mode) }
    }

    fun cycleLoopMode() {
        val next = when (_state.value.loopMode) {
            LoopMode.OFF -> LoopMode.QUEUE
            LoopMode.QUEUE -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.OFF
        }
        setLoopMode(next)
    }

    fun updatePosition(positionMs: Long, durationMs: Long) {
        _state.update { it.copy(position = positionMs, duration = durationMs) }
    }

    fun onTrackEnded() {
        // A track that played to the end is now fully on disk — refresh the offline set.
        refreshDownloaded()
        scope.launch { skipToNext() }
    }

    private fun addToHistory(track: Track) {
        _state.update { s ->
            val filtered = s.history.filter { it.id != track.id }
            s.copy(history = (listOf(track) + filtered).take(MAX_HISTORY))
        }
        scope.launch { storage.saveHistory(_state.value.history) }
    }

    /** Remember a track's metadata so it can be shown (and replayed) from the offline library later. */
    private fun addToLibrary(track: Track) {
        val before = _state.value.savedTracks
        // Nothing to do if this exact entry (same metadata) is already at the front.
        if (before.firstOrNull() == track) return
        _state.update { s ->
            val filtered = s.savedTracks.filterNot { it.id == track.id }
            s.copy(savedTracks = (listOf(track) + filtered).take(MAX_LIBRARY))
        }
        scope.launch { storage.saveSavedTracks(_state.value.savedTracks) }
    }

    private suspend fun persistQueue() {
        storage.saveQueue(_state.value.queue)
    }

    companion object {
        const val MAX_HISTORY = 50
        const val MAX_LIBRARY = 500

        @Volatile
        var instance: PlayerController? = null
            private set

        fun getInstance(context: Context): PlayerController {
            val appContext = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: PlayerController(
                    storage = AppDataStore(appContext),
                    downloadedIdsProvider = { AudioCache.fullyCachedTrackIds(appContext) },
                ).also { instance = it }
            }
        }
    }
}
