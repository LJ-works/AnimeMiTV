package com.ljworks.animemitv

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup

private const val ANIME_PAGE_SIZE = 20

data class Anime(
    val id: Int,
    val title: String,
    val episodeStatus: String,
    val year: String,
    val season: String,
    val fansub: String,
) {
    val categoryUrl: String get() = "https://anime1.me/?cat=$id"
}

data class Episode(
    val id: String,
    val title: String,
    val pageUrl: String,
    val apiRequest: String?,
    val videoId: String?,
    val tServer: String?,
    val sourcePageUrl: String = pageUrl,
)

data class EpisodePage(
    val episodes: List<Episode>,
    val nextPageUrl: String?,
)

data class PlayableSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

data class AnimePage(
    val items: List<Anime>,
    val pageIndex: Int,
    val totalPages: Int,
) {
    val hasPrevious: Boolean get() = pageIndex > 0
    val hasNext: Boolean get() = pageIndex < totalPages - 1
}

fun List<Anime>.page(index: Int, pageSize: Int = ANIME_PAGE_SIZE): AnimePage {
    require(pageSize > 0) { "pageSize must be positive" }
    val totalPages = maxOf(1, (size + pageSize - 1) / pageSize)
    val pageIndex = index.coerceIn(0, totalPages - 1)
    val from = (pageIndex * pageSize).coerceAtMost(size)
    val to = (from + pageSize).coerceAtMost(size)
    return AnimePage(subList(from, to), pageIndex, totalPages)
}

fun parseAnimeList(json: String): List<Anime> {
    val rows = JSONArray(json)
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONArray(index) ?: continue
            val id = row.optInt(0, 0)
            if (id == 0 || row.length() < 6) continue
            add(
                Anime(
                    id = id,
                    title = row.requiredString(1),
                    episodeStatus = row.requiredString(2),
                    year = row.requiredString(3),
                    season = row.requiredString(4),
                    fansub = row.requiredString(5),
                )
            )
        }
    }
}

fun parseCategoryPage(html: String, pageUrl: String): EpisodePage {
    val document = Jsoup.parse(html, pageUrl)
    val episodes = document.select("article").mapNotNull { article ->
        val link = article.selectFirst("a[rel=bookmark][href]") ?: return@mapNotNull null
        val title = article.selectFirst(".entry-title")?.text()?.trim()
            ?: link.text().trim().takeIf(String::isNotEmpty)
            ?: return@mapNotNull null
        val video = article.selectFirst("video[data-apireq]")
        val id = article.id().removePrefix("post-").ifBlank { link.absUrl("href") }
        Episode(
            id = id,
            title = title,
            pageUrl = link.absUrl("href"),
            apiRequest = video?.attr("data-apireq")?.takeIf(String::isNotBlank),
            videoId = video?.attr("data-vid")?.takeIf(String::isNotBlank),
            tServer = video?.attr("data-tserver")?.takeIf(String::isNotBlank),
            sourcePageUrl = pageUrl,
        )
    }.distinctBy { it.id }

    val nextPageUrl = document
        .selectFirst("nav.posts-navigation .nav-previous a[href]")
        ?.absUrl("href")
        ?.takeIf { it.isNotBlank() && it != pageUrl }

    return EpisodePage(episodes, nextPageUrl)
}

fun parsePlaybackResponse(json: String): PlayableSource? {
    val sources = JSONObject(json).optJSONArray("s") ?: return null
    for (index in 0 until sources.length()) {
        val source = sources.optJSONObject(index)?.optString("src")?.trim().orEmpty()
        if (source.isNotEmpty()) {
            return PlayableSource(if (source.startsWith("//")) "https:$source" else source)
        }
    }
    return null
}

private fun JSONArray.requiredString(index: Int): String =
    optString(index).trim().also {
        if (it.isEmpty() && isNull(index)) throw JSONException("Missing field $index")
    }
