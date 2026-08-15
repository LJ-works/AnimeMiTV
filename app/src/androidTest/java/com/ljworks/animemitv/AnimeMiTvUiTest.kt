package com.ljworks.animemitv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import com.ljworks.animemitv.ui.theme.AnimeMiTVTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AnimeMiTvUiTest {
    @Test
    fun firstTwoAnimeCardsMoveUpToSearchInsteadOfSidebar() {
        val anime = (1..5).map { Anime(it, "测试动画 $it", "1", "2026", "夏", "") }
        val viewModel = viewModel(anime)

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }
        waitForTag("anime-card-5")

        for (id in 1..2) {
            composeRule.onNodeWithTag("anime-card-$id").performSemanticsAction(SemanticsActions.RequestFocus)
            composeRule.onNodeWithTag("anime-card-$id").performKeyInput { pressKey(Key.DirectionUp) }
            composeRule.onNodeWithTag("anime-search-button").assertIsFocused()
        }
    }

    @Test
    fun movingFocusDownKeepsThePreviousRowVisible() {
        val anime = (1..10).map { Anime(it, "测试动画 $it", "1", "2026", "夏", "") }
        val viewModel = viewModel(anime)

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }
        waitForTag("anime-card-10")

        composeRule.onNodeWithTag("anime-card-1").performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag("anime-card-6").assertIsFocused()
        composeRule.onNodeWithTag("anime-card-1").assertIsDisplayed()
    }

    @Test
    fun animeSearchOpensWithInputFocusFiltersLiveClearsAndExits() {
        val first = Anime(1, "My Anime", "1", "2026", "夏", "")
        val second = Anime(2, "Another Show", "1", "2026", "夏", "")
        val viewModel = viewModel(listOf(first, second))

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }
        waitForTag("anime-card-2")
        composeRule.onNodeWithTag("anime-search-button").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithText("清除").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertIsDisplayed()
        composeRule.onNodeWithTag("anime-search-input").assertIsFocused().performTextInput("another")
        composeRule.onNodeWithTag("anime-card-2").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("anime-card-1").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("anime-search-clear").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("anime-search-input").assertIsFocused()
        composeRule.onNodeWithTag("anime-card-1").assertIsDisplayed()
        composeRule.onNodeWithTag("anime-card-2").assertIsDisplayed()
        composeRule.onNodeWithTag("anime-card-1").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("anime-card-1").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("anime-search-input").assertIsFocused()

        composeRule.onNodeWithTag("anime-search-input").performTextInput("missing")
        composeRule.onNodeWithText("没有找到匹配的动画").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("anime-card-1").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("anime-search-input").assertIsFocused()
        composeRule.onNodeWithTag("anime-search-close").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("anime-title").assertIsDisplayed()
        composeRule.onNodeWithTag("anime-search-button").assertIsDisplayed()
        composeRule.onNodeWithTag("anime-card-1").assertIsFocused()
    }

    @Test
    fun animeSearchSupportsSimplifiedTraditionalAndPinyinQueries() {
        val viewModel = viewModel(
            listOf(
                Anime(1, "动画", "1", "2026", "夏", ""),
                Anime(2, "其他", "1", "2026", "夏", ""),
            ),
        )

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }
        composeRule.onNodeWithTag("anime-search-button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("搜索标题或拼音").assertIsDisplayed()

        composeRule.onNodeWithTag("anime-search-input").performTextInput("Donghua")
        composeRule.onNodeWithTag("anime-card-1").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("anime-card-2").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("anime-search-clear").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("anime-search-input").performTextInput("Dhu")
        composeRule.onNodeWithTag("anime-card-1").assertIsDisplayed()

        composeRule.onNodeWithTag("anime-search-clear").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("anime-search-input").performTextInput("動畫")
        composeRule.onNodeWithTag("anime-card-1").assertIsDisplayed()
    }

    @Test
    fun animeSearchResultStillOpensEpisodes() {
        val anime = Anime(1933, "测试动画", "連載中(01)", "2026", "夏", "")
        val episode = Episode("1", "测试动画 [01]", "https://anime1.me/1", "request", "v1", "pt2")
        val viewModel = viewModel(listOf(anime), listOf(episode))

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }
        composeRule.onNodeWithTag("anime-search-button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("anime-search-input").performTextInput("动画")
        composeRule.onNodeWithTag("anime-card-1933").performSemanticsAction(SemanticsActions.OnClick)

        waitForText("剧集")
        composeRule.onNodeWithText("测试动画 [01]").assertIsDisplayed()
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun animeSearchRestoresQueryAndFocusedAnimeAfterEpisodeBack() {
        val anime = (1..2).map { Anime(it, "测试动画 $it", "1", "2026", "夏", "") }
        val episode = Episode("1", "测试动画 [01]", "https://anime1.me/1", "request", "v1", "pt2")
        val viewModel = viewModel(anime, listOf(episode))

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }
        waitForTag("anime-card-2")
        composeRule.onNodeWithTag("anime-search-button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("anime-search-input").performTextInput("测试")
        composeRule.onNodeWithTag("anime-card-2").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("anime-card-2").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("剧集")

        viewModel.back()

        waitForTag("anime-card-2")
        assertEquals("测试", viewModel.uiState.value.animeSearchQuery)
        composeRule.onNodeWithTag("anime-search-input").assertIsDisplayed()
        composeRule.onNodeWithTag("anime-card-2").assertIsFocused()
    }

    @Test
    fun animeSearchRestoresFirstResultWhenFocusedAnimeIsFilteredOut() {
        val first = Anime(1, "第一部", "1", "2026", "夏", "")
        val second = Anime(2, "第二部", "1", "2026", "夏", "")
        val episode = Episode("1", "第一部 [01]", "https://anime1.me/1", "request", "v1", "pt2")
        val viewModel = viewModel(listOf(first, second), listOf(episode))

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }
        waitForTag("anime-card-2")
        composeRule.onNodeWithTag("anime-search-button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("anime-search-input").performTextInput("第一")
        viewModel.rememberAnimeFocus(second.id)
        viewModel.openAnime(first)
        waitForText("剧集")

        viewModel.back()

        waitForTag("anime-card-1")
        composeRule.onNodeWithTag("anime-card-1").assertIsFocused()
        assertEquals(0, composeRule.onAllNodesWithTag("anime-card-2").fetchSemanticsNodes().size)
    }

    @Test
    fun animeSearchCanLeaveThroughSidebarAndReturnToFullList() {
        val viewModel = viewModel(listOf(Anime(1, "测试动画", "1", "2026", "夏", "")))

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }
        composeRule.onNodeWithTag("anime-search-button").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("anime-search-input").assertIsFocused()
        composeRule.onNodeWithTag("sidebar-followed").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("关注的动画").assertIsDisplayed()
        composeRule.onNodeWithTag("sidebar-animation").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("anime-title").assertIsDisplayed()
        composeRule.onNodeWithTag("anime-search-button").assertIsDisplayed()
        assertFalse(viewModel.uiState.value.isAnimeSearchActive)
        assertEquals("", viewModel.uiState.value.animeSearchQuery)
    }

    @Test
    fun animeHomeShowsSidebarAndTextCard() {
        val anime = Anime(1933, "测试动画", "連載中(01)", "2026", "夏", "")
        val viewModel = AnimeViewModel(
            object : Anime1DataSource {
                override suspend fun fetchAnimeList() = listOf(anime)
                override suspend fun fetchEpisodes(anime: Anime, pageUrl: String) = EpisodePage(emptyList(), null)
                override suspend fun resolvePlayback(anime: Anime, episode: Episode) =
                    PlayableSource("https://video.example/test.mp4")
            },
            CoroutineScope(Dispatchers.Unconfined),
        )

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }

        composeRule.onNodeWithTag("screen-background").assertIsDisplayed()
        composeRule.onNodeWithTag("sidebar").assertWidthIsEqualTo(90.dp)
        composeRule.onNodeWithText("AnimeMiTV").assertIsDisplayed()
        composeRule.onNodeWithTag("sidebar-animation").assertIsDisplayed()
        composeRule.onNodeWithTag("anime-top-bar").assertHeightIsEqualTo(56.dp)
        composeRule.onNodeWithTag("anime-title").assertIsDisplayed()
        composeRule.onNodeWithText("测试动画").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("anime-bottom-bar").fetchSemanticsNodes().size)
    }

    @Test
    fun historicalSeasonDoesNotHighlightCurrentWeekday() {
        val current = AnimeSeason("2026年夏季新番", "https://anime1.me/2026-summer")
        val historical = AnimeSeason("2026年春季新番", "https://anime1.me/2026-spring")
        val state = seasonalState(
            historical,
            listOf(SeasonalAnime("first", "第一部", "https://anime1.me/?cat=1")),
        ).copy(currentSeason = current)

        composeRule.setContent { AnimeMiTVTheme { SeasonalListScreen(state, viewModel(emptyList())) } }

        assertEquals(0, composeRule.onAllNodesWithTag("current-weekday-${java.time.LocalDate.now().dayOfWeek.value % 7}").fetchSemanticsNodes().size)
    }

    @Test
    fun seasonalScheduleInitiallyFocusesFirstCard() {
        val selectedSeason = AnimeSeason("2026年夏季新番", "https://anime1.me/2026-summer")
        val state = seasonalState(
            selectedSeason,
            listOf(SeasonalAnime("first", "第一部", "https://anime1.me/?cat=1")),
        )

        composeRule.setContent { AnimeMiTVTheme { SeasonalListScreen(state, viewModel(emptyList())) } }

        composeRule.onNodeWithTag("seasonal-card-first").assertIsFocused()
    }

    @Test
    fun seasonalHeaderDownFocusesSchedule() {
        val selectedSeason = AnimeSeason("2026年夏季新番", "https://anime1.me/2026-summer")
        val state = seasonalState(
            selectedSeason,
            listOf(SeasonalAnime("first", "第一部", "https://anime1.me/?cat=1")),
        )

        composeRule.setContent { AnimeMiTVTheme { SeasonalListScreen(state, viewModel(emptyList())) } }
        composeRule.onNodeWithTag("season-2026年夏季新番").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("season-2026年夏季新番").performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag("seasonal-card-first").assertIsFocused()
    }

    @Test
    fun seasonalHeaderFocusScrollsToOlderSeason() {
        val seasons = (0 until 21).map { AnimeSeason("2026年夏季新番-$it", "https://anime1.me/season-$it") }
        val state = AppUiState(
            screen = AppScreen.SeasonalList,
            seasonalDiscovery = LoadState.Content(seasons),
            currentSeason = seasons.first(),
            selectedSeason = seasons.first(),
            seasonalSchedule = LoadState.Content(
                AnimeSchedule(listOf(listOf(SeasonalAnime("first", "第一部", "https://anime1.me/?cat=1")), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())),
            ),
        )

        composeRule.setContent { AnimeMiTVTheme { SeasonalListScreen(state, viewModel(emptyList())) } }
        composeRule.onNodeWithTag("season-${seasons.last().label}").performSemanticsAction(SemanticsActions.RequestFocus)

        composeRule.onNodeWithTag("season-${seasons.last().label}").assertIsDisplayed()
    }

    @Test
    fun seasonalScheduleRestoresFocusedCard() {
        val selectedSeason = AnimeSeason("2026年夏季新番", "https://anime1.me/2026-summer")
        val state = seasonalState(
            selectedSeason,
            listOf(
                SeasonalAnime("first", "第一部", "https://anime1.me/?cat=1"),
                SeasonalAnime("restored", "恢复的动画", "https://anime1.me/?cat=2"),
            ),
        ).copy(focusedSeasonalAnimeId = "restored", seasonalScrollIndex = 1)

        composeRule.setContent { AnimeMiTVTheme { SeasonalListScreen(state, viewModel(emptyList())) } }

        composeRule.onNodeWithTag("seasonal-card-restored").assertIsFocused()
    }

    @Test
    fun seasonalCardUpFocusesSelectedSeason() {
        val selectedSeason = AnimeSeason("2026年夏季新番", "https://anime1.me/2026-summer")
        val schedule = AnimeSchedule(
            listOf(
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                listOf(SeasonalAnime("target", "测试新番", "https://anime1.me/?cat=1")),
                emptyList(),
            ),
        )
        val state = AppUiState(
            screen = AppScreen.SeasonalList,
            seasonalDiscovery = LoadState.Content(listOf(selectedSeason)),
            currentSeason = selectedSeason,
            selectedSeason = selectedSeason,
            seasonalSchedule = LoadState.Content(schedule),
        )

        composeRule.setContent { AnimeMiTVTheme { SeasonalListScreen(state, viewModel(emptyList())) } }
        composeRule.onNodeWithTag("seasonal-card-target").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("seasonal-card-target").performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.onNodeWithTag("season-2026年夏季新番").assertIsFocused()
    }

    @Test
    fun lowerSeasonalCardUpFocusesCardAbove() {
        val selectedSeason = AnimeSeason("2026年夏季新番", "https://anime1.me/2026-summer")
        val schedule = AnimeSchedule(
            listOf(
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                listOf(
                    SeasonalAnime("above", "上一排", "https://anime1.me/?cat=1"),
                    SeasonalAnime("below", "下一排", "https://anime1.me/?cat=2"),
                ),
                emptyList(),
            ),
        )
        val state = AppUiState(
            screen = AppScreen.SeasonalList,
            seasonalDiscovery = LoadState.Content(listOf(selectedSeason)),
            currentSeason = selectedSeason,
            selectedSeason = selectedSeason,
            seasonalSchedule = LoadState.Content(schedule),
        )

        composeRule.setContent { AnimeMiTVTheme { SeasonalListScreen(state, viewModel(emptyList())) } }
        composeRule.onNodeWithTag("seasonal-card-below").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("seasonal-card-below").performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.onNodeWithTag("seasonal-card-above").assertIsFocused()
    }

    @Test
    fun playbackErrorShowsRetryAndBack() {
        val anime = Anime(1933, "测试动画", "連載中(01)", "2026", "夏", "")
        val episode = Episode("1", "测试动画 [01]", "https://anime1.me/1", "request", "v1", "pt2")
        val viewModel = viewModel(listOf(anime), listOf(episode), playbackReady = false)

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }
        composeRule.onNodeWithTag("anime-card-1933").performSemanticsAction(SemanticsActions.OnClick)
        waitForTag("episode-card-1")
        composeRule.onNodeWithTag("episode-card-1").performSemanticsAction(SemanticsActions.OnClick)
        viewModel.playbackError(episode.id, "视频播放失败")

        composeRule.onNodeWithText("重试").assertIsDisplayed()
        composeRule.onNodeWithText("返回").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("剧集").assertIsDisplayed()
    }

    @Test
    fun selectingAnimeShowsItsEpisodes() {
        val anime = Anime(1933, "测试动画", "連載中(01)", "2026", "夏", "")
        val episode = Episode("1", "测试动画 [01]", "https://anime1.me/1", "request", "v1", "pt2")
        val viewModel = viewModel(listOf(anime), listOf(episode))

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }

        composeRule.onNodeWithTag("anime-card-1933").assertHasClickAction()
        composeRule.onNodeWithTag("anime-card-1933").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("剧集")
        composeRule.onNodeWithText("剧集").assertIsDisplayed()
        composeRule.onNodeWithTag("episode-title").assertIsDisplayed()
        waitForTag("episode-card-1")
        composeRule.onNodeWithText("测试动画 [01]").assertIsDisplayed()
    }

    @Test
    fun followedAnimePageShowsItsTitleAndSidebarEntry() {
        val viewModel = viewModel(emptyList())

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }

        composeRule.onNodeWithTag("sidebar-followed").assertIsDisplayed()
        composeRule.onNodeWithTag("sidebar-followed").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("关注的动画").assertIsDisplayed()
        composeRule.onNodeWithText("还没有关注的动画").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("anime-search-button").fetchSemanticsNodes().size)
    }

    @Test
    fun followedAnimePageShowsOnlyFollowedAnimeInSourceOrder() {
        val first = Anime(1, "第一部", "1", "2026", "夏", "")
        val second = Anime(2, "第二部", "1", "2026", "夏", "")
        val viewModel = viewModel(listOf(first, second), followedAnimeIds = setOf(2))

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }

        composeRule.onNodeWithTag("sidebar-followed").performSemanticsAction(SemanticsActions.OnClick)
        waitForTag("anime-card-2")
        composeRule.onNodeWithTag("anime-card-2").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("anime-card-1").fetchSemanticsNodes().size)
    }

    @Test
    fun episodePageCanFollowAnimation() {
        val anime = Anime(1933, "测试动画", "連載中(01)", "2026", "夏", "")
        val episode = Episode("1", "测试动画 [01]", "https://anime1.me/1", "request", "v1", "pt2")
        val viewModel = viewModel(listOf(anime), listOf(episode))

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }

        composeRule.onNodeWithTag("anime-card-1933").performSemanticsAction(SemanticsActions.OnClick)
        waitForTag("follow-anime-button")
        composeRule.onNodeWithTag("follow-anime-button").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithText("取消关注").assertIsDisplayed()
    }

    @Test
    fun dpadRestoresAnimeFocusAfterReturningFromEpisodes() {
        val anime = (1..2).map { Anime(it, "测试动画 $it", "1", "2026", "夏", "") }
        val viewModel = viewModel(anime)

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }

        waitForTag("anime-card-2")
        composeRule.onNodeWithTag("anime-card-1").assertIsFocused()
        composeRule.onNodeWithTag("anime-card-1").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithTag("anime-card-2").assertIsFocused()
        composeRule.onNodeWithTag("anime-card-2").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("剧集")

        viewModel.back()
        waitForTag("anime-card-2")
        composeRule.onNodeWithTag("anime-card-2").assertIsFocused()
    }

    @Test
    fun firstRowEpisodeCardsMoveUpToSortInsteadOfTheSidebar() {
        val anime = Anime(1933, "测试动画", "連載中(05)", "2026", "夏", "")
        val episodes = (1..5).map {
            Episode("$it", "测试动画 [$it]", "https://anime1.me/$it", "request", "v1", "pt2")
        }
        val viewModel = viewModel(listOf(anime), episodes)

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }

        composeRule.onNodeWithTag("anime-card-1933").performSemanticsAction(SemanticsActions.OnClick)
        waitForTag("episode-card-5")
        for (id in 1..5) {
            composeRule.onNodeWithTag("episode-card-$id").performSemanticsAction(SemanticsActions.RequestFocus)
            composeRule.onNodeWithTag("episode-card-$id").performKeyInput { pressKey(Key.DirectionUp) }
            composeRule.onNodeWithTag("episode-sort-button").assertIsFocused()
        }
    }

    @Test
    fun dpadRestoresEpisodeFocusAfterReturningFromPlayer() {
        val anime = Anime(1933, "测试动画", "連載中(01)", "2026", "夏", "")
        val episodes = (1..2).map {
            Episode("$it", "测试动画 [$it]", "https://anime1.me/$it", "request", "v1", "pt2")
        }
        val viewModel = viewModel(listOf(anime), episodes, playbackReady = false)

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }

        composeRule.onNodeWithTag("anime-card-1933").performSemanticsAction(SemanticsActions.OnClick)
        waitForTag("episode-card-2")
        composeRule.onNodeWithTag("episode-card-1").assertIsFocused()
        composeRule.onNodeWithTag("episode-card-1").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithTag("episode-card-2").assertIsFocused()
        composeRule.onNodeWithTag("episode-card-2").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("正在准备播放…")

        viewModel.back()
        waitForText("剧集")
        composeRule.onNodeWithTag("episode-card-2").assertIsFocused()
    }

    private fun seasonalState(season: AnimeSeason, items: List<SeasonalAnime>) = AppUiState(
        screen = AppScreen.SeasonalList,
        seasonalDiscovery = LoadState.Content(listOf(season)),
        currentSeason = season,
        selectedSeason = season,
        seasonalSchedule = LoadState.Content(
            AnimeSchedule(listOf(items, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())),
        ),
    )

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun viewModel(
        animeList: List<Anime>,
        episodes: List<Episode> = emptyList(),
        playbackReady: Boolean = true,
        followedAnimeIds: Set<Int> = emptySet(),
    ) = AnimeViewModel(
        object : Anime1DataSource {
            override suspend fun fetchAnimeList() = animeList
            override suspend fun fetchEpisodes(anime: Anime, pageUrl: String) = EpisodePage(episodes, null)
            override suspend fun resolvePlayback(anime: Anime, episode: Episode): PlayableSource {
                if (!playbackReady) CompletableDeferred<PlayableSource>().await()
                return PlayableSource("https://video.example/test.mp4")
            }
        },
        CoroutineScope(Dispatchers.Unconfined),
        object : FollowedAnimeStore {
            override fun load() = followedAnimeIds

            override fun save(ids: Set<Int>) = true
        },
    )
}
