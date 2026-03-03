package com.maestro.android.mcp

import com.maestro.android.data.model.Track
import com.maestro.android.player.PlayerController
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
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

    fun registerAll(server: Server) {
        server.addTool(
            name = "play_music",
            description = "Search YouTube and play a track. If something is already playing, the new track is added to the queue.",
            inputSchema = toolSchema(requiredString("query", "Search query for YouTube")),
        ) { request ->
            val ctrl = controller ?: return@addTool errorResult("Player not initialized")
            val query = request.arguments["query"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool errorResult("Missing 'query'")
            val results = ctrl.api.search(query, 1)
            if (results.isEmpty()) return@addTool textResult("No results found for: $query")
            val track = results.first()
            ctrl.play(track)
            textResult("Playing: ${track.title} by ${track.artist ?: "Unknown"}")
        }

        server.addTool(
            name = "pause_music",
            description = "Pause the currently playing track.",
            inputSchema = toolSchema(),
        ) {
            controller?.pause() ?: return@addTool errorResult("Player not initialized")
            textResult("Paused")
        }

        server.addTool(
            name = "resume_music",
            description = "Resume the paused track.",
            inputSchema = toolSchema(),
        ) {
            controller?.resume() ?: return@addTool errorResult("Player not initialized")
            textResult("Resumed")
        }

        server.addTool(
            name = "skip_music",
            description = "Skip to the next track in the queue.",
            inputSchema = toolSchema(),
        ) {
            val ctrl = controller ?: return@addTool errorResult("Player not initialized")
            ctrl.skipToNext()
            textResult("Skipped to next track")
        }

        server.addTool(
            name = "stop_music",
            description = "Stop playback and clear the queue.",
            inputSchema = toolSchema(),
        ) {
            controller?.stop() ?: return@addTool errorResult("Player not initialized")
            textResult("Stopped and cleared queue")
        }

        server.addTool(
            name = "add_to_queue",
            description = "Search YouTube and add a track to the end of the queue without interrupting playback.",
            inputSchema = toolSchema(requiredString("query", "Search query for YouTube")),
        ) { request ->
            val ctrl = controller ?: return@addTool errorResult("Player not initialized")
            val query = request.arguments["query"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool errorResult("Missing 'query'")
            val results = ctrl.api.search(query, 1)
            if (results.isEmpty()) return@addTool textResult("No results found for: $query")
            val track = results.first()
            ctrl.enqueue(track)
            textResult("Added to queue: ${track.title} by ${track.artist ?: "Unknown"}")
        }

        server.addTool(
            name = "remove_from_queue",
            description = "Remove a track from the queue by its 0-based index.",
            inputSchema = toolSchema(requiredInt("index", "0-based index of the track to remove")),
        ) { request ->
            val ctrl = controller ?: return@addTool errorResult("Player not initialized")
            val index = request.arguments["index"]?.jsonPrimitive?.intOrNull
                ?: return@addTool errorResult("Missing 'index'")
            val state = ctrl.state.value
            if (index < 0 || index >= state.queue.size) {
                return@addTool errorResult("Index out of range (queue has ${state.queue.size} tracks)")
            }
            val removed = state.queue[index]
            ctrl.removeFromQueue(index)
            textResult("Removed from queue: ${removed.title}")
        }

        server.addTool(
            name = "get_music_state",
            description = "Get current playback state including track info, queue, volume, and loop mode.",
            inputSchema = toolSchema(),
        ) {
            val ctrl = controller ?: return@addTool errorResult("Player not initialized")
            val state = ctrl.state.value
            textResult(json.encodeToString(buildJsonObject {
                put("state", state.state.name.lowercase())
                put("currentTrack", state.currentTrack?.let { trackToJson(it) } ?: JsonNull)
                putJsonArray("queue") { state.queue.forEach { add(trackToJson(it)) } }
                put("volume", state.volume)
                put("loopMode", state.loopMode.name.lowercase())
                put("position", state.position / 1000)
                put("duration", state.duration / 1000)
            }))
        }

        server.addTool(
            name = "set_volume",
            description = "Set the playback volume.",
            inputSchema = toolSchema(requiredNumber("volume", "Volume level from 0.0 (mute) to 1.0 (max)")),
        ) { request ->
            val ctrl = controller ?: return@addTool errorResult("Player not initialized")
            val volume = request.arguments["volume"]?.jsonPrimitive?.floatOrNull
                ?: return@addTool errorResult("Missing 'volume'")
            ctrl.setVolume(volume)
            textResult("Volume set to ${(volume * 100).toInt()}%")
        }

        server.addTool(
            name = "get_recently_played",
            description = "Get a list of recently played tracks.",
            inputSchema = toolSchema(optionalInt("limit", "Number of tracks to return (1-50, default 10)")),
        ) { request ->
            val ctrl = controller ?: return@addTool errorResult("Player not initialized")
            val limit = (request.arguments["limit"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, 50)
            val history = ctrl.state.value.history.take(limit)
            textResult(json.encodeToString(buildJsonObject {
                putJsonArray("tracks") { history.forEach { add(trackToJson(it)) } }
                put("total", history.size)
            }))
        }

        server.addTool(
            name = "search_music",
            description = "Search YouTube for tracks without playing them. Returns up to 5 results.",
            inputSchema = toolSchema(requiredString("query", "Search query for YouTube")),
        ) { request ->
            val ctrl = controller ?: return@addTool errorResult("Player not initialized")
            val query = request.arguments["query"]?.jsonPrimitive?.contentOrNull
                ?: return@addTool errorResult("Missing 'query'")
            val results = ctrl.api.search(query, 5)
            textResult(json.encodeToString(buildJsonObject {
                putJsonArray("results") { results.forEach { add(trackToJson(it)) } }
            }))
        }
    }

    // --- Helpers ---

    private fun textResult(text: String) = CallToolResult(content = listOf(TextContent(text)))
    private fun errorResult(msg: String) = CallToolResult(content = listOf(TextContent("Error: $msg")), isError = true)

    private fun trackToJson(track: Track): JsonObject = buildJsonObject {
        put("id", track.id)
        put("title", track.title)
        put("artist", track.artist ?: "Unknown")
        put("duration", track.duration ?: 0)
        put("url", track.url)
    }

    // --- Schema helpers ---

    private fun toolSchema(vararg properties: Pair<String, JsonObject>): Tool.Input {
        return Tool.Input(
            properties = buildJsonObject {
                properties.forEach { (k, v) -> put(k, v) }
            },
            required = properties.filter { it.second.boolOrFalse("_required") }.map { it.first }
        )
    }

    private fun requiredString(name: String, description: String) = name to buildJsonObject {
        put("type", "string"); put("description", description); put("_required", true)
    }
    private fun requiredInt(name: String, description: String) = name to buildJsonObject {
        put("type", "integer"); put("description", description); put("_required", true)
    }
    private fun requiredNumber(name: String, description: String) = name to buildJsonObject {
        put("type", "number"); put("description", description); put("_required", true)
    }
    private fun optionalInt(name: String, description: String) = name to buildJsonObject {
        put("type", "integer"); put("description", description)
    }

    private fun JsonObject.boolOrFalse(key: String): Boolean = get(key)?.jsonPrimitive?.booleanOrNull ?: false
}
