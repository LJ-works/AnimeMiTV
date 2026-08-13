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
    fun parsesCurrentSeasonAndSevenDayScheduleIncludingUnavailableEntries() {
        val home = """
            <header id="masthead"><a href="https://anime1.me/2026%e5%b9%b4%e5%a4%8f%e5%ad%a3%e6%96%b0%e7%95%aa">2026年夏季新番</a></header>
        """.trimIndent()
        val season = """
            <table><thead><tr><th>日</th><th>一</th><th>二</th><th>三</th><th>四</th><th>五</th><th>六</th></tr></thead>
            <tbody><tr>
                <td>无资源动画</td><td><a href="/?cat=1933">有资源动画</a></td><td></td><td></td><td></td><td></td><td></td>
            </tr></tbody></table>
        """.trimIndent()

        assertEquals("2026年夏季新番", parseCurrentSeason(home).label)
        val schedule = parseSeasonSchedule(season, "https://anime1.me/season")
        assertEquals(7, schedule.days.size)
        assertEquals("无资源动画", schedule.days[0].single().title)
        assertEquals(null, schedule.days[0].single().categoryUrl)
        assertEquals("有资源动画", schedule.days[1].single().title)
        assertEquals("https://anime1.me/?cat=1933", schedule.days[1].single().categoryUrl)
    }

    @Test
    fun parsesCurrentSeasonFromMastheadInsteadOfEarlierSeasonLink() {
        val home = """
            <article><a href="/2025年秋季新番">2025年秋季新番</a></article>
            <header id="masthead"><a href="/2026年夏季新番">2026年夏季新番</a></header>
        """.trimIndent()

        val season = parseCurrentSeason(home)

        assertEquals("2026年夏季新番", season.label)
        assertEquals("https://anime1.me/2026年夏季新番", season.url)
    }

    @Test
    fun generatesCurrentAndTwentyPreviousSeasonsInNewestFirstOrder() {
        val seasons = precedingSeasons(AnimeSeason("2026年冬季新番", "https://anime1.me/current"))

        assertEquals(21, seasons.size)
        assertEquals("2026年冬季新番", seasons.first().label)
        assertEquals("2025年秋季新番", seasons[1].label)
        assertEquals("2021年冬季新番", seasons.last().label)
    }

    @Test
    fun seasonDiscoveryPreservesDiscoveredCurrentSeasonUrl() {
        val current = AnimeSeason("2026年夏季新番", "https://anime1.me/current-season")

        val seasons = precedingSeasons(current)

        assertEquals(current, seasons.first())
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
