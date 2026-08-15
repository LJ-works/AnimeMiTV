package com.ljworks.animemitv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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

private const val ANIME_GRID_COLUMNS = 5

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AnimeListScreen(state: AppUiState, viewModel: AnimeViewModel) {
    LaunchedEffect(Unit) {
        if (state.anime is LoadState.Loading || state.anime is LoadState.Idle) viewModel.loadAnime()
    }
    val followed = state.screen == AppScreen.FollowedAnimeList
    val source = if (followed) EpisodeSource.FOLLOWED_ANIME_LIST else EpisodeSource.ANIME_LIST
    val searchRequester = remember { FocusRequester() }
    val searchInputRequester = remember { FocusRequester() }
    BackHandler(enabled = !followed && state.isAnimeSearchActive) { viewModel.closeAnimeSearch() }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Column(modifier = Modifier.padding(top = 20.dp)) {
            SideBar(
                onAnime = viewModel::navigateToAnime,
                onSeasonal = viewModel::openSeasonal,
                onFollowed = viewModel::openFollowedAnime,
                selected = if (state.screen == AppScreen.FollowedAnimeList) {
                    AppScreen.FollowedAnimeList
                } else {
                    AppScreen.AnimeList
                },
            )
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("anime-top-bar"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!followed && state.isAnimeSearchActive) {
                    AnimeSearchHeader(state, viewModel, searchInputRequester)
                } else {
                    Text(
                        if (followed) "关注的动画" else "动画",
                        modifier = Modifier.testTag(if (followed) "followed-anime-title" else "anime-title"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (!followed) {
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = viewModel::openAnimeSearch,
                            modifier = Modifier
                                .focusRequester(searchRequester)
                                .testTag("anime-search-button"),
                        ) { Text("搜索") }
                    }
                }
            }
            when (val anime = state.anime) {
                LoadState.Loading -> StatusMessage("正在加载动画列表…")
                is LoadState.Error -> RetryMessage(anime.message, viewModel::retryAnime)
                is LoadState.Content -> {
                    val items = if (followed) {
                        state.followedAnime
                    } else {
                        remember(anime.value, state.animeSearchQuery) {
                            filterAnimeByTitle(anime.value, state.animeSearchQuery)
                        }
                    }
                    AnimeGrid(
                        items = items,
                        focusedAnimeId = if (followed) state.focusedFollowedAnimeId else state.focusedAnimeId,
                        viewModel = viewModel,
                        source = source,
                        restoreFocus = followed || !state.isAnimeSearchActive || state.restoreAnimeSearchFocus,
                        upRequester = when {
                            followed -> null
                            state.isAnimeSearchActive -> searchInputRequester
                            else -> searchRequester
                        },
                        emptyMessage = when {
                            followed -> "还没有关注的动画"
                            state.animeSearchQuery.isNotBlank() -> "没有找到匹配的动画"
                            else -> "没有可显示的动画"
                        },
                    )
                }
                LoadState.Idle -> Unit
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeSearchHeader(
    state: AppUiState,
    viewModel: AnimeViewModel,
    inputRequester: FocusRequester,
) {
    LaunchedEffect(Unit) {
        if (!state.restoreAnimeSearchFocus) {
            withFrameNanos { }
            runCatching { inputRequester.requestFocus() }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = state.animeSearchQuery,
            onValueChange = viewModel::updateAnimeSearchQuery,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .focusRequester(inputRequester)
                .testTag("anime-search-input"),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color(0xFF8FE3E0), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.animeSearchQuery.isEmpty()) {
                        Text("搜索动画", color = Color.LightGray)
                    }
                    Box(Modifier.weight(1f)) { innerTextField() }
                }
            },
        )
        Button(
            onClick = viewModel::clearAnimeSearch,
            modifier = Modifier.testTag("anime-search-clear"),
        ) { Text("清除") }
        Button(
            onClick = viewModel::closeAnimeSearch,
            modifier = Modifier.testTag("anime-search-close"),
        ) { Text("取消") }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeGrid(
    items: List<Anime>,
    focusedAnimeId: Int?,
    viewModel: AnimeViewModel,
    source: EpisodeSource,
    restoreFocus: Boolean,
    upRequester: FocusRequester?,
    emptyMessage: String,
) {
    val gridState = rememberLazyGridState()
    val firstCardRequester = remember { FocusRequester() }
    val targetAnimeId = items.firstOrNull { it.id == focusedAnimeId }?.id ?: items.firstOrNull()?.id
    val targetAnimeIndex = items.indexOfFirst { it.id == targetAnimeId }
    var focusRestored by remember(source, restoreFocus) { mutableStateOf(false) }
    LaunchedEffect(source, targetAnimeId, targetAnimeIndex, items.size) {
        if (targetAnimeIndex >= 0) {
            gridState.scrollToItem(targetAnimeIndex)
        } else if (restoreFocus) {
            focusRestored = true
        }
    }
    if (items.isEmpty()) {
        StatusMessage(emptyMessage)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(ANIME_GRID_COLUMNS),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(items, key = { _, anime -> anime.id }) { index, anime ->
                val cardModifier = Modifier
                    .let { modifier ->
                        if (index < ANIME_GRID_COLUMNS && upRequester != null) modifier.focusProperties { up = upRequester }
                        else modifier
                    }
                    .onFocusChanged { if (it.isFocused) viewModel.rememberAnimeFocus(anime.id) }
                    .let { modifier ->
                        if (anime.id == targetAnimeId && restoreFocus) {
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
                AnimeCard(
                    anime = anime,
                    modifier = cardModifier,
                    onClick = { viewModel.openAnime(anime, source) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeCard(anime: Anime, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.testTag("anime-card-${anime.id}").height(150.dp).fillMaxWidth(),
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
        Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                anime.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text(anime.episodeStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}
