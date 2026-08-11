package com.ljworks.animemitv

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
    fun playbackRetryRefreshesEpisodeSignatureBeforeResolving() {
        val dataSource = FakeDataSource()
        val viewModel = AnimeViewModel(dataSource, CoroutineScope(Dispatchers.Unconfined))

        viewModel.openAnime(anime)
        viewModel.playEpisode(episode)
        viewModel.retryPlayback()

        assertEquals(2, dataSource.fetchEpisodeCalls)
        assertTrue(viewModel.uiState.value.playback is LoadState.Content)
    }

    private class FakeDataSource(
        private val animeList: List<Anime> = emptyList(),
        private val episode: Episode = Episode(
            "1",
            "相反的你和我 第二季 [01]",
            "https://anime1.me/1",
            "request",
            "v1",
            "pt2",
        ),
    ) : Anime1DataSource {
        var fetchEpisodeCalls = 0

        override suspend fun fetchAnimeList() = animeList

        override suspend fun fetchEpisodes(anime: Anime, pageUrl: String): EpisodePage {
            fetchEpisodeCalls++
            return EpisodePage(listOf(episode), null)
        }

        override suspend fun resolvePlayback(anime: Anime, episode: Episode) =
            PlayableSource("https://video.example/episode.mp4")
    }
}
