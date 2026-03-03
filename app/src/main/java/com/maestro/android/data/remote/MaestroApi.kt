package com.maestro.android.data.remote

import com.maestro.android.data.model.Track
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SearchResponse(val results: List<Track>)

@Serializable
data class ExtractResponse(
    val stream_url: String,
    val duration: Long? = null,
    val title: String? = null,
    val artist: String? = null
)

class MaestroApi(private val baseUrl: String) {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun search(query: String, limit: Int = 5): List<Track> {
        val response: SearchResponse = client.get("$baseUrl/search") {
            parameter("q", query)
            parameter("limit", limit)
        }.body()
        return response.results
    }

    suspend fun extractStreamUrl(videoId: String): ExtractResponse {
        return client.get("$baseUrl/extract") {
            parameter("id", videoId)
        }.body()
    }

    suspend fun healthCheck(): Boolean {
        return try {
            client.get("$baseUrl/health")
            true
        } catch (_: Exception) {
            false
        }
    }
}
