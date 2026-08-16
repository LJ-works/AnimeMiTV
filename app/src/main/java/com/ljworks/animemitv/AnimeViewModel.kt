package com.ljworks.animemitv

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Content<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

sealed interface AppScreen {
    data object AnimeList : AppScreen
    data object SeasonalList : AppScreen
    data object FollowedAnimeList : AppScreen
    data object EpisodeList : AppScreen
    data object Player : AppScreen
}

enum class EpisodeSource {
    ANIME_LIST,
    SEASONAL_LIST,
    FOLLOWED_ANIME_LIST,
}

enum class EpisodeSort {
    NEWEST,
    OLDEST,
}

data class AppUiState(
    val screen: AppScreen = AppScreen.AnimeList,
    val anime: LoadState<List<Anime>> = LoadState.Loading,
    val followedAnimeIds: Set<Int> = emptySet(),
    val followedAnime: List<Anime> = emptyList(),
    val focusedAnimeId: Int? = null,
    val focusedFollowedAnimeId: Int? = null,
    val isAnimeSearchActive: Boolean = false,
    val animeSearchQuery: String = "",
    val restoreAnimeSearchFocus: Boolean = false,
    val seasonalDiscovery: LoadState<List<AnimeSeason>> = LoadState.Idle,
    val currentSeason: AnimeSeason? = null,
    val selectedSeason: AnimeSeason? = null,
    val seasonalSchedule: LoadState<AnimeSchedule> = LoadState.Idle,
    val focusedSeasonalAnimeId: String? = null,
    val seasonalScrollIndex: Int = 0,
    val unavailableMessage: String? = null,
    val selectedAnime: Anime? = null,
    val episodeSource: EpisodeSource = EpisodeSource.ANIME_LIST,
    val episodes: LoadState<List<Episode>> = LoadState.Idle,
    val episodeSort: EpisodeSort = EpisodeSort.NEWEST,
    val focusedEpisodeId: String? = null,
    val selectedEpisode: Episode? = null,
    val playback: LoadState<PlayableSource> = LoadState.Idle,
    val episodeProgress: Map<String, EpisodeProgress> = emptyMap(),
    val followedAnimeSaveError: String? = null,
    val isExitConfirmVisible: Boolean = false,
)

class AnimeViewModel(
    private val dataSource: Anime1DataSource,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val followedAnimeStore: FollowedAnimeStore = EmptyFollowedAnimeStore,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
    private val episodeProgressStore: EpisodeProgressStore = EmptyEpisodeProgressStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AppUiState(
            followedAnimeIds = followedAnimeStore.load(),
            episodeProgress = episodeProgressStore.load(),
        ),
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var animeJob: Job? = null
    private var seasonDiscoveryJob: Job? = null
    private var seasonScheduleJob: Job? = null
    private var episodeJob: Job? = null
    private var playbackJob: Job? = null
    private var unavailableJob: Job? = null
    private val seasonalCache = mutableMapOf<String, AnimeSchedule>()
    private val episodeCache = mutableMapOf<String, CachedEpisodes>()

    fun requestExit() {
        _uiState.update {
            if (it.screen == AppScreen.AnimeList ||
                it.screen == AppScreen.SeasonalList ||
                it.screen == AppScreen.FollowedAnimeList
            ) {
                it.copy(isExitConfirmVisible = true)
            } else {
                it
            }
        }
    }

    fun dismissExit() {
        _uiState.update { it.copy(isExitConfirmVisible = false) }
    }

    fun loadAnime() {
        animeJob?.cancel()
        animeJob = scope.launch {
            _uiState.update { it.copy(anime = LoadState.Loading) }
            try {
                val items = dataSource.fetchAnimeList()
                _uiState.update {
                    it.copy(
                        anime = LoadState.Content(items),
                        followedAnime = items.filter { anime -> anime.id in it.followedAnimeIds },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { it.copy(anime = LoadState.Error(error.message ?: "动画列表加载失败")) }
            }
        }
    }

    fun retryAnime() = loadAnime()

    fun openAnimeSearch() {
        if (_uiState.value.screen == AppScreen.AnimeList) {
            _uiState.update { it.copy(isAnimeSearchActive = true, restoreAnimeSearchFocus = false) }
        }
    }

    fun updateAnimeSearchQuery(query: String) {
        if (_uiState.value.screen == AppScreen.AnimeList && _uiState.value.isAnimeSearchActive) {
            _uiState.update { it.copy(animeSearchQuery = query) }
        }
    }

    fun clearAnimeSearch() {
        if (_uiState.value.screen == AppScreen.AnimeList && _uiState.value.isAnimeSearchActive) {
            _uiState.update { it.copy(animeSearchQuery = "") }
        }
    }

    fun closeAnimeSearch() {
        if (!_uiState.value.isAnimeSearchActive) return
        _uiState.update {
            it.copy(isAnimeSearchActive = false, animeSearchQuery = "", restoreAnimeSearchFocus = false)
        }
    }

    fun openFollowedAnime() {
        if (_uiState.value.screen == AppScreen.FollowedAnimeList) return
        seasonDiscoveryJob?.cancel()
        seasonScheduleJob?.cancel()
        episodeJob?.cancel()
        playbackJob?.cancel()
        _uiState.update {
            it.copy(
                screen = AppScreen.FollowedAnimeList,
                isExitConfirmVisible = false,
                unavailableMessage = null,
                isAnimeSearchActive = false,
                animeSearchQuery = "",
                restoreAnimeSearchFocus = false,
            )
        }
        if (_uiState.value.anime is LoadState.Idle) loadAnime()
    }

    fun toggleFollowedAnime() {
        val anime = _uiState.value.selectedAnime ?: return
        val currentIds = _uiState.value.followedAnimeIds
        val updatedIds = if (anime.id in currentIds) currentIds - anime.id else currentIds + anime.id
        val saved = try {
            followedAnimeStore.save(updatedIds)
        } catch (_: Throwable) {
            false
        }
        if (!saved) {
            _uiState.update { it.copy(followedAnimeSaveError = "关注保存失败") }
            return
        }
        _uiState.update {
            it.copy(
                followedAnimeIds = updatedIds,
                followedAnime = when (val animeState = it.anime) {
                    is LoadState.Content -> animeState.value.filter { item -> item.id in updatedIds }
                    else -> it.followedAnime
                },
                followedAnimeSaveError = null,
            )
        }
    }

    fun clearFollowedAnimeSaveError() {
        _uiState.update { it.copy(followedAnimeSaveError = null) }
    }

    fun openSeasonal() {
        episodeJob?.cancel()
        playbackJob?.cancel()
        _uiState.update {
            it.copy(
                screen = AppScreen.SeasonalList,
                isExitConfirmVisible = false,
                unavailableMessage = null,
                isAnimeSearchActive = false,
                animeSearchQuery = "",
                restoreAnimeSearchFocus = false,
            )
        }
        if (_uiState.value.seasonalDiscovery is LoadState.Content) {
            _uiState.value.selectedSeason?.let(::loadSeason)
            return
        }
        seasonDiscoveryJob?.cancel()
        seasonDiscoveryJob = scope.launch {
            _uiState.update { it.copy(seasonalDiscovery = LoadState.Loading, seasonalSchedule = LoadState.Loading) }
            try {
                val current = dataSource.fetchCurrentSeason()
                val seasons = precedingSeasons(current)
                if (_uiState.value.screen != AppScreen.SeasonalList) return@launch
                _uiState.update {
                    it.copy(
                        seasonalDiscovery = LoadState.Content(seasons),
                        currentSeason = current,
                        selectedSeason = seasons.first(),
                    )
                }
                loadSeason(seasons.first())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_uiState.value.screen == AppScreen.SeasonalList) {
                    _uiState.update {
                        it.copy(
                            seasonalDiscovery = LoadState.Error(error.message ?: "当前季度发现失败"),
                            seasonalSchedule = LoadState.Idle,
                        )
                    }
                }
            }
        }
    }

    fun retrySeasonal() = openSeasonal()

    fun selectSeason(season: AnimeSeason) {
        if (_uiState.value.seasonalDiscovery !is LoadState.Content) return
        _uiState.update { it.copy(selectedSeason = season, focusedSeasonalAnimeId = null, seasonalScrollIndex = 0) }
        loadSeason(season)
    }

    private fun loadSeason(season: AnimeSeason) {
        seasonScheduleJob?.cancel()
        seasonalCache[season.label]?.let { schedule ->
            _uiState.update { it.copy(seasonalSchedule = LoadState.Content(schedule)) }
            return
        }
        seasonScheduleJob = scope.launch {
            _uiState.update { it.copy(seasonalSchedule = LoadState.Loading) }
            try {
                val schedule = dataSource.fetchSeasonSchedule(season)
                if (_uiState.value.screen != AppScreen.SeasonalList || _uiState.value.selectedSeason != season) return@launch
                seasonalCache[season.label] = schedule
                _uiState.update { it.copy(seasonalSchedule = LoadState.Content(schedule)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_uiState.value.screen == AppScreen.SeasonalList && _uiState.value.selectedSeason == season) {
                    _uiState.update { it.copy(seasonalSchedule = LoadState.Error(error.message ?: "季度排期加载失败")) }
                }
            }
        }
    }

    fun rememberSeasonalFocus(id: String, rowIndex: Int) {
        _uiState.update {
            if (it.screen == AppScreen.SeasonalList) it.copy(focusedSeasonalAnimeId = id, seasonalScrollIndex = rowIndex) else it
        }
    }

    fun confirmSeasonalAnime(anime: SeasonalAnime) {
        if (anime.categoryUrl == null) {
            unavailableJob?.cancel()
            unavailableJob = scope.launch {
                _uiState.update { it.copy(unavailableMessage = "暂无资源") }
                kotlinx.coroutines.delay(2_000)
                _uiState.update { it.copy(unavailableMessage = null) }
            }
            return
        }
        val id = Regex("[?&]cat=(\\d+)").find(anime.categoryUrl)?.groupValues?.get(1)?.toIntOrNull() ?: return
        openAnime(
            Anime(id, anime.title, "", "", "", "", anime.categoryUrl),
            EpisodeSource.SEASONAL_LIST,
        )
    }

    fun navigateToAnime() {
        seasonDiscoveryJob?.cancel()
        seasonScheduleJob?.cancel()
        episodeJob?.cancel()
        playbackJob?.cancel()
        _uiState.update {
            it.copy(
                screen = AppScreen.AnimeList,
                isExitConfirmVisible = false,
                unavailableMessage = null,
                isAnimeSearchActive = false,
                animeSearchQuery = "",
                restoreAnimeSearchFocus = false,
            )
        }
    }

    fun rememberAnimeFocus(id: Int) {
        _uiState.update {
            when (it.screen) {
                AppScreen.AnimeList -> it.copy(focusedAnimeId = id, restoreAnimeSearchFocus = false)
                AppScreen.FollowedAnimeList -> it.copy(focusedFollowedAnimeId = id)
                else -> it
            }
        }
    }

    fun rememberEpisodeFocus(id: String) {
        _uiState.update { if (it.screen == AppScreen.EpisodeList) it.copy(focusedEpisodeId = id) else it }
    }

    fun openAnime(anime: Anime, source: EpisodeSource = EpisodeSource.ANIME_LIST) {
        episodeJob?.cancel()
        playbackJob?.cancel()
        val cached = freshCachedEpisodes(anime)
        _uiState.update {
            it.copy(
                screen = AppScreen.EpisodeList,
                selectedAnime = anime,
                episodeSource = source,
                episodes = if (cached == null) LoadState.Loading else LoadState.Content(cached),
                episodeSort = EpisodeSort.NEWEST,
                focusedEpisodeId = null,
                selectedEpisode = null,
                playback = LoadState.Idle,
            )
        }
        if (cached != null) return
        episodeJob = scope.launch {
            try {
                val episodes = loadAllEpisodes(anime)
                if (!isCurrentAnime(anime)) return@launch
                episodeCache[episodeCacheKey(anime)] = CachedEpisodes(episodes, elapsedRealtimeMillis())
                _uiState.update { it.copy(episodes = LoadState.Content(episodes)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isCurrentAnime(anime)) {
                    _uiState.update { it.copy(episodes = LoadState.Error(error.message ?: "剧集加载失败")) }
                }
            }
        }
    }

    fun retryEpisodes() {
        val state = _uiState.value
        state.selectedAnime?.let { openAnime(it, state.episodeSource) }
    }

    fun toggleEpisodeSort() {
        _uiState.update {
            it.copy(
                episodeSort = if (it.episodeSort == EpisodeSort.NEWEST) EpisodeSort.OLDEST else EpisodeSort.NEWEST,
                focusedEpisodeId = null,
            )
        }
    }

    fun playEpisode(episode: Episode) {
        val anime = _uiState.value.selectedAnime ?: return
        playbackJob?.cancel()
        _uiState.update {
            it.copy(
                screen = AppScreen.Player,
                focusedEpisodeId = episode.id,
                selectedEpisode = episode,
                playback = LoadState.Loading,
            )
        }
        playbackJob = scope.launch {
            try {
                val source = dataSource.resolvePlayback(anime, episode)
                if (!isCurrentEpisode(anime.id, episode.id)) return@launch
                _uiState.update { it.copy(playback = LoadState.Content(source)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isCurrentEpisode(anime.id, episode.id)) {
                    _uiState.update { it.copy(playback = LoadState.Error(error.message ?: "播放地址加载失败")) }
                }
            }
        }
    }

    fun retryPlayback() {
        val state = _uiState.value
        val anime = state.selectedAnime ?: return
        val episode = state.selectedEpisode ?: return
        playbackJob?.cancel()
        _uiState.update { it.copy(playback = LoadState.Loading) }
        playbackJob = scope.launch {
            try {
                val refreshed = dataSource.fetchEpisodes(anime, episode.sourcePageUrl)
                    .episodes
                    .firstOrNull { it.id == episode.id }
                    ?: error("剧集已更新，请返回后重新选择")
                val source = dataSource.resolvePlayback(anime, refreshed)
                if (!isCurrentEpisode(anime.id, episode.id)) return@launch
                _uiState.update {
                    it.copy(
                        selectedEpisode = refreshed,
                        playback = LoadState.Content(source),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isCurrentEpisode(anime.id, episode.id)) {
                    _uiState.update { it.copy(playback = LoadState.Error(error.message ?: "播放地址加载失败")) }
                }
            }
        }
    }

    fun playbackError(episodeId: String, message: String) {
        if (isCurrentEpisode(_uiState.value.selectedAnime?.id, episodeId)) {
            playbackJob?.cancel()
            _uiState.update { it.copy(playback = LoadState.Error(message)) }
        }
    }

    fun saveEpisodeProgress(episodeId: String, positionMillis: Long, durationMillis: Long) {
        val progress = EpisodeProgress(positionMillis.coerceAtLeast(0), durationMillis.coerceAtLeast(0))
        _uiState.update { it.copy(episodeProgress = it.episodeProgress + (episodeId to progress)) }
        try {
            episodeProgressStore.save(episodeId, progress)
        } catch (_: Exception) {
            // Saving progress is best-effort and must never interrupt playback.
        }
    }

    fun back() {
        when (_uiState.value.screen) {
            AppScreen.Player -> {
                playbackJob?.cancel()
                _uiState.update { it.copy(screen = AppScreen.EpisodeList, playback = LoadState.Idle) }
            }
            AppScreen.EpisodeList -> {
                episodeJob?.cancel()
                playbackJob?.cancel()
                _uiState.update {
                    val destination = when (it.episodeSource) {
                        EpisodeSource.ANIME_LIST -> AppScreen.AnimeList
                        EpisodeSource.SEASONAL_LIST -> AppScreen.SeasonalList
                        EpisodeSource.FOLLOWED_ANIME_LIST -> AppScreen.FollowedAnimeList
                    }
                    it.copy(
                        screen = destination,
                        restoreAnimeSearchFocus = destination == AppScreen.AnimeList && it.isAnimeSearchActive,
                    )
                }
            }
            AppScreen.AnimeList, AppScreen.SeasonalList, AppScreen.FollowedAnimeList -> Unit
        }
    }

    private suspend fun loadAllEpisodes(anime: Anime): List<Episode> {
        val visitedPages = mutableSetOf<String>()
        val episodes = buildList {
            var pageUrl: String? = anime.categoryUrl
            while (pageUrl != null && visitedPages.add(pageUrl)) {
                val page = dataSource.fetchEpisodes(anime, pageUrl)
                addAll(page.episodes)
                pageUrl = page.nextPageUrl
            }
        }
        return episodes.distinctBy(Episode::id)
    }

    private fun episodeCacheKey(anime: Anime): String = "${anime.id}|${anime.categoryUrl}"

    private fun freshCachedEpisodes(anime: Anime): List<Episode>? {
        val cached = episodeCache[episodeCacheKey(anime)] ?: return null
        if (elapsedRealtimeMillis() - cached.loadedAtMillis >= EPISODE_CACHE_TTL_MILLIS) {
            episodeCache.remove(episodeCacheKey(anime))
            return null
        }
        return cached.episodes
    }

    private fun isCurrentAnime(anime: Anime): Boolean =
        _uiState.value.screen == AppScreen.EpisodeList &&
            _uiState.value.selectedAnime?.let(::episodeCacheKey) == episodeCacheKey(anime)

    private fun isCurrentEpisode(animeId: Int?, episodeId: String): Boolean =
        _uiState.value.screen == AppScreen.Player &&
            _uiState.value.selectedAnime?.id == animeId &&
            _uiState.value.selectedEpisode?.id == episodeId

    override fun onCleared() {
        animeJob?.cancel()
        episodeJob?.cancel()
        seasonDiscoveryJob?.cancel()
        seasonScheduleJob?.cancel()
        unavailableJob?.cancel()
        playbackJob?.cancel()
        scope.cancel()
        super.onCleared()
    }

    private data class CachedEpisodes(
        val episodes: List<Episode>,
        val loadedAtMillis: Long,
    )

    private companion object {
        /** 剧集列表进程内缓存的存活时间；短 TTL 用于降低播放签名陈旧的风险。 */
        const val EPISODE_CACHE_TTL_MILLIS = 10 * 60 * 1000L
    }
}
