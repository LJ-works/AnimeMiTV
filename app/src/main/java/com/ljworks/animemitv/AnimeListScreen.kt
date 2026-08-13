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
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AnimeListScreen(state: AppUiState, viewModel: AnimeViewModel) {
    LaunchedEffect(Unit) {
        if (state.anime is LoadState.Loading || state.anime is LoadState.Idle) viewModel.loadAnime()
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Column(modifier = Modifier.padding(top = 20.dp)) {
            SideBar(
                onAnime = viewModel::navigateToAnime,
                onSeasonal = viewModel::openSeasonal,
                selected = AppScreen.AnimeList,
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
                Text("动画", modifier = Modifier.testTag("anime-title"), style = MaterialTheme.typography.bodyLarge)
            }
            when (val anime = state.anime) {
                LoadState.Loading -> StatusMessage("正在加载动画列表…")
                is LoadState.Error -> RetryMessage(anime.message, viewModel::retryAnime)
                is LoadState.Content -> AnimeGrid(anime.value, state.focusedAnimeId, viewModel)
                LoadState.Idle -> Unit
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeGrid(items: List<Anime>, focusedAnimeId: Int?, viewModel: AnimeViewModel) {
    val gridState = rememberLazyGridState()
    val firstCardRequester = remember { FocusRequester() }
    val targetAnimeId = items.firstOrNull { it.id == focusedAnimeId }?.id ?: items.firstOrNull()?.id
    val targetAnimeIndex = items.indexOfFirst { it.id == targetAnimeId }
    var focusRestored by remember(targetAnimeId) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (targetAnimeIndex >= 0) gridState.scrollToItem(targetAnimeIndex)
    }
    if (items.isEmpty()) {
        StatusMessage("没有可显示的动画")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(items, key = { it.id }) { anime ->
                val cardModifier = Modifier
                    .onFocusChanged { if (it.isFocused) viewModel.rememberAnimeFocus(anime.id) }
                    .let { modifier ->
                        if (anime.id == targetAnimeId) {
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
                    onClick = { viewModel.openAnime(anime) },
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
