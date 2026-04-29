package com.maestro.android.player

import com.maestro.android.data.datastore.PlayerStorage
import com.maestro.android.data.model.LoopMode
import com.maestro.android.data.model.PlaybackState
import com.maestro.android.data.model.Track
import com.maestro.android.data.remote.ExtractResponse
import com.maestro.android.data.remote.MaestroApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerTest {

    @Test
    fun `play starts track when stopped`() = runTest {
        val api = FakeMaestroApi(extractResult = ExtractResponse(streamUrl = "https://s1", duration = 120.0))
        val controller = controller(api = api, scope = this)
        var playedUrl: String? = null
        var playedFromMs: Long = -1
        controller.onPlayUrl = { url, _, fromMs -> playedUrl = url; playedFromMs = fromMs }
        advanceUntilIdle()

        controller.play(track("v1"))
        advanceUntilIdle()

        assertEquals(PlaybackState.PLAYING, controller.state.value.state)
        assertEquals("v1", controller.state.value.currentTrack?.id)
        assertEquals("https://s1", playedUrl)
        assertEquals(0L, playedFromMs)
        assertEquals(false, api.extractCalls.single().refresh)
    }

    @Test
    fun `play enqueues when already playing`() = runTest {
        val controller = controller(scope = this)
        advanceUntilIdle()

        controller.play(track("v1"))
        advanceUntilIdle()
        controller.play(track("v2"))
        advanceUntilIdle()

        assertEquals("v1", controller.state.value.currentTrack?.id)
        assertEquals(listOf("v2"), controller.state.value.queue.map { it.id })
    }

    @Test
    fun `playNow replaces current track`() = runTest {
        val controller = controller(scope = this)
        advanceUntilIdle()

        controller.play(track("v1"))
        advanceUntilIdle()
        controller.playNow(track("v2"))
        advanceUntilIdle()

        assertEquals("v2", controller.state.value.currentTrack?.id)
    }

    @Test
    fun `onPlaybackError refreshes URL with refresh=true and resumes at saved position`() = runTest {
        val api = FakeMaestroApi(extractResult = ExtractResponse(streamUrl = "https://s1"))
        val now = longArrayOf(1_000_000L)
        val controller = controller(api = api, scope = this, clock = { now[0] })
        var lastUrl: String? = null
        var lastFromMs: Long = -1
        val cacheEvictions = mutableListOf<Triple<String, Long, Long>>()
        controller.onPlayUrl = { url, _, fromMs -> lastUrl = url; lastFromMs = fromMs }
        controller.onClearCacheForTrack = { id, posMs, durMs -> cacheEvictions += Triple(id, posMs, durMs) }
        advanceUntilIdle()

        controller.play(track("v1"))
        advanceUntilIdle()
        controller.updatePosition(45_000L, 200_000L)

        api.extractResult = ExtractResponse(streamUrl = "https://s2")
        controller.onPlaybackError(RuntimeException("boom"))
        advanceUntilIdle()

        // Two extract calls: initial (refresh=false), recovery (refresh=true)
        assertEquals(2, api.extractCalls.size)
        assertEquals(false, api.extractCalls[0].refresh)
        assertEquals(true, api.extractCalls[1].refresh)
        assertEquals("https://s2", lastUrl)
        assertEquals(45_000L, lastFromMs)
        // Corrupted cache tail for the current track must be evicted with the resume position
        // and duration so PlaybackService can keep the verified-good prefix.
        assertEquals(listOf(Triple("v1", 45_000L, 200_000L)), cacheEvictions)
    }

    @Test
    fun `onPlaybackError skips to next on tight retry loop`() = runTest {
        val now = longArrayOf(1_000_000L)
        val api = FakeMaestroApi()
        val controller = controller(api = api, scope = this, clock = { now[0] })
        val cacheEvictions = mutableListOf<String>()
        controller.onClearCacheForTrack = { id, _, _ -> cacheEvictions += id }
        advanceUntilIdle()

        controller.play(track("v1"))
        advanceUntilIdle()
        controller.enqueue(track("v2"))
        advanceUntilIdle()

        // First error allowed (>= 5s since last retry of 0).
        controller.onPlaybackError(null)
        advanceUntilIdle()
        assertEquals("v1", controller.state.value.currentTrack?.id)

        // Second error 1s later — should trip the guard and skip to v2.
        now[0] += 1_000L
        controller.onPlaybackError(null)
        advanceUntilIdle()

        assertEquals("v2", controller.state.value.currentTrack?.id)
        assertEquals(emptyList<String>(), controller.state.value.queue.map { it.id })
        // Only the first error path should have evicted; the guarded path skips straight to next.
        assertEquals(listOf("v1"), cacheEvictions)
    }

    @Test
    fun `skipToNext stops when loop OFF and queue empty`() = runTest {
        val controller = controller(scope = this)
        advanceUntilIdle()

        controller.play(track("v1"))
        advanceUntilIdle()
        controller.skipToNext()
        advanceUntilIdle()

        assertEquals(PlaybackState.STOPPED, controller.state.value.state)
        assertNull(controller.state.value.currentTrack)
        assertEquals(emptyList<String>(), controller.state.value.queue.map { it.id })
    }

    @Test
    fun `skipToNext replays current when loop ONE`() = runTest {
        val controller = controller(scope = this)
        advanceUntilIdle()

        controller.setLoopMode(LoopMode.ONE)
        controller.play(track("v1"))
        advanceUntilIdle()

        controller.skipToNext()
        advanceUntilIdle()

        assertEquals("v1", controller.state.value.currentTrack?.id)
        assertEquals(PlaybackState.PLAYING, controller.state.value.state)
    }

    @Test
    fun `skipToNext rotates current to back when loop QUEUE`() = runTest {
        val controller = controller(scope = this)
        advanceUntilIdle()

        controller.setLoopMode(LoopMode.QUEUE)
        controller.play(track("v1"))
        advanceUntilIdle()
        controller.enqueue(track("v2"))
        advanceUntilIdle()

        controller.skipToNext()
        advanceUntilIdle()

        assertEquals("v2", controller.state.value.currentTrack?.id)
        assertEquals(listOf("v1"), controller.state.value.queue.map { it.id })
    }

    @Test
    fun `setVolume clamps and persists`() = runTest {
        val storage = FakePlayerStorage()
        val controller = controller(storage = storage, scope = this)
        advanceUntilIdle()

        controller.setVolume(2f)
        advanceUntilIdle()
        assertEquals(1f, controller.state.value.volume, 0f)
        assertEquals(1f, storage.savedVolume, 0f)

        controller.setVolume(-0.5f)
        advanceUntilIdle()
        assertEquals(0f, controller.state.value.volume, 0f)
        assertEquals(0f, storage.savedVolume, 0f)
    }

    @Test
    fun `cycleLoopMode rotates OFF QUEUE ONE OFF`() = runTest {
        val controller = controller(scope = this)
        advanceUntilIdle()

        assertEquals(LoopMode.OFF, controller.state.value.loopMode)
        controller.cycleLoopMode()
        assertEquals(LoopMode.QUEUE, controller.state.value.loopMode)
        controller.cycleLoopMode()
        assertEquals(LoopMode.ONE, controller.state.value.loopMode)
        controller.cycleLoopMode()
        assertEquals(LoopMode.OFF, controller.state.value.loopMode)
    }

    @Test
    fun `pause then resume transitions state`() = runTest {
        val controller = controller(scope = this)
        advanceUntilIdle()

        controller.play(track("v1"))
        advanceUntilIdle()

        controller.pause()
        assertEquals(PlaybackState.PAUSED, controller.state.value.state)
        controller.resume()
        assertEquals(PlaybackState.PLAYING, controller.state.value.state)
    }

    private fun TestScope.controller(
        storage: PlayerStorage = FakePlayerStorage(),
        api: MaestroApi = FakeMaestroApi(),
        scope: TestScope = this,
        clock: () -> Long = { 1_000_000L },
    ): PlayerController = PlayerController(
        storage = storage,
        apiFactory = { api },
        scope = scope,
        clock = clock,
    )

    private fun track(id: String) = Track(id = id, title = "t-$id")
}

private class FakePlayerStorage : PlayerStorage {
    var savedVolume: Float = 1f
    var savedLoopMode: LoopMode = LoopMode.OFF
    var savedQueue: List<Track> = emptyList()
    var savedHistory: List<Track> = emptyList()

    override suspend fun saveQueue(queue: List<Track>) { savedQueue = queue }
    override suspend fun loadQueue(): List<Track> = savedQueue
    override suspend fun saveHistory(history: List<Track>) { savedHistory = history }
    override suspend fun loadHistory(): List<Track> = savedHistory
    override suspend fun saveVolume(volume: Float) { savedVolume = volume }
    override suspend fun loadVolume(): Float = savedVolume
    override suspend fun saveLoopMode(mode: LoopMode) { savedLoopMode = mode }
    override suspend fun loadLoopMode(): LoopMode = savedLoopMode
}

private class FakeMaestroApi(
    var extractResult: ExtractResponse = ExtractResponse(streamUrl = "https://default", duration = 60.0),
    var searchResult: List<Track> = emptyList(),
) : MaestroApi() {
    data class ExtractCall(val videoId: String, val refresh: Boolean)
    val extractCalls = mutableListOf<ExtractCall>()

    override suspend fun extractStreamUrl(videoId: String, refresh: Boolean): ExtractResponse {
        extractCalls.add(ExtractCall(videoId, refresh))
        return extractResult
    }

    override suspend fun search(query: String, limit: Int): List<Track> = searchResult
}
