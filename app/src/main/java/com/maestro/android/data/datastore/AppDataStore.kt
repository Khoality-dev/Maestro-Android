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

class AppDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val KEY_QUEUE = stringPreferencesKey("queue")
        private val KEY_HISTORY = stringPreferencesKey("history")
        private val KEY_VOLUME = floatPreferencesKey("volume")
        private val KEY_LOOP_MODE = stringPreferencesKey("loop_mode")
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
    }

    suspend fun saveQueue(queue: List<Track>) {
        context.dataStore.edit { it[KEY_QUEUE] = json.encodeToString(queue) }
    }

    suspend fun loadQueue(): List<Track> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_QUEUE]?.let { json.decodeFromString<List<Track>>(it) } ?: emptyList()
        }.first()
    }

    suspend fun saveHistory(history: List<Track>) {
        context.dataStore.edit { it[KEY_HISTORY] = json.encodeToString(history) }
    }

    suspend fun loadHistory(): List<Track> {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_HISTORY]?.let { json.decodeFromString<List<Track>>(it) } ?: emptyList()
        }.first()
    }

    suspend fun saveVolume(volume: Float) {
        context.dataStore.edit { it[KEY_VOLUME] = volume }
    }

    suspend fun loadVolume(): Float {
        return context.dataStore.data.map { it[KEY_VOLUME] ?: 1f }.first()
    }

    suspend fun saveLoopMode(mode: LoopMode) {
        context.dataStore.edit { it[KEY_LOOP_MODE] = mode.name }
    }

    suspend fun loadLoopMode(): LoopMode {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_LOOP_MODE]?.let { LoopMode.valueOf(it) } ?: LoopMode.OFF
        }.first()
    }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun loadServerUrl(): String {
        return context.dataStore.data.map { it[KEY_SERVER_URL] ?: "" }.first()
    }

    suspend fun isServerConfigured(): Boolean {
        return context.dataStore.data.map { it[KEY_SERVER_URL] != null }.first()
    }
}
