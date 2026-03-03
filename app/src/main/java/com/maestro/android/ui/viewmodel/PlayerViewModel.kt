package com.maestro.android.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.maestro.android.data.model.LoopMode
import com.maestro.android.data.model.PlayerState
import com.maestro.android.data.model.Track
import com.maestro.android.player.PlaybackService
import com.maestro.android.player.PlayerController
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlayerViewModel(private val context: Context) : ViewModel() {

    private val controller = PlayerController.getInstance(context)
    val state: StateFlow<PlayerState> = controller.state

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private var serviceStarted = false

    init {
        viewModelScope.launch {
            _serverUrl.value = controller.getServerUrl()
        }
        // Start the playback service so ExoPlayer is ready
        ensureServiceStarted()
    }

    private fun ensureServiceStarted() {
        if (!serviceStarted) {
            val intent = Intent(context, PlaybackService::class.java)
            context.startForegroundService(intent)
            serviceStarted = true
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            try {
                _searchResults.value = controller.api.search(query)
            } catch (e: Exception) {
                _searchError.value = e.message ?: "Search failed"
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun play(track: Track) {
        ensureServiceStarted()
        viewModelScope.launch { controller.playNow(track) }
    }

    fun playOrEnqueue(track: Track) {
        ensureServiceStarted()
        viewModelScope.launch { controller.play(track) }
    }

    fun enqueue(track: Track) {
        viewModelScope.launch { controller.enqueue(track) }
    }

    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun togglePlayPause() = controller.togglePlayPause()
    fun skip() { viewModelScope.launch { controller.skipToNext() } }
    fun stop() = controller.stop()
    fun removeFromQueue(index: Int) = controller.removeFromQueue(index)
    fun moveInQueue(from: Int, to: Int) = controller.moveInQueue(from, to)
    fun clearQueue() = controller.clearQueue()
    fun setVolume(volume: Float) = controller.setVolume(volume)
    fun setLoopMode(mode: LoopMode) = controller.setLoopMode(mode)
    fun cycleLoopMode() = controller.cycleLoopMode()

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            controller.updateServerUrl(url)
            _serverUrl.value = url
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(context.applicationContext) as T
        }
    }
}
