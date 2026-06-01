package com.maestro.android.data.datastore

import com.maestro.android.data.model.LoopMode
import com.maestro.android.data.model.Track

interface PlayerStorage {
    suspend fun saveQueue(queue: List<Track>)
    suspend fun loadQueue(): List<Track>
    suspend fun saveHistory(history: List<Track>)
    suspend fun loadHistory(): List<Track>
    suspend fun saveSavedTracks(tracks: List<Track>)
    suspend fun loadSavedTracks(): List<Track>
    suspend fun saveVolume(volume: Float)
    suspend fun loadVolume(): Float
    suspend fun saveLoopMode(mode: LoopMode)
    suspend fun loadLoopMode(): LoopMode
    suspend fun saveAutoplaySimilar(enabled: Boolean)
    suspend fun loadAutoplaySimilar(): Boolean
}
