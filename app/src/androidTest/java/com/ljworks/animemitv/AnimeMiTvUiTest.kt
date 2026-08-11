package com.ljworks.animemitv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

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

        composeRule.setContent { AnimeMiTVApp(viewModel) }

        composeRule.onNodeWithText("AnimeMiTV").assertIsDisplayed()
        composeRule.onNodeWithTag("sidebar-animation").assertIsDisplayed()
        composeRule.onNodeWithText("测试动画").assertIsDisplayed()
    }

    @Test
    fun nextPageShowsTheRemainingAnime() {
        val viewModel = viewModel((1..21).map { Anime(it, "测试动画 $it", "1", "2026", "夏", "") })

        composeRule.setContent { AnimeMiTVApp(viewModel) }

        composeRule.onNodeWithText("下一页").performClick()
        composeRule.onNodeWithText("测试动画 21").assertIsDisplayed()
    }

    @Test
    fun selectingAnimeShowsItsEpisodes() {
        val anime = Anime(1933, "测试动画", "連載中(01)", "2026", "夏", "")
        val episode = Episode("1", "测试动画 [01]", "https://anime1.me/1", "request", "v1", "pt2")
        val viewModel = viewModel(listOf(anime), listOf(episode))

        composeRule.setContent { AnimeMiTVApp(viewModel) }

        composeRule.onNodeWithText("测试动画").performClick()
        composeRule.onNodeWithText("剧集").assertIsDisplayed()
        composeRule.onNodeWithText("测试动画 [01]").assertIsDisplayed()
    }

    private fun viewModel(animeList: List<Anime>, episodes: List<Episode> = emptyList()) = AnimeViewModel(
        object : Anime1DataSource {
            override suspend fun fetchAnimeList() = animeList
            override suspend fun fetchEpisodes(anime: Anime, pageUrl: String) = EpisodePage(episodes, null)
            override suspend fun resolvePlayback(anime: Anime, episode: Episode) =
                PlayableSource("https://video.example/test.mp4")
        },
        CoroutineScope(Dispatchers.Unconfined),
    )
}
