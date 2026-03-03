package com.maestro.android.mcp

import android.util.Log
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import java.io.Writer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object McpServer {

    private const val TAG = "McpServer"
    private const val PORT = 29170
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // SSE sessions: sessionId -> outgoing channel
    private val sessions = ConcurrentHashMap<String, Channel<String>>()

    fun start() {
        if (server != null) return

        server = embeddedServer(Netty, port = PORT) {
            install(ContentNegotiation) { json(json) }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Delete)
            }

            routing {
                get("/health") {
                    call.respond(mapOf("status" to "ok", "name" to "Maestro Android", "version" to "1.0.0"))
                }

                // SSE transport: client connects here to receive events
                get("/sse") {
                    val sessionId = UUID.randomUUID().toString()
                    val channel = Channel<String>(Channel.BUFFERED)
                    sessions[sessionId] = channel

                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        // Send endpoint event so client knows where to POST
                        writeSseEvent(this, "endpoint", "/messages?sessionId=$sessionId")
                        flush()

                        try {
                            for (message in channel) {
                                writeSseEvent(this, "message", message)
                                flush()
                            }
                        } finally {
                            sessions.remove(sessionId)
                            channel.close()
                        }
                    }
                }

                // SSE transport: client sends JSON-RPC here
                post("/messages") {
                    val sessionId = call.request.queryParameters["sessionId"]
                    if (sessionId == null || !sessions.containsKey(sessionId)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid session")
                        return@post
                    }

                    val body = call.receiveText()
                    val response = handleJsonRpc(body)
                    if (response != null) {
                        sessions[sessionId]?.send(response)
                    }
                    call.respond(HttpStatusCode.Accepted)
                }

                // Streamable HTTP transport: single endpoint
                post("/mcp") {
                    val body = call.receiveText()
                    val response = handleJsonRpc(body)
                    if (response != null) {
                        call.respondText(response, ContentType.Application.Json)
                    } else {
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                server?.start(wait = false)
                Log.i(TAG, "MCP server started on port $PORT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MCP server: ${e.message}")
            }
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        sessions.values.forEach { it.close() }
        sessions.clear()
    }

    private fun writeSseEvent(writer: Writer, event: String, data: String) {
        writer.write("event: $event\n")
        // Split data by newlines for proper SSE format
        for (line in data.lines()) {
            writer.write("data: $line\n")
        }
        writer.write("\n")
    }

    private suspend fun handleJsonRpc(body: String): String? {
        val request = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return jsonRpcError(null, -32700, "Parse error")
        }

        val id = request["id"]
        val method = request["method"]?.jsonPrimitive?.contentOrNull ?: return null
        val params = request["params"]?.jsonObject

        return when (method) {
            "initialize" -> {
                val result = buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    putJsonObject("capabilities") {
                        putJsonObject("tools") {}
                    }
                    putJsonObject("serverInfo") {
                        put("name", "Maestro Android")
                        put("version", "1.0.0")
                    }
                }
                jsonRpcResult(id, result)
            }
            "notifications/initialized" -> null
            "tools/list" -> {
                val result = buildJsonObject {
                    putJsonArray("tools") {
                        McpTools.definitions.forEach { add(it) }
                    }
                }
                jsonRpcResult(id, result)
            }
            "tools/call" -> {
                val toolName = params?.get("name")?.jsonPrimitive?.contentOrNull
                val args = params?.get("arguments")?.jsonObject ?: buildJsonObject {}
                if (toolName == null) {
                    return jsonRpcError(id, -32602, "Missing tool name")
                }
                val toolResult = McpTools.execute(toolName, args)
                val result = buildJsonObject {
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", toolResult)
                        }
                    }
                }
                jsonRpcResult(id, result)
            }
            "ping" -> jsonRpcResult(id, buildJsonObject {})
            else -> jsonRpcError(id, -32601, "Method not found: $method")
        }
    }

    private fun jsonRpcResult(id: JsonElement?, result: JsonObject): String {
        return json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id) else put("id", JsonNull)
            put("result", result)
        })
    }

    private fun jsonRpcError(id: JsonElement?, code: Int, message: String): String {
        return json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id) else put("id", JsonNull)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        })
    }
}
