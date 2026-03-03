package com.maestro.android.mcp

import com.maestro.android.data.model.Track
import com.maestro.android.player.PlayerController
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

object McpTools {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val controller: PlayerController?
        get() = PlayerController.instance

    val definitions: List<JsonObject> = listOf(
        toolDef(
            "play_music", "Search YouTube and play a track. If something is already playing, the new track is added to the queue.",
            requiredString("query", "Search query for YouTube")
        ),
        toolDef("pause_music", "Pause the currently playing track."),
        toolDef("resume_music", "Resume the paused track."),
        toolDef("skip_music", "Skip to the next track in the queue."),
        toolDef("stop_music", "Stop playback and clear the queue."),
        toolDef(
            "add_to_queue", "Search YouTube and add a track to the end of the queue without interrupting playback.",
            requiredString("query", "Search query for YouTube")
        ),
        toolDef(
            "remove_from_queue", "Remove a track from the queue by its 0-based index.",
            requiredInt("index", "0-based index of the track to remove")
        ),
        toolDef("get_music_state", "Get current playback state including track info, queue, volume, and loop mode."),
        toolDef(
            "set_volume", "Set the playback volume.",
            requiredNumber("volume", "Volume level from 0.0 (mute) to 1.0 (max)")
        ),
        toolDef(
            "get_recently_played", "Get a list of recently played tracks.",
            optionalInt("limit", "Number of tracks to return (1-50, default 10)")
        ),
        toolDef(
            "search_music", "Search YouTube for tracks without playing them. Returns up to 5 results.",
            requiredString("query", "Search query for YouTube")
        ),
    )

    suspend fun execute(name: String, args: JsonObject): String {
        val ctrl = controller ?: return "Error: Player not initialized"

        return try {
            when (name) {
                "play_music" -> {
                    val query = args.str("query") ?: return "Error: missing 'query'"
                    val results = ctrl.api.search(query, 1)
                    if (results.isEmpty()) return "No results found for: $query"
                    val track = results.first()
                    ctrl.play(track)
                    "Playing: ${track.title} by ${track.artist ?: "Unknown"}"
                }
                "pause_music" -> {
                    ctrl.pause()
                    "Paused"
                }
                "resume_music" -> {
                    ctrl.resume()
                    "Resumed"
                }
                "skip_music" -> {
                    ctrl.skipToNext()
                    "Skipped to next track"
                }
                "stop_music" -> {
                    ctrl.stop()
                    "Stopped and cleared queue"
                }
                "add_to_queue" -> {
                    val query = args.str("query") ?: return "Error: missing 'query'"
                    val results = ctrl.api.search(query, 1)
                    if (results.isEmpty()) return "No results found for: $query"
                    val track = results.first()
                    ctrl.enqueue(track)
                    "Added to queue: ${track.title} by ${track.artist ?: "Unknown"}"
                }
                "remove_from_queue" -> {
                    val index = args.int("index") ?: return "Error: missing 'index'"
                    val state = ctrl.state.value
                    if (index < 0 || index >= state.queue.size) return "Error: index out of range (queue has ${state.queue.size} tracks)"
                    val removed = state.queue[index]
                    ctrl.removeFromQueue(index)
                    "Removed from queue: ${removed.title}"
                }
                "get_music_state" -> {
                    val state = ctrl.state.value
                    json.encodeToString(buildJsonObject {
                        put("state", state.state.name.lowercase())
                        put("currentTrack", state.currentTrack?.let { trackToJson(it) } ?: JsonNull)
                        putJsonArray("queue") { state.queue.forEach { add(trackToJson(it)) } }
                        put("volume", state.volume)
                        put("loopMode", state.loopMode.name.lowercase())
                        put("position", state.position / 1000)
                        put("duration", state.duration / 1000)
                    })
                }
                "set_volume" -> {
                    val volume = args.float("volume") ?: return "Error: missing 'volume'"
                    ctrl.setVolume(volume)
                    "Volume set to ${(volume * 100).toInt()}%"
                }
                "get_recently_played" -> {
                    val limit = (args.int("limit") ?: 10).coerceIn(1, 50)
                    val history = ctrl.state.value.history.take(limit)
                    json.encodeToString(buildJsonObject {
                        putJsonArray("tracks") { history.forEach { add(trackToJson(it)) } }
                        put("total", history.size)
                    })
                }
                "search_music" -> {
                    val query = args.str("query") ?: return "Error: missing 'query'"
                    val results = ctrl.api.search(query, 5)
                    json.encodeToString(buildJsonObject {
                        putJsonArray("results") { results.forEach { add(trackToJson(it)) } }
                    })
                }
                else -> "Error: Unknown tool '$name'"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun trackToJson(track: Track): JsonObject = buildJsonObject {
        put("id", track.id)
        put("title", track.title)
        put("artist", track.artist ?: "Unknown")
        put("duration", track.duration ?: 0)
        put("url", track.url)
    }

    // --- Tool definition helpers ---

    private fun toolDef(name: String, description: String, vararg properties: Pair<String, JsonObject>): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("description", description)
            putJsonObject("inputSchema") {
                put("type", "object")
                if (properties.isNotEmpty()) {
                    putJsonObject("properties") {
                        properties.forEach { (k, v) -> put(k, v) }
                    }
                    putJsonArray("required") {
                        properties.filter { it.second.boolOrFalse("_required") }
                            .forEach { add(it.first) }
                    }
                }
            }
        }
    }

    private fun requiredString(name: String, description: String): Pair<String, JsonObject> {
        return name to buildJsonObject {
            put("type", "string")
            put("description", description)
            put("_required", true)
        }
    }

    private fun requiredInt(name: String, description: String): Pair<String, JsonObject> {
        return name to buildJsonObject {
            put("type", "integer")
            put("description", description)
            put("_required", true)
        }
    }

    private fun requiredNumber(name: String, description: String): Pair<String, JsonObject> {
        return name to buildJsonObject {
            put("type", "number")
            put("description", description)
            put("_required", true)
        }
    }

    private fun optionalInt(name: String, description: String): Pair<String, JsonObject> {
        return name to buildJsonObject {
            put("type", "integer")
            put("description", description)
        }
    }

    // --- JSON helpers ---

    private fun JsonObject.str(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitive?.intOrNull
    private fun JsonObject.float(key: String): Float? = get(key)?.jsonPrimitive?.floatOrNull
    private fun JsonObject.boolOrFalse(key: String): Boolean = get(key)?.jsonPrimitive?.booleanOrNull ?: false
}
