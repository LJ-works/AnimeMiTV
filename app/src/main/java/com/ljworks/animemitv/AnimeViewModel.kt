package com.ljworks.animemitv

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

    private var animeJob: Job? = null
    private var episodeJob: Job? = null
    private var loadMoreJob: Job? = null
    private var playbackJob: Job? = null

    fun loadAnime() {
        animeJob?.cancel()
        animeJob = scope.launch {
            _uiState.update { it.copy(anime = LoadState.Loading) }
            try {
                val items = dataSource.fetchAnimeList()
                _uiState.update { it.copy(anime = LoadState.Content(items)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { it.copy(anime = LoadState.Error(error.message ?: "动画列表加载失败")) }
            }
        }
    }

    fun retryAnime() = loadAnime()

    fun rememberAnimeFocus(id: Int) {
        _uiState.update { if (it.screen == AppScreen.AnimeList) it.copy(focusedAnimeId = id) else it }
    }

    fun rememberEpisodeFocus(id: String) {
        _uiState.update { if (it.screen == AppScreen.EpisodeList) it.copy(focusedEpisodeId = id) else it }
    }

    fun openAnime(anime: Anime) {
        episodeJob?.cancel()
        loadMoreJob?.cancel()
        playbackJob?.cancel()
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
        episodeJob = scope.launch {
            try {
                val page = dataSource.fetchEpisodes(anime)
                if (!isCurrentAnime(anime.id)) return@launch
                _uiState.update {
                    it.copy(
                        episodes = LoadState.Content(page.episodes),
                        nextEpisodePageUrl = page.nextPageUrl,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isCurrentAnime(anime.id)) {
                    _uiState.update { it.copy(episodes = LoadState.Error(error.message ?: "剧集加载失败")) }
                }
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
        loadMoreJob?.cancel()
        _uiState.update { it.copy(loadingMoreEpisodes = true, episodeLoadError = null) }
        loadMoreJob = scope.launch {
            try {
                val page = dataSource.fetchEpisodes(anime, pageUrl)
                if (!isCurrentEpisodePage(anime.id, pageUrl)) return@launch
                _uiState.update {
                    it.copy(
                        episodes = LoadState.Content((existing + page.episodes).distinctBy(Episode::id)),
                        nextEpisodePageUrl = page.nextPageUrl,
                        loadingMoreEpisodes = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isCurrentEpisodePage(anime.id, pageUrl)) {
                    _uiState.update {
                        it.copy(
                            loadingMoreEpisodes = false,
                            episodeLoadError = error.message ?: "更多剧集加载失败",
                        )
                    }
                }
            }
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

    fun back() {
        when (_uiState.value.screen) {
            AppScreen.Player -> {
                playbackJob?.cancel()
                _uiState.update { it.copy(screen = AppScreen.EpisodeList, playback = LoadState.Idle) }
            }
            AppScreen.EpisodeList -> {
                episodeJob?.cancel()
                loadMoreJob?.cancel()
                playbackJob?.cancel()
                _uiState.update { it.copy(screen = AppScreen.AnimeList) }
            }
            AppScreen.AnimeList -> Unit
        }
    }

    private fun isCurrentAnime(animeId: Int): Boolean =
        _uiState.value.screen == AppScreen.EpisodeList && _uiState.value.selectedAnime?.id == animeId

    private fun isCurrentEpisodePage(animeId: Int, pageUrl: String): Boolean =
        isCurrentAnime(animeId) && _uiState.value.nextEpisodePageUrl == pageUrl

    private fun isCurrentEpisode(animeId: Int?, episodeId: String): Boolean =
        _uiState.value.screen == AppScreen.Player &&
            _uiState.value.selectedAnime?.id == animeId &&
            _uiState.value.selectedEpisode?.id == episodeId

    override fun onCleared() {
        animeJob?.cancel()
        episodeJob?.cancel()
        loadMoreJob?.cancel()
        playbackJob?.cancel()
        scope.cancel()
        super.onCleared()
    }
}
