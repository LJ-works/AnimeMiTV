package com.ljworks.animemitv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class AnimeDataTest {
    @Test
    fun parsesAnimeListAndFiltersExternalAdultEntries() {
        val json = javaClass.getResource("/animelist.json")!!.readText()

        val result = parseAnimeList(json)

        assertEquals(2, result.size)
        assertEquals(1933, result[0].id)
        assertEquals("相反的你和我 第二季", result[0].title)
        assertEquals("夏", result[0].season)
        assertEquals("另一部动画", result[1].title)
    }

    @Test
    fun parsesCategoryEpisodesAndNextPage() {
        val html = javaClass.getResource("/category-page-1.html")!!.readText()

        val result = parseCategoryPage(html, "https://anime1.me/?cat=1933")

        assertEquals(listOf("动画 [02]", "动画 [01]"), result.episodes.map { it.title })
        assertEquals("episode-2", result.episodes[0].videoId)
        assertEquals("%7B%22c%22%3A%221933%22%2C%22e%22%3A%222b%22%7D", result.episodes[0].apiRequest)
        assertEquals("https://anime1.me/?cat=1933", result.episodes[0].sourcePageUrl)
        assertEquals("https://anime1.me/category/demo/page/2", result.nextPageUrl)
    }

    @Test
    fun playbackRequestCarriesOriginRefererAndResponseCookies() {
        lateinit var connection: FakeHttpURLConnection
        val dataSource = Anime1HttpDataSource { url ->
            FakeHttpURLConnection(
                url,
                javaClass.getResource("/playback-response.json")!!.readText(),
                listOf("h=token; Path=/"),
            ).also { connection = it }
        }
        val anime = Anime(1933, "动画", "连载中", "2026", "夏", "")
        val episode = Episode(
            id = "1",
            title = "动画 [01]",
            pageUrl = "https://anime1.me/1",
            apiRequest = "%7B%22c%22%3A%221933%22%7D",
            videoId = "episode-1",
            tServer = "pt2",
        )

        val source = runBlocking { dataSource.resolvePlayback(anime, episode) }

        assertEquals("d=%7B%22c%22%3A%221933%22%7D", connection.requestBody.toString())
        assertEquals("https://anime1.me", connection.requestHeaders["Origin"])
        assertEquals("https://anime1.me/1", connection.requestHeaders["Referer"])
        assertEquals("h=token", source.headers["Cookie"])
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val responseBody: String,
        private val responseCookies: List<String>,
    ) : HttpURLConnection(url) {
        val requestHeaders = mutableMapOf<String, String>()
        val requestBody = ByteArrayOutputStream()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = HTTP_OK
        override fun getInputStream() = ByteArrayInputStream(responseBody.toByteArray())
        override fun getOutputStream(): OutputStream = requestBody
        override fun setRequestProperty(key: String, value: String) {
            requestHeaders[key] = value
        }
        override fun getHeaderFields(): MutableMap<String, MutableList<String>> =
            mutableMapOf("Set-Cookie" to responseCookies.toMutableList())
    }
}
