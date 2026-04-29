package com.maestro.android.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.maestro.android.data.model.LoopMode
import com.maestro.android.data.model.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "maestro_prefs")

class AppDataStore(private val context: Context) : PlayerStorage {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val KEY_QUEUE = stringPreferencesKey("queue")
        private val KEY_HISTORY = stringPreferencesKey("history")
        private val KEY_VOLUME = floatPreferencesKey("volume")
        private val KEY_LOOP_MODE = stringPreferencesKey("loop_mode")
    }

    override suspend fun saveQueue(queue: List<Track>) {
        context.dataStore.edit { it[KEY_QUEUE] = json.encodeToString(queue) }
    }

    override suspend fun loadQueue(): List<Track> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_QUEUE]?.let { json.decodeFromString<List<Track>>(it) } ?: emptyList()
        }.first()
    }

    override suspend fun saveHistory(history: List<Track>) {
        context.dataStore.edit { it[KEY_HISTORY] = json.encodeToString(history) }
    }

    override suspend fun loadHistory(): List<Track> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_HISTORY]?.let { json.decodeFromString<List<Track>>(it) } ?: emptyList()
        }.first()
    }

    override suspend fun saveVolume(volume: Float) {
        context.dataStore.edit { it[KEY_VOLUME] = volume }
    }

    override suspend fun loadVolume(): Float {
        return context.dataStore.data.map { it[KEY_VOLUME] ?: 1f }.first()
    }

    override suspend fun saveLoopMode(mode: LoopMode) {
        context.dataStore.edit { it[KEY_LOOP_MODE] = mode.name }
    }

    override suspend fun loadLoopMode(): LoopMode {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_LOOP_MODE]?.let { LoopMode.valueOf(it) } ?: LoopMode.OFF
        }.first()
    }
}
