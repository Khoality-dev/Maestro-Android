package com.maestro.android.player

import android.content.Context
import android.util.Log
import com.maestro.android.data.datastore.AppDataStore
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

class PlayerController(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dataStore = AppDataStore(context)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var _api: MaestroApi? = null
    val api: MaestroApi get() = _api ?: throw IllegalStateException("API not initialized")

    var onPlayUrl: ((String, Track) -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onVolumeChange: ((Float) -> Unit)? = null

    init {
        scope.launch {
            val serverUrl = dataStore.loadServerUrl()
            _api = MaestroApi(serverUrl)
            val queue = dataStore.loadQueue()
            val history = dataStore.loadHistory()
            val volume = dataStore.loadVolume()
            val loopMode = dataStore.loadLoopMode()
            _state.update { it.copy(queue = queue, history = history, volume = volume, loopMode = loopMode) }
        }
    }

    suspend fun updateServerUrl(url: String) {
        dataStore.saveServerUrl(url)
        _api = MaestroApi(url)
    }

    suspend fun getServerUrl(): String {
        return dataStore.loadServerUrl()
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
                    duration = (updatedTrack.duration ?: 0L) * 1000
                )
            }
            addToHistory(updatedTrack)
            onPlayUrl?.invoke(extracted.stream_url, updatedTrack)
        } catch (e: Exception) {
            Log.e("PlayerController", "Failed to start track: ${e.message}")
            // Skip to next if extraction fails
            skipToNext()
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
                } else {
                    stop()
                }
            }
        }
        persistQueue()
    }

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
        scope.launch { dataStore.saveVolume(clamped) }
    }

    fun setLoopMode(mode: LoopMode) {
        _state.update { it.copy(loopMode = mode) }
        scope.launch { dataStore.saveLoopMode(mode) }
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
        scope.launch { skipToNext() }
    }

    private fun addToHistory(track: Track) {
        _state.update { s ->
            val filtered = s.history.filter { it.id != track.id }
            s.copy(history = (listOf(track) + filtered).take(MAX_HISTORY))
        }
        scope.launch { dataStore.saveHistory(_state.value.history) }
    }

    private suspend fun persistQueue() {
        dataStore.saveQueue(_state.value.queue)
    }

    companion object {
        const val MAX_HISTORY = 50

        @Volatile
        var instance: PlayerController? = null
            private set

        fun getInstance(context: Context): PlayerController {
            return instance ?: synchronized(this) {
                instance ?: PlayerController(context.applicationContext).also { instance = it }
            }
        }
    }
}
