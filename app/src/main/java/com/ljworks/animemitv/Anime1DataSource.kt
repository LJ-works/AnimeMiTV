package com.ljworks.animemitv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface Anime1DataSource {
    suspend fun fetchAnimeList(): List<Anime>
    suspend fun fetchCurrentSeason(): AnimeSeason = error("当前数据源不支持季度新番")
    suspend fun fetchSeasonSchedule(season: AnimeSeason): AnimeSchedule = error("当前数据源不支持季度新番")
    suspend fun fetchEpisodes(anime: Anime, pageUrl: String = anime.categoryUrl): EpisodePage
    suspend fun resolvePlayback(anime: Anime, episode: Episode): PlayableSource
}

class Anime1HttpDataSource(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : Anime1DataSource {
    private val cookies = linkedMapOf<String, String>()

    override suspend fun fetchAnimeList(): List<Anime> = withContext(Dispatchers.IO) {
        parseAnimeList(get("https://anime1.me/animelist.json", "https://anime1.me/"))
    }

    override suspend fun fetchCurrentSeason(): AnimeSeason = withContext(Dispatchers.IO) {
        parseCurrentSeason(get("https://anime1.me/", "https://anime1.me/"))
    }

    override suspend fun fetchSeasonSchedule(season: AnimeSeason): AnimeSchedule = withContext(Dispatchers.IO) {
        parseSeasonSchedule(get(season.url, "https://anime1.me/"), season.url)
    }

    override suspend fun fetchEpisodes(anime: Anime, pageUrl: String): EpisodePage = withContext(Dispatchers.IO) {
        parseCategoryPage(get(pageUrl, anime.categoryUrl), pageUrl)
    }

    override suspend fun resolvePlayback(anime: Anime, episode: Episode): PlayableSource = withContext(Dispatchers.IO) {
        val request = episode.apiRequest ?: throw IOException("该剧集没有可用的播放请求")
        val response = postApi(request, episode.pageUrl)
        val source = parsePlaybackResponse(response) ?: throw IOException("播放地址为空")
        source.copy(headers = cookieHeader()?.let { mapOf("Cookie" to it) }.orEmpty())
    }

    private fun get(url: String, referer: String): String {
        val connection = open(url, referer)
        return read(connection)
    }

    private fun postApi(apiRequest: String, referer: String): String {
        val connection = open("https://v.anime1.me/api", referer).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        connection.outputStream.bufferedWriter().use { it.write("d=$apiRequest") }
        return read(connection)
    }

    private fun open(url: String, referer: String): HttpURLConnection =
        connectionFactory(URL(url)).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/json")
            setRequestProperty("Origin", "https://anime1.me")
            setRequestProperty("Referer", referer)
            cookieHeader()?.let { setRequestProperty("Cookie", it) }
        }

    private fun read(connection: HttpURLConnection): String {
        try {
            val status = connection.responseCode
            rememberCookies(connection)
            if (status !in 200..299) throw IOException("Anime1 请求失败：HTTP $status")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun rememberCookies(connection: HttpURLConnection) {
        connection.headerFields.entries
            .firstOrNull { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
            ?.value.orEmpty()
            .forEach { value ->
                val pair = value.substringBefore(';')
                val separator = pair.indexOf('=')
                if (separator > 0) cookies[pair.substring(0, separator)] = pair.substring(separator + 1)
            }
    }

    private fun cookieHeader(): String? = cookies.entries
        .joinToString("; ") { (name, value) -> "$name=$value" }
        .takeIf(String::isNotEmpty)

    private companion object {
        const val USER_AGENT = "AnimeMiTV/1.0 (Android TV)"
    }
}
