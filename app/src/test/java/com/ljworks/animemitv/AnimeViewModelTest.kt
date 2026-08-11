package com.ljworks.animemitv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeViewModelTest {
    private val anime = Anime(1933, "相反的你和我 第二季", "連載中(18)", "2026", "夏", "")
    private val episode = Episode("1", "相反的你和我 第二季 [01]", "https://anime1.me/1", "request", "v1", "pt2")

    @Test
    fun loadingAnimeExposesContentAndSupportsLocalPageChanges() {
        val dataSource = FakeDataSource(animeList = (1..21).map { anime.copy(id = it) })
        val viewModel = AnimeViewModel(dataSource, CoroutineScope(Dispatchers.Unconfined))

        viewModel.loadAnime()
        viewModel.nextAnimePage()

        val state = viewModel.uiState.value
        assertTrue(state.anime is LoadState.Content)
        assertEquals(1, state.animePageIndex)
        assertEquals(1, (state.anime as LoadState.Content).value.page(1).items.size)
    }

    @Test
    fun openingAnimeLoadsEpisodesAndSortIsOnlyCurrentPageState() {
        val viewModel = AnimeViewModel(FakeDataSource(), CoroutineScope(Dispatchers.Unconfined))

        viewModel.openAnime(anime)
        viewModel.toggleEpisodeSort()

        val state = viewModel.uiState.value
        assertEquals(AppScreen.EpisodeList, state.screen)
        assertEquals(EpisodeSort.OLDEST, state.episodeSort)
        assertEquals(LoadState.Content(listOf(episode)), state.episodes)
    }

    @Test
    fun loadingMoreFailureKeepsExistingEpisodes() {
        val dataSource = FakeDataSource(nextPageUrl = "page-2", failMorePage = true)
        val viewModel = AnimeViewModel(dataSource, CoroutineScope(Dispatchers.Unconfined))

        viewModel.openAnime(anime)
        viewModel.loadMoreEpisodes()

        val state = viewModel.uiState.value
        assertEquals(LoadState.Content(listOf(episode)), state.episodes)
        assertEquals("更多剧集加载失败", state.episodeLoadError)
        assertTrue(!state.loadingMoreEpisodes)
    }

    @Test
    fun playbackRetryRefreshesEpisodeSignatureBeforeResolving() {
        val refreshed = episode.copy(apiRequest = "fresh-request")
        val dataSource = FakeDataSource(episode = refreshed)
        val viewModel = AnimeViewModel(dataSource, CoroutineScope(Dispatchers.Unconfined))

        viewModel.openAnime(anime)
        viewModel.playEpisode(episode)
        viewModel.retryPlayback()

        assertEquals(2, dataSource.fetchEpisodeCalls)
        assertEquals("fresh-request", dataSource.resolvedEpisodes.last().apiRequest)
        assertTrue(viewModel.uiState.value.playback is LoadState.Content)
    }

    @Test
    fun playbackRetryDoesNotReuseOldSignatureWhenEpisodeDisappears() {
        val dataSource = FakeDataSource(episode = null)
        val viewModel = AnimeViewModel(dataSource, CoroutineScope(Dispatchers.Unconfined))

        viewModel.openAnime(anime)
        viewModel.playEpisode(episode)
        val resolvesBeforeRetry = dataSource.resolvedEpisodes.size
        viewModel.retryPlayback()

        assertEquals(resolvesBeforeRetry, dataSource.resolvedEpisodes.size)
        assertTrue(viewModel.uiState.value.playback is LoadState.Error)
    }

    @Test
    fun playbackErrorShowsRecoveryStateAndBackReturnsToEpisodes() {
        val viewModel = AnimeViewModel(FakeDataSource(), CoroutineScope(Dispatchers.Unconfined))

        viewModel.openAnime(anime)
        viewModel.playEpisode(episode)
        viewModel.playbackError(episode.id, "视频播放失败")

        assertEquals(LoadState.Error("视频播放失败"), viewModel.uiState.value.playback)
        viewModel.back()
        assertEquals(AppScreen.EpisodeList, viewModel.uiState.value.screen)
    }

    @Test
    fun oldAnimeRequestCannotOverwriteNewAnime() {
        val animeB = anime.copy(id = 2000, title = "动画 B")
        val source = DeferredDataSource()
        val viewModel = AnimeViewModel(source, CoroutineScope(Dispatchers.Unconfined))

        viewModel.openAnime(anime)
        viewModel.openAnime(animeB)
        source.requests.getValue(animeB.id).complete(EpisodePage(listOf(episode.copy(id = "B")), null))
        source.requests.getValue(anime.id).complete(EpisodePage(listOf(episode.copy(id = "A")), null))

        val state = viewModel.uiState.value
        assertEquals(animeB, state.selectedAnime)
        assertEquals("B", (state.episodes as LoadState.Content).value.single().id)
    }

    private class FakeDataSource(
        private val animeList: List<Anime> = emptyList(),
        private val episode: Episode? = Episode(
            "1",
            "相反的你和我 第二季 [01]",
            "https://anime1.me/1",
            "request",
            "v1",
            "pt2",
        ),
        private val nextPageUrl: String? = null,
        private val failMorePage: Boolean = false,
    ) : Anime1DataSource {
        var fetchEpisodeCalls = 0
        val resolvedEpisodes = mutableListOf<Episode>()

        override suspend fun fetchAnimeList() = animeList

        override suspend fun fetchEpisodes(anime: Anime, pageUrl: String): EpisodePage {
            fetchEpisodeCalls++
            if (failMorePage && pageUrl == nextPageUrl) error("更多剧集加载失败")
            return EpisodePage(listOfNotNull(episode), nextPageUrl)
        }

        override suspend fun resolvePlayback(anime: Anime, episode: Episode): PlayableSource {
            resolvedEpisodes += episode
            return PlayableSource("https://video.example/episode.mp4")
        }
    }

    private class DeferredDataSource : Anime1DataSource {
        val requests = mutableMapOf<Int, CompletableDeferred<EpisodePage>>()

        override suspend fun fetchAnimeList() = emptyList<Anime>()

        override suspend fun fetchEpisodes(anime: Anime, pageUrl: String): EpisodePage =
            requests.getOrPut(anime.id) { CompletableDeferred() }.await()

        override suspend fun resolvePlayback(anime: Anime, episode: Episode) =
            PlayableSource("https://video.example/episode.mp4")
    }
}
