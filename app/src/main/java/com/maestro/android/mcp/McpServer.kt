package com.maestro.android.mcp

import android.util.Log
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import kotlinx.coroutines.*

object McpServer {

    private const val TAG = "McpServer"
    private const val PORT = 29170
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private lateinit var mcpServerInstance: Server

    fun start() {
        if (server != null) return

        mcpServerInstance = Server(
            serverInfo = Implementation(
                name = "Maestro Android",
                version = "1.0.0"
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                ),
            )
        )

        McpTools.registerAll(mcpServerInstance)

        server = embeddedServer(CIO, port = PORT) {
            routing {
                get("/health") {
                    call.respond(mapOf("status" to "ok", "name" to "Maestro Android", "version" to "1.0.0"))
                }
            }

            mcp {
                mcpServerInstance
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
    }
}
