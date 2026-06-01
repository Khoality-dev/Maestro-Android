package com.maestro.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String? = null,
    val duration: Double? = null,
    val thumbnail: String? = null,
    val url: String = ""
)

@Serializable
enum class PlaybackState {
    STOPPED, PLAYING, PAUSED
}

@Serializable
enum class LoopMode {
    OFF, QUEUE, ONE
}

@Serializable
data class PlayerState(
    val state: PlaybackState = PlaybackState.STOPPED,
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val volume: Float = 1f,
    val history: List<Track> = emptyList(),
    val loopMode: LoopMode = LoopMode.OFF,
    // "Radio": when the queue runs dry, keep playing songs similar to the last one.
    val autoplaySimilar: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    // Every track ever played, newest first (metadata only; audio lives in the ExoPlayer cache).
    val savedTracks: List<Track> = emptyList(),
    // IDs of tracks whose audio is fully on disk and therefore playable with no internet.
    val downloadedIds: Set<String> = emptySet()
) {
    // Saved songs that are completely downloaded — the offline-ready library.
    val offlineLibrary: List<Track> get() = savedTracks.filter { it.id in downloadedIds }
}
