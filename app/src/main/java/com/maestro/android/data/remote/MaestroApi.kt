package com.maestro.android.data.remote

import com.maestro.android.data.model.Track
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SearchResponse(val results: List<Track>)

@Serializable
data class ExtractResponse(
    @SerialName("stream_url") val streamUrl: String,
    val duration: Double? = null,
    val title: String? = null,
    val artist: String? = null
)

open class MaestroApi(
    private val baseUrl: String,
    engine: HttpClientEngine? = null,
) {

    private val client = if (engine != null) {
        HttpClient(engine) {
            install(ContentNegotiation) { json(JSON_CONFIG) }
        }
    } else {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(JSON_CONFIG) }
        }
    }

    open suspend fun search(query: String, limit: Int = 5): List<Track> {
        val response: SearchResponse = client.get("$baseUrl/search") {
            parameter("q", query)
            parameter("limit", limit)
        }.body()
        return response.results
    }

    open suspend fun extractStreamUrl(videoId: String, refresh: Boolean = false): ExtractResponse {
        return client.get("$baseUrl/extract") {
            parameter("id", videoId)
            if (refresh) parameter("refresh", true)
        }.body()
    }

    open suspend fun healthCheck(): Boolean {
        return try {
            client.get("$baseUrl/health").status.isSuccess()
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private val JSON_CONFIG = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
