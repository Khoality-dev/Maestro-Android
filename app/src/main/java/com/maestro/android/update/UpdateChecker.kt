package com.maestro.android.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
private data class GithubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long = 0,
)

data class AvailableUpdate(
    val versionName: String,
    val tagName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val releaseNotes: String?,
)

class UpdateChecker(
    private val repoOwner: String = "Khoality-dev",
    private val repoName: String = "Maestro-Android",
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun fetchLatest(): GithubRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            json.decodeFromString<GithubRelease>(body)
        }
    }

    suspend fun checkForUpdate(currentVersion: String): AvailableUpdate? {
        val release = fetchLatest() ?: return null
        val latestVersion = release.tag_name.removePrefix("v")
        if (compareSemver(latestVersion, currentVersion) <= 0) return null

        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        return AvailableUpdate(
            versionName = latestVersion,
            tagName = release.tag_name,
            downloadUrl = apk.browser_download_url,
            sizeBytes = apk.size,
            releaseNotes = release.body,
        )
    }

    companion object {
        fun compareSemver(a: String, b: String): Int {
            val pa = a.removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }
            val pb = b.removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }
            val n = maxOf(pa.size, pb.size)
            for (i in 0 until n) {
                val ai = pa.getOrElse(i) { 0 }
                val bi = pb.getOrElse(i) { 0 }
                if (ai != bi) return ai.compareTo(bi)
            }
            return 0
        }
    }
}
