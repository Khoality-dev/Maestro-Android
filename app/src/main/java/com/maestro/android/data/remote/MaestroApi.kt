package com.maestro.android.data.remote

import com.maestro.android.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class ExtractResponse(
    val streamUrl: String,
    val duration: Double? = null,
    val title: String? = null,
    val artist: String? = null,
)

open class MaestroApi {

    open suspend fun search(query: String, limit: Int = 5): List<Track> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        val info = SearchInfo.getInfo(service, handler)
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .take(limit)
            .mapNotNull { it.toTrack() }
    }

    open suspend fun extractStreamUrl(videoId: String, refresh: Boolean = false): ExtractResponse =
        withContext(Dispatchers.IO) {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            val audio = info.audioStreams
                ?.maxByOrNull { stream ->
                    val avg = stream.averageBitrate
                    if (avg > 0) avg else stream.bitrate
                }
                ?: throw IllegalStateException("No audio stream available for $videoId")
            ExtractResponse(
                streamUrl = audio.content,
                duration = info.duration.toDouble().takeIf { it > 0 },
                title = info.name,
                artist = info.uploaderName,
            )
        }

    /** Related / "similar" songs for a video, used for recommendations and radio autoplay. */
    open suspend fun getRelated(videoId: String, limit: Int = 20): List<Track> =
        withContext(Dispatchers.IO) {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            (info.relatedItems ?: emptyList())
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toTrack() }
                .filter { it.id != videoId }
                .distinctBy { it.id }
                .take(limit)
        }

    private fun StreamInfoItem.toTrack(): Track? {
        val videoId = url?.let { extractVideoId(it) } ?: return null
        return Track(
            id = videoId,
            title = name ?: "",
            artist = uploaderName,
            duration = duration.toDouble().takeIf { it > 0 },
            thumbnail = thumbnails?.firstOrNull()?.url,
            url = url ?: "",
        )
    }

    private fun extractVideoId(url: String): String? {
        val vMatch = Regex("[?&]v=([\\w-]{11})").find(url)
        if (vMatch != null) return vMatch.groupValues[1]
        val shortMatch = Regex("youtu\\.be/([\\w-]{11})").find(url)
        return shortMatch?.groupValues?.get(1)
    }
}
