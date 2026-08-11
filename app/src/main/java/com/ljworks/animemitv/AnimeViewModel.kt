package com.ljworks.animemitv

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    data object EpisodeList : AppScreen
    data object Player : AppScreen
}

enum class EpisodeSort {
    NEWEST,
    OLDEST,
}

data class AppUiState(
    val screen: AppScreen = AppScreen.AnimeList,
    val anime: LoadState<List<Anime>> = LoadState.Loading,
    val animePageIndex: Int = 0,
    val focusedAnimeId: Int? = null,
    val selectedAnime: Anime? = null,
    val episodes: LoadState<List<Episode>> = LoadState.Idle,
    val nextEpisodePageUrl: String? = null,
    val loadingMoreEpisodes: Boolean = false,
    val episodeLoadError: String? = null,
    val episodeSort: EpisodeSort = EpisodeSort.NEWEST,
    val focusedEpisodeId: String? = null,
    val selectedEpisode: Episode? = null,
    val playback: LoadState<PlayableSource> = LoadState.Idle,
)

class AnimeViewModel(
    private val dataSource: Anime1DataSource,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun loadAnime() = scope.launch {
        _uiState.update { it.copy(anime = LoadState.Loading) }
        runCatching { dataSource.fetchAnimeList() }
            .onSuccess { items -> _uiState.update { it.copy(anime = LoadState.Content(items), animePageIndex = 0) } }
            .onFailure { error -> _uiState.update { it.copy(anime = LoadState.Error(error.message ?: "动画列表加载失败")) } }
    }

    fun retryAnime() = loadAnime()

    fun nextAnimePage() {
        val state = _uiState.value
        val items = (state.anime as? LoadState.Content)?.value ?: return
        val page = items.page(state.animePageIndex)
        if (page.hasNext) {
            val nextPage = items.page(page.pageIndex + 1)
            _uiState.update {
                it.copy(
                    animePageIndex = nextPage.pageIndex,
                    focusedAnimeId = nextPage.items.firstOrNull()?.id,
                )
            }
        }
    }

    fun previousAnimePage() {
        val state = _uiState.value
        val items = (state.anime as? LoadState.Content)?.value ?: return
        if (state.animePageIndex > 0) {
            val previousPage = items.page(state.animePageIndex - 1)
            _uiState.update {
                it.copy(
                    animePageIndex = previousPage.pageIndex,
                    focusedAnimeId = previousPage.items.lastOrNull()?.id,
                )
            }
        }
    }

    fun rememberAnimeFocus(id: Int) {
        _uiState.update { if (it.screen == AppScreen.AnimeList) it.copy(focusedAnimeId = id) else it }
    }

    fun rememberEpisodeFocus(id: String) {
        _uiState.update { if (it.screen == AppScreen.EpisodeList) it.copy(focusedEpisodeId = id) else it }
    }

    fun openAnime(anime: Anime) {
        _uiState.update {
            it.copy(
                screen = AppScreen.EpisodeList,
                selectedAnime = anime,
                episodes = LoadState.Loading,
                nextEpisodePageUrl = null,
                loadingMoreEpisodes = false,
                episodeLoadError = null,
                episodeSort = EpisodeSort.NEWEST,
                focusedEpisodeId = null,
                selectedEpisode = null,
                playback = LoadState.Idle,
            )
        }
        scope.launch {
            runCatching { dataSource.fetchEpisodes(anime) }
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            episodes = LoadState.Content(page.episodes),
                            nextEpisodePageUrl = page.nextPageUrl,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(episodes = LoadState.Error(error.message ?: "剧集加载失败")) }
                }
        }
    }

    fun retryEpisodes() {
        _uiState.value.selectedAnime?.let(::openAnime)
    }

    fun toggleEpisodeSort() {
        _uiState.update {
            it.copy(
                episodeSort = if (it.episodeSort == EpisodeSort.NEWEST) EpisodeSort.OLDEST else EpisodeSort.NEWEST
            )
        }
    }

    fun loadMoreEpisodes() {
        val state = _uiState.value
        val anime = state.selectedAnime ?: return
        val pageUrl = state.nextEpisodePageUrl ?: return
        val existing = (state.episodes as? LoadState.Content)?.value ?: return
        if (state.loadingMoreEpisodes) return
        _uiState.update { it.copy(loadingMoreEpisodes = true, episodeLoadError = null) }
        scope.launch {
            runCatching { dataSource.fetchEpisodes(anime, pageUrl) }
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            episodes = LoadState.Content((existing + page.episodes).distinctBy(Episode::id)),
                            nextEpisodePageUrl = page.nextPageUrl,
                            loadingMoreEpisodes = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loadingMoreEpisodes = false,
                            episodeLoadError = error.message ?: "更多剧集加载失败",
                        )
                    }
                }
        }
    }

    fun playEpisode(episode: Episode) {
        val anime = _uiState.value.selectedAnime ?: return
        _uiState.update {
            it.copy(
                screen = AppScreen.Player,
                focusedEpisodeId = episode.id,
                selectedEpisode = episode,
                playback = LoadState.Loading,
            )
        }
        scope.launch {
            runCatching { dataSource.resolvePlayback(anime, episode) }
                .onSuccess { source -> _uiState.update { it.copy(playback = LoadState.Content(source)) } }
                .onFailure { error -> _uiState.update { it.copy(playback = LoadState.Error(error.message ?: "播放地址加载失败")) } }
        }
    }

    fun retryPlayback() {
        val state = _uiState.value
        val anime = state.selectedAnime ?: return
        val episode = state.selectedEpisode ?: return
        _uiState.update { it.copy(playback = LoadState.Loading) }
        scope.launch {
            runCatching {
                val refreshed = dataSource.fetchEpisodes(anime, episode.sourcePageUrl).episodes.firstOrNull { it.id == episode.id } ?: episode
                dataSource.resolvePlayback(anime, refreshed)
            }.onSuccess { source ->
                _uiState.update { it.copy(playback = LoadState.Content(source)) }
            }.onFailure { error ->
                _uiState.update { it.copy(playback = LoadState.Error(error.message ?: "播放地址加载失败")) }
            }
        }
    }

    fun back() {
        _uiState.update {
            when (it.screen) {
                AppScreen.Player -> it.copy(screen = AppScreen.EpisodeList, playback = LoadState.Idle)
                AppScreen.EpisodeList -> it.copy(screen = AppScreen.AnimeList)
                AppScreen.AnimeList -> it
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
