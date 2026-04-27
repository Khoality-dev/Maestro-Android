package com.maestro.android.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaestroApiTest {

    private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `search hits search endpoint with query and limit`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            capturedUrls += request.url.toString()
            respond(
                content = """{"results":[{"id":"abc","title":"Song"}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeader,
            )
        }
        val api = MaestroApi("http://test", engine)

        val results = api.search("hello", limit = 3)

        assertEquals(1, results.size)
        assertEquals("abc", results[0].id)
        assertEquals("Song", results[0].title)
        assertEquals(1, capturedUrls.size)
        val url = capturedUrls.single()
        assertTrue(url, url.startsWith("http://test/search"))
        assertTrue(url, url.contains("q=hello"))
        assertTrue(url, url.contains("limit=3"))
    }

    @Test
    fun `extractStreamUrl omits refresh param by default`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            capturedUrls += request.url.toString()
            respond(
                content = """{"stream_url":"https://cdn/stream","duration":120.5}""",
                status = HttpStatusCode.OK,
                headers = jsonHeader,
            )
        }
        val api = MaestroApi("http://test", engine)

        val response = api.extractStreamUrl("vid42")

        assertEquals("https://cdn/stream", response.streamUrl)
        assertEquals(120.5, response.duration!!, 0.0001)
        val url = capturedUrls.single()
        assertTrue(url, url.contains("id=vid42"))
        assertTrue("refresh should be absent: $url", !url.contains("refresh"))
    }

    @Test
    fun `extractStreamUrl sends refresh=true when requested`() = runTest {
        val capturedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            capturedUrls += request.url.toString()
            respond(
                content = """{"stream_url":"https://cdn/fresh"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeader,
            )
        }
        val api = MaestroApi("http://test", engine)

        api.extractStreamUrl("vid42", refresh = true)

        val url = capturedUrls.single()
        assertTrue(url, url.contains("id=vid42"))
        assertTrue("refresh should be present: $url", url.contains("refresh=true"))
    }

    @Test
    fun `extract response tolerates missing optional fields`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"stream_url":"https://cdn/x"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeader,
            )
        }
        val api = MaestroApi("http://test", engine)

        val response = api.extractStreamUrl("vid")

        assertEquals("https://cdn/x", response.streamUrl)
        assertNull(response.duration)
        assertNull(response.title)
        assertNull(response.artist)
    }

    @Test
    fun `healthCheck returns true on 200 and false on error`() = runTest {
        val okEngine = MockEngine { respond("ok", HttpStatusCode.OK) }
        assertEquals(true, MaestroApi("http://test", okEngine).healthCheck())

        val badEngine = MockEngine { respond("bad", HttpStatusCode.InternalServerError) }
        assertEquals(false, MaestroApi("http://test", badEngine).healthCheck())
    }
}
