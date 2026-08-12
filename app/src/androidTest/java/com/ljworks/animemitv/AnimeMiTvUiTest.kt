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
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.ljworks.animemitv.ui.theme.AnimeMiTVTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AnimeMiTvUiTest {
    @get:Rule
    val composeRule = createComposeRule()

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
        composeRule.onNodeWithTag("anime-bottom-bar").assertIsDisplayed()
        composeRule.onNodeWithText("测试动画").assertIsDisplayed()
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
    fun nextPageShowsTheRemainingAnime() {
        val viewModel = viewModel((1..21).map { Anime(it, "测试动画 $it", "1", "2026", "夏", "") })

        composeRule.setContent { AnimeMiTVTheme { AnimeMiTVApp(viewModel) } }

        composeRule.onNodeWithTag("page-sentinel-下一页").performSemanticsAction(SemanticsActions.OnClick)
        waitForTag("anime-card-21")
        composeRule.onNodeWithText("测试动画 21").assertIsDisplayed()
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
    )
}
