package com.maestro.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String? = null,
    val duration: Long? = null,
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
    val position: Long = 0L,
    val duration: Long = 0L
)
