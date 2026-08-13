package com.ljworks.animemitv

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern

data class Anime(
    val id: Int,
    val title: String,
    val episodeStatus: String,
    val year: String,
    val season: String,
    val fansub: String,
    private val categoryUrlOverride: String? = null,
) {
    val categoryUrl: String get() = categoryUrlOverride ?: "https://anime1.me/?cat=$id"
}

data class AnimeSeason(
    val label: String,
    val url: String,
)

data class SeasonalAnime(
    val id: String,
    val title: String,
    val categoryUrl: String?,
) {
    val hasResource: Boolean get() = categoryUrl != null
}

data class AnimeSchedule(
    val days: List<List<SeasonalAnime>>,
) {
    init {
        require(days.size == 7) { "季度排期必须包含七天" }
    }
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

private val seasonPattern = Pattern.compile("(\\d{4})年([冬春夏秋])季新番")
private val categoryPattern = Pattern.compile("(?:[?&]cat=)(\\d+)")

fun parseCurrentSeason(html: String, pageUrl: String = "https://anime1.me/"): AnimeSeason {
    val document = Jsoup.parse(html, pageUrl)
    val link = document.select("#masthead a[href]").firstOrNull { seasonPattern.matcher(it.text().trim()).find() }
        ?: error("Anime1 首页未找到当前季")
    val label = seasonPattern.matcher(link.text().trim()).run {
        if (!find()) error("Anime1 当前季格式无效") else group(0).orEmpty()
    }
    val url = link.absUrl("href").takeIf(String::isNotBlank)
        ?: error("Anime1 当前季链接无效")
    return AnimeSeason(label, url)
}

fun precedingSeasons(current: AnimeSeason): List<AnimeSeason> {
    val match = seasonPattern.matcher(current.label)
    require(match.matches()) { "当前季格式无效" }
    var year = match.group(1).orEmpty().toInt()
    var seasonIndex = listOf("冬", "春", "夏", "秋").indexOf(match.group(2).orEmpty())
    return buildList {
        repeat(21) { index ->
            val season = listOf("冬", "春", "夏", "秋")[seasonIndex]
            val label = "${year}年${season}季新番"
            add(
                if (index == 0) current
                else AnimeSeason(
                    label,
                    "https://anime1.me/${URLEncoder.encode(label, StandardCharsets.UTF_8.name()).replace("+", "%20")}",
                )
            )
            if (--seasonIndex < 0) {
                seasonIndex = 3
                year--
            }
        }
    }
}

fun parseSeasonSchedule(html: String, pageUrl: String): AnimeSchedule {
    val document = Jsoup.parse(html, pageUrl)
    val table = document.select("table").firstOrNull { table ->
        table.select("thead tr").any { row ->
            row.select("th").map { it.text().trim() } == listOf("日", "一", "二", "三", "四", "五", "六")
        }
    } ?: error("Anime1 当前季排期格式无效")

    val days = Array(7) { mutableListOf<SeasonalAnime>() }
    table.select("tbody tr").forEachIndexed { rowIndex, row ->
        row.select("td").take(7).forEachIndexed { dayIndex, cell ->
            val title = cell.text().trim().takeIf(String::isNotEmpty) ?: return@forEachIndexed
            val link = cell.selectFirst("a[href]")
            val href = link?.absUrl("href")?.takeIf(String::isNotBlank)
            val categoryUrl = href?.takeIf { isAnime1Category(it) }
            val id = categoryUrl?.let { categoryPattern.matcher(it).run { if (find()) group(1) else null } }
                ?.let { "anime-$it" }
                ?: "unavailable-$dayIndex-$rowIndex"
            days[dayIndex] += SeasonalAnime(id, title, categoryUrl)
        }
    }
    return AnimeSchedule(days.map { it.toList() })
}

private fun isAnime1Category(url: String): Boolean =
    url.lowercase(Locale.ROOT).startsWith("https://anime1.me/") && categoryPattern.matcher(url).find()

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
