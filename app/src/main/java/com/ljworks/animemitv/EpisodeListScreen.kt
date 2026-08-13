package com.ljworks.animemitv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun EpisodeListScreen(state: AppUiState, viewModel: AnimeViewModel) {
    val anime = state.selectedAnime ?: return
    val episodeSortRequester = remember { FocusRequester() }
    Row(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        SideBar(
            onAnime = viewModel::navigateToAnime,
            onSeasonal = viewModel::openSeasonal,
            onFollowed = viewModel::openFollowedAnime,
            selected = when (state.episodeSource) {
                EpisodeSource.ANIME_LIST -> AppScreen.AnimeList
                EpisodeSource.SEASONAL_LIST -> AppScreen.SeasonalList
                EpisodeSource.FOLLOWED_ANIME_LIST -> AppScreen.FollowedAnimeList
            },
        )
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    anime.title,
                    modifier = Modifier.testTag("episode-title"),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = viewModel::toggleFollowedAnime,
                    modifier = Modifier.testTag("follow-anime-button"),
                ) {
                    Text(if (anime.id in state.followedAnimeIds) "取消关注" else "关注")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("剧集")
                if (state.episodes is LoadState.Content) {
                    Button(
                        onClick = viewModel::toggleEpisodeSort,
                        modifier = Modifier
                            .focusRequester(episodeSortRequester)
                            .testTag("episode-sort-button"),
                    ) {
                        Text(if (state.episodeSort == EpisodeSort.NEWEST) "最新集优先" else "第一集优先")
                    }
                }
            }
            when (val episodes = state.episodes) {
                LoadState.Loading -> StatusMessage("正在加载全部剧集…")
                is LoadState.Error -> RetryMessage(episodes.message, viewModel::retryEpisodes)
                is LoadState.Content -> EpisodeGrid(state, episodes.value, viewModel, episodeSortRequester)
                LoadState.Idle -> Unit
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeGrid(
    state: AppUiState,
    sourceEpisodes: List<Episode>,
    viewModel: AnimeViewModel,
    episodeSortRequester: FocusRequester,
) {
    val episodes = if (state.episodeSort == EpisodeSort.NEWEST) sourceEpisodes else sourceEpisodes.asReversed()
    val gridState = rememberLazyGridState()
    val firstCardRequester = remember { FocusRequester() }
    val targetEpisodeId = episodes.firstOrNull { it.id == state.focusedEpisodeId }?.id ?: episodes.firstOrNull()?.id
    val targetEpisodeIndex = episodes.indexOfFirst { it.id == targetEpisodeId }
    var focusRestored by remember(targetEpisodeId) { mutableStateOf(false) }
    LaunchedEffect(state.episodeSort) {
        if (targetEpisodeIndex >= 0) gridState.scrollToItem(targetEpisodeIndex)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (episodes.isEmpty()) {
            StatusMessage("没有可显示的剧集")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                state = gridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
                    val cardModifier = Modifier
                        .let { modifier ->
                            if (index < 5) modifier.focusProperties { up = episodeSortRequester } else modifier
                        }
                        .onFocusChanged { if (it.isFocused) viewModel.rememberEpisodeFocus(episode.id) }
                        .let { modifier ->
                            if (episode.id == targetEpisodeId) {
                                modifier
                                    .focusRequester(firstCardRequester)
                                    .onGloballyPositioned {
                                        if (!focusRestored) {
                                            focusRestored = true
                                            firstCardRequester.requestFocus()
                                        }
                                    }
                            } else {
                                modifier
                            }
                        }
                    Card(
                        onClick = { viewModel.playEpisode(episode) },
                        modifier = cardModifier.testTag("episode-card-${episode.id}").height(100.dp).fillMaxWidth(),
                        border = CardDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(3.dp, Color(0xFF8FE3E0)),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        ),
                        colors = CardDefaults.colors(
                            focusedContainerColor = Color(0xFF29466F),
                        ),
                        scale = CardDefaults.scale(focusedScale = 1.05f),
                        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                            verticalArrangement = Arrangement.Top,
                        ) {
                            Text(
                                episode.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
