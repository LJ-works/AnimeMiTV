package com.ljworks.animemitv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeViewModelTest {
    private val anime = Anime(1933, "相反的你和我 第二季", "連載中(18)", "2026", "夏", "")
    private val episode = Episode("1", "相反的你和我 第二季 [01]", "https://anime1.me/1", "request", "v1", "pt2")

    @Test
    fun openingSeasonalDiscoversTwentyOneSeasonsAndLoadsOnlyTheSelectedSchedule() {
        val source = SeasonalFake()
        val viewModel = AnimeViewModel(source, CoroutineScope(Dispatchers.Unconfined))

        viewModel.openSeasonal()

        assertEquals(AppScreen.SeasonalList, viewModel.uiState.value.screen)
        assertEquals(21, (viewModel.uiState.value.seasonalDiscovery as LoadState.Content).value.size)
        assertEquals(1, source.scheduleCalls)
        assertEquals("2026年夏季新番", source.requestedSeasons.single().label)
    }

    @Test
    fun successfulSeasonalScheduleIsCachedButFailureCanBeRetried() {
        val source = SeasonalFake(failFirstSchedule = true)
        val viewModel = AnimeViewModel(source, CoroutineScope(Dispatchers.Unconfined))

        viewModel.openSeasonal()
        assertTrue(viewModel.uiState.value.seasonalSchedule is LoadState.Error)

        viewModel.retrySeasonal()
        assertTrue(viewModel.uiState.value.seasonalSchedule is LoadState.Content)
        viewModel.openSeasonal()
        assertEquals(2, source.scheduleCalls)
    }

    @Test
    fun seasonalAvailableAnimeUsesCategoryLinkAndBackReturnsToSchedule() {
        val source = SeasonalFake()
        val viewModel = AnimeViewModel(source, CoroutineScope(Dispatchers.Unconfined))
        viewModel.openSeasonal()
        val seasonalAnime = (viewModel.uiState.value.seasonalSchedule as LoadState.Content).value.days[0].single()

        viewModel.confirmSeasonalAnime(seasonalAnime)

        assertEquals(AppScreen.EpisodeList, viewModel.uiState.value.screen)
        assertEquals(1940, viewModel.uiState.value.selectedAnime?.id)
        assertEquals(EpisodeSource.SEASONAL_LIST, viewModel.uiState.value.episodeSource)
        viewModel.back()
        assertEquals(AppScreen.SeasonalList, viewModel.uiState.value.screen)
    }

    @Test
    fun seasonalUnavailableAnimeDoesNotNavigate() {
        val source = SeasonalFake()
        val viewModel = AnimeViewModel(source, CoroutineScope(Dispatchers.Unconfined))
        viewModel.openSeasonal()
        val unavailable = (viewModel.uiState.value.seasonalSchedule as LoadState.Content).value.days[1].single()

        viewModel.confirmSeasonalAnime(unavailable)

        assertEquals(AppScreen.SeasonalList, viewModel.uiState.value.screen)
        assertEquals("暂无资源", viewModel.uiState.value.unavailableMessage)
    }

    @Test
    fun followedAnimeAreRestoredAndKeepTheAnimeListOrder() {
        val first = anime.copy(id = 1)
        val second = anime.copy(id = 2)
        val viewModel = AnimeViewModel(
            FakeDataSource(animeList = listOf(first, second)),
            CoroutineScope(Dispatchers.Unconfined),
            FakeFollowedAnimeStore(setOf(2, 99, 1)),
        )

        viewModel.loadAnime()
        viewModel.openFollowedAnime()

        assertEquals(AppScreen.FollowedAnimeList, viewModel.uiState.value.screen)
        assertEquals(listOf(first, second), viewModel.uiState.value.followedAnime)
    }

    @Test
    fun hiddenFollowedAnimeReturnsWhenTheSourceListContainsItAgain() {
        val source = MutableAnimeListDataSource()
        val store = FakeFollowedAnimeStore(setOf(anime.id))
        val viewModel = AnimeViewModel(source, CoroutineScope(Dispatchers.Unconfined), store)

        viewModel.loadAnime()
        assertEquals(emptyList<Anime>(), viewModel.uiState.value.followedAnime)

        source.items = listOf(anime)
        viewModel.retryAnime()

        assertEquals(listOf(anime), viewModel.uiState.value.followedAnime)
        assertEquals(setOf(anime.id), store.ids)
    }

    @Test
    fun followingAndUnfollowingAnimePersistsAndUpdatesTheList() {
        val store = FakeFollowedAnimeStore()
        val viewModel = AnimeViewModel(
            FakeDataSource(animeList = listOf(anime)),
            CoroutineScope(Dispatchers.Unconfined),
            store,
        )

        viewModel.loadAnime()
        viewModel.openAnime(anime)
        viewModel.toggleFollowedAnime()

        assertEquals(setOf(anime.id), viewModel.uiState.value.followedAnimeIds)
        assertEquals(setOf(anime.id), store.ids)

        viewModel.toggleFollowedAnime()

        assertEquals(emptySet<Int>(), viewModel.uiState.value.followedAnimeIds)
        assertEquals(emptySet<Int>(), store.ids)
    }

    @Test
    fun switchingToFollowedAnimeCancelsEpisodeLoading() {
        val source = DeferredDataSource()
        val viewModel = AnimeViewModel(source, CoroutineScope(Dispatchers.Unconfined))

        viewModel.openAnime(anime)
        viewModel.openFollowedAnime()

        assertEquals(setOf(anime.id), source.cancelledAnimeIds)
        assertEquals(AppScreen.FollowedAnimeList, viewModel.uiState.value.screen)
    }

    @Test
    fun followedAnimeFocusIsKeptSeparatelyFromTheMainList() {
        val viewModel = AnimeViewModel(
            FakeDataSource(animeList = listOf(anime)),
            CoroutineScope(Dispatchers.Unconfined),
            FakeFollowedAnimeStore(setOf(anime.id)),
        )

        viewModel.loadAnime()
        viewModel.rememberAnimeFocus(anime.id)
        viewModel.openFollowedAnime()
        viewModel.rememberAnimeFocus(anime.id)

        assertEquals(anime.id, viewModel.uiState.value.focusedAnimeId)
        assertEquals(anime.id, viewModel.uiState.value.focusedFollowedAnimeId)
    }

    @Test
    fun followedAnimeReturnsToTheFollowedList() {
        val viewModel = AnimeViewModel(
            FakeDataSource(animeList = listOf(anime)),
            CoroutineScope(Dispatchers.Unconfined),
            FakeFollowedAnimeStore(setOf(anime.id)),
        )

        viewModel.loadAnime()
        viewModel.openFollowedAnime()
        viewModel.openAnime(anime, EpisodeSource.FOLLOWED_ANIME_LIST)
        viewModel.back()

        assertEquals(AppScreen.FollowedAnimeList, viewModel.uiState.value.screen)
    }

    @Test
    fun unfollowedAnimeDisappearsFromTheFollowedListAfterReturning() {
        val viewModel = AnimeViewModel(
            FakeDataSource(animeList = listOf(anime)),
            CoroutineScope(Dispatchers.Unconfined),
            FakeFollowedAnimeStore(setOf(anime.id)),
        )

        viewModel.loadAnime()
        viewModel.openFollowedAnime()
        viewModel.openAnime(anime, EpisodeSource.FOLLOWED_ANIME_LIST)
        viewModel.toggleFollowedAnime()
        viewModel.back()

        assertEquals(emptyList<Anime>(), viewModel.uiState.value.followedAnime)
    }

    @Test
    fun failedFollowSaveKeepsTheStateAndReportsAnError() {
        val store = FakeFollowedAnimeStore(saveSucceeds = false)
        val viewModel = AnimeViewModel(
            FakeDataSource(animeList = listOf(anime)),
            CoroutineScope(Dispatchers.Unconfined),
            store,
        )

        viewModel.loadAnime()
        viewModel.openAnime(anime)
        viewModel.toggleFollowedAnime()

        assertEquals(emptySet<Int>(), viewModel.uiState.value.followedAnimeIds)
        assertEquals("关注保存失败", viewModel.uiState.value.followedAnimeSaveError)
    }

    @Test
    fun followedSaveErrorCanBeClearedAfterShowingFeedback() {
        val viewModel = AnimeViewModel(
            FakeDataSource(animeList = listOf(anime)),
            CoroutineScope(Dispatchers.Unconfined),
            FakeFollowedAnimeStore(saveSucceeds = false),
        )

        viewModel.openAnime(anime)
        viewModel.toggleFollowedAnime()
        viewModel.clearFollowedAnimeSaveError()

        assertEquals(null, viewModel.uiState.value.followedAnimeSaveError)
    }

    @Test
    fun loadingAnimeExposesTheCompleteList() {
        val animeList = (1..21).map { anime.copy(id = it) }
        val viewModel = AnimeViewModel(FakeDataSource(animeList = animeList), CoroutineScope(Dispatchers.Unconfined))

        viewModel.loadAnime()

        val state = viewModel.uiState.value
        assertTrue(state.anime is LoadState.Content)
        assertEquals(animeList, (state.anime as LoadState.Content).value)
    }

    @Test
    fun animeSearchKeepsItsQueryAcrossReloadAndClearAndCloseDoNotFetch() {
        val source = FakeDataSource(animeList = listOf(anime))
        val viewModel = AnimeViewModel(source, CoroutineScope(Dispatchers.Unconfined))

        viewModel.loadAnime()
        viewModel.openAnimeSearch()
        viewModel.updateAnimeSearchQuery("  anime  ")
        viewModel.clearAnimeSearch()
        viewModel.closeAnimeSearch()

        assertEquals(1, source.fetchAnimeListCalls)
        assertEquals("", viewModel.uiState.value.animeSearchQuery)
        assertFalse(viewModel.uiState.value.isAnimeSearchActive)

        viewModel.openAnimeSearch()
        viewModel.updateAnimeSearchQuery("anime")
        viewModel.retryAnime()

        assertEquals(2, source.fetchAnimeListCalls)
        assertEquals("anime", viewModel.uiState.value.animeSearchQuery)
        assertTrue(viewModel.uiState.value.isAnimeSearchActive)
    }

    @Test
    fun animeSearchContextSurvivesOpeningAnimeAndBackWithoutReloadingList() {
        val source = FakeDataSource(animeList = listOf(anime))
        val viewModel = AnimeViewModel(source, CoroutineScope(Dispatchers.Unconfined))

        viewModel.loadAnime()
        viewModel.openAnimeSearch()
        viewModel.updateAnimeSearchQuery("相反")
        viewModel.rememberAnimeFocus(anime.id)
        viewModel.openAnime(anime)
        viewModel.back()

        val state = viewModel.uiState.value
        assertEquals(AppScreen.AnimeList, state.screen)
        assertTrue(state.isAnimeSearchActive)
        assertEquals("相反", state.animeSearchQuery)
        assertEquals(anime.id, state.focusedAnimeId)
        assertTrue(state.restoreAnimeSearchFocus)
        assertEquals(1, source.fetchAnimeListCalls)
    }

    @Test
    fun restoredAnimeFocusConsumesTheSearchFocusSignal() {
        val viewModel = AnimeViewModel(
            FakeDataSource(animeList = listOf(anime)),
            CoroutineScope(Dispatchers.Unconfined),
        )

        viewModel.loadAnime()
        viewModel.openAnimeSearch()
        viewModel.openAnime(anime)
        viewModel.back()
        viewModel.rememberAnimeFocus(anime.id)

        assertFalse(viewModel.uiState.value.restoreAnimeSearchFocus)
    }

    @Test
    fun animeSearchContextKeepsUnavailableFocusForUiFallback() {
        val first = anime.copy(id = 1, title = "第一部")
        val second = anime.copy(id = 2, title = "第二部")
        val viewModel = AnimeViewModel(
            FakeDataSource(animeList = listOf(first, second)),
            CoroutineScope(Dispatchers.Unconfined),
        )

        viewModel.loadAnime()
        viewModel.openAnimeSearch()
        viewModel.updateAnimeSearchQuery("不存在")
        viewModel.openAnime(first)
        viewModel.back()

        assertTrue(viewModel.uiState.value.isAnimeSearchActive)
        assertTrue(viewModel.uiState.value.restoreAnimeSearchFocus)
        assertEquals("不存在", viewModel.uiState.value.animeSearchQuery)
    }

    @Test
    fun openingAnimeLoadsAllEpisodePagesBeforeShowingContent() {
        val secondEpisode = episode.copy(id = "2", title = "相反的你和我 第二季 [02]")
        val dataSource = FakeDataSource(
            pages = mapOf(
                anime.categoryUrl to EpisodePage(listOf(episode), "page-2"),
                "page-2" to EpisodePage(listOf(secondEpisode), null),
            ),
        )
        val viewModel = AnimeViewModel(dataSource, CoroutineScope(Dispatchers.Unconfined))

        viewModel.openAnime(anime)
        viewModel.rememberEpisodeFocus(episode.id)
        viewModel.toggleEpisodeSort()

        val state = viewModel.uiState.value
        assertEquals(AppScreen.EpisodeList, state.screen)
        assertEquals(EpisodeSort.OLDEST, state.episodeSort)
        assertEquals(null, state.focusedEpisodeId)
        assertEquals(LoadState.Content(listOf(episode, secondEpisode)), state.episodes)
        assertEquals(2, dataSource.fetchEpisodeCalls)
    }

    @Test
    fun repeatedEpisodePageStopsLoadingWithoutDuplicatingEpisodes() {
        val secondEpisode = episode.copy(id = "2", title = "相反的你和我 第二季 [02]")
        val dataSource = FakeDataSource(
            pages = mapOf(
                anime.categoryUrl to EpisodePage(listOf(episode), "page-2"),
                "page-2" to EpisodePage(listOf(secondEpisode), "page-2"),
            ),
        )
        val viewModel = AnimeViewModel(dataSource, CoroutineScope(Dispatchers.Unconfined))

        viewModel.openAnime(anime)

        assertEquals(LoadState.Content(listOf(episode, secondEpisode)), viewModel.uiState.value.episodes)
        assertEquals(2, dataSource.fetchEpisodeCalls)
    }

    @Test
    fun episodePageFailureDoesNotExposePartialEpisodes() {
        val dataSource = FakeDataSource(nextPageUrl = "page-2", failMorePage = true)
        val viewModel = AnimeViewModel(dataSource, CoroutineScope(Dispatchers.Unconfined))

        viewModel.openAnime(anime)

        assertEquals(LoadState.Error("更多剧集加载失败"), viewModel.uiState.value.episodes)
        assertEquals(2, dataSource.fetchEpisodeCalls)
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

    private class MutableAnimeListDataSource : Anime1DataSource {
        var items = emptyList<Anime>()

        override suspend fun fetchAnimeList() = items

        override suspend fun fetchEpisodes(anime: Anime, pageUrl: String) = EpisodePage(emptyList(), null)

        override suspend fun resolvePlayback(anime: Anime, episode: Episode) =
            PlayableSource("https://video.example/episode.mp4")
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
        private val pages: Map<String, EpisodePage> = emptyMap(),
    ) : Anime1DataSource {
        var fetchAnimeListCalls = 0
        var fetchEpisodeCalls = 0
        val resolvedEpisodes = mutableListOf<Episode>()

        override suspend fun fetchAnimeList(): List<Anime> {
            fetchAnimeListCalls++
            return animeList
        }

        override suspend fun fetchEpisodes(anime: Anime, pageUrl: String): EpisodePage {
            fetchEpisodeCalls++
            if (failMorePage && pageUrl == nextPageUrl) error("更多剧集加载失败")
            return pages[pageUrl] ?: EpisodePage(listOfNotNull(episode), nextPageUrl)
        }

        override suspend fun resolvePlayback(anime: Anime, episode: Episode): PlayableSource {
            resolvedEpisodes += episode
            return PlayableSource("https://video.example/episode.mp4")
        }
    }

    private class SeasonalFake(
        private val failFirstSchedule: Boolean = false,
    ) : Anime1DataSource {
        var scheduleCalls = 0
        val requestedSeasons = mutableListOf<AnimeSeason>()

        override suspend fun fetchAnimeList() = emptyList<Anime>()

        override suspend fun fetchCurrentSeason() = AnimeSeason(
            "2026年夏季新番",
            "https://anime1.me/2026年夏季新番",
        )

        override suspend fun fetchSeasonSchedule(season: AnimeSeason): AnimeSchedule {
            scheduleCalls++
            requestedSeasons += season
            if (failFirstSchedule && scheduleCalls == 1) error("季度排期失败")
            return AnimeSchedule(
                listOf(
                    listOf(SeasonalAnime("available", "有资源", "https://anime1.me/?cat=1940")),
                    listOf(SeasonalAnime("missing", "无资源", null)),
                    emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                ),
            )
        }

        override suspend fun fetchEpisodes(anime: Anime, pageUrl: String) = EpisodePage(emptyList(), null)

        override suspend fun resolvePlayback(anime: Anime, episode: Episode) =
            PlayableSource("https://video.example/episode.mp4")
    }

    private class FakeFollowedAnimeStore(
        initialIds: Set<Int> = emptySet(),
        private val saveSucceeds: Boolean = true,
    ) : FollowedAnimeStore {
        var ids = initialIds

        override fun load(): Set<Int> = ids

        override fun save(ids: Set<Int>): Boolean {
            if (saveSucceeds) this.ids = ids
            return saveSucceeds
        }
    }

    private class DeferredDataSource : Anime1DataSource {
        val requests = mutableMapOf<Int, CompletableDeferred<EpisodePage>>()
        val cancelledAnimeIds = mutableSetOf<Int>()

        override suspend fun fetchAnimeList() = emptyList<Anime>()

        override suspend fun fetchEpisodes(anime: Anime, pageUrl: String): EpisodePage = try {
            requests.getOrPut(anime.id) { CompletableDeferred() }.await()
        } finally {
            cancelledAnimeIds += anime.id
        }

        override suspend fun resolvePlayback(anime: Anime, episode: Episode) =
            PlayableSource("https://video.example/episode.mp4")
    }
}
