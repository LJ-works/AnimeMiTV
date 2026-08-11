package com.ljworks.animemitv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ljworks.animemitv.ui.theme.AnimeMiTVTheme

class MainActivity : ComponentActivity() {
    private val animeViewModel by viewModels<AnimeViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AnimeViewModel(Anime1HttpDataSource()) as T
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnimeMiTVTheme {
                AnimeMiTVApp(animeViewModel)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeMiTVApp(viewModel: AnimeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.screen != AppScreen.AnimeList) {
        BackHandler { viewModel.back() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (state.screen) {
            AppScreen.AnimeList -> AnimeListScreen(state, viewModel)
            AppScreen.EpisodeList -> EpisodeListScreen(state, viewModel)
            AppScreen.Player -> PlayerScreen(state, viewModel)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeListScreen(state: AppUiState, viewModel: AnimeViewModel) {
    LaunchedEffect(Unit) {
        if (state.anime is LoadState.Loading || state.anime is LoadState.Idle) viewModel.loadAnime()
    }
    Row(modifier = Modifier.fillMaxSize().padding(28.dp)) {
        SideBar()
        Spacer(Modifier.width(30.dp))
        Column(modifier = Modifier.fillMaxSize()) {
            Text("动画", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(18.dp))
            when (val anime = state.anime) {
                LoadState.Loading -> StatusMessage("正在加载动画列表…")
                is LoadState.Error -> RetryMessage(anime.message, viewModel::retryAnime)
                is LoadState.Content -> {
                    val page = anime.value.page(state.animePageIndex)
                    AnimeGrid(page, state.focusedAnimeId, viewModel)
                }
                LoadState.Idle -> Unit
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SideBar() {
    Column(
        modifier = Modifier.width(170.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AnimeMiTV", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(28.dp))
        Button(onClick = {}, modifier = Modifier.testTag("sidebar-animation")) { Text("动画") }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeGrid(page: AnimePage, focusedAnimeId: Int?, viewModel: AnimeViewModel) {
    val firstCardRequester = remember { FocusRequester() }
    val targetAnimeId = page.items.firstOrNull { it.id == focusedAnimeId }?.id ?: page.items.firstOrNull()?.id
    LaunchedEffect(page.pageIndex, targetAnimeId) {
        if (targetAnimeId != null) firstCardRequester.requestFocus()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (page.hasPrevious) {
            PageSentinel("上一页") { viewModel.previousAnimePage() }
            Spacer(Modifier.height(10.dp))
        }
        if (page.items.isEmpty()) {
            StatusMessage("没有可显示的动画")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(page.items, key = { it.id }) { anime ->
                    val cardModifier = Modifier
                        .onFocusChanged { if (it.isFocused) viewModel.rememberAnimeFocus(anime.id) }
                        .let { if (anime.id == targetAnimeId) it.focusRequester(firstCardRequester) else it }
                    AnimeCard(
                        anime = anime,
                        modifier = cardModifier,
                        onClick = { viewModel.openAnime(anime) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("第 ${page.pageIndex + 1} / ${page.totalPages} 页")
            if (page.hasNext) PageSentinel("下一页") { viewModel.nextAnimePage() }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeCard(anime: Anime, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(150.dp).fillMaxWidth(),
        shape = androidx.tv.material3.CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(anime.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(anime.episodeStatus, style = MaterialTheme.typography.bodyMedium)
            Text("${anime.year} · ${anime.season}", style = MaterialTheme.typography.bodySmall)
            if (anime.fansub.isNotBlank()) Text(anime.fansub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeListScreen(state: AppUiState, viewModel: AnimeViewModel) {
    val anime = state.selectedAnime ?: return
    Row(modifier = Modifier.fillMaxSize().padding(28.dp)) {
        SideBar()
        Spacer(Modifier.width(30.dp))
        Column(modifier = Modifier.fillMaxSize()) {
            Text(anime.title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("剧集")
                Button(onClick = viewModel::toggleEpisodeSort) {
                    Text(if (state.episodeSort == EpisodeSort.NEWEST) "最新集优先" else "第一集优先")
                }
            }
            when (val episodes = state.episodes) {
                LoadState.Loading -> StatusMessage("正在加载剧集…")
                is LoadState.Error -> RetryMessage(episodes.message, viewModel::retryEpisodes)
                is LoadState.Content -> EpisodeGrid(state, episodes.value, viewModel)
                LoadState.Idle -> Unit
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeGrid(state: AppUiState, sourceEpisodes: List<Episode>, viewModel: AnimeViewModel) {
    val episodes = if (state.episodeSort == EpisodeSort.NEWEST) sourceEpisodes else sourceEpisodes.asReversed()
    val firstCardRequester = remember { FocusRequester() }
    val targetEpisodeId = episodes.firstOrNull { it.id == state.focusedEpisodeId }?.id ?: episodes.firstOrNull()?.id
    LaunchedEffect(state.episodeSort, targetEpisodeId) {
        if (targetEpisodeId != null) firstCardRequester.requestFocus()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (episodes.isEmpty()) {
            StatusMessage("没有可显示的剧集")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(episodes, key = { it.id }) { episode ->
                    val cardModifier = Modifier
                        .onFocusChanged { if (it.isFocused) viewModel.rememberEpisodeFocus(episode.id) }
                        .let { if (episode.id == targetEpisodeId) it.focusRequester(firstCardRequester) else it }
                    Card(onClick = { viewModel.playEpisode(episode) }, modifier = cardModifier.height(105.dp).fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                            Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (state.episodeLoadError != null) {
            RetryMessage(state.episodeLoadError, viewModel::loadMoreEpisodes)
        } else if (state.loadingMoreEpisodes) {
            StatusMessage("正在加载更早剧集…")
        } else if (state.nextEpisodePageUrl != null) {
            PageSentinel("加载更早剧集") { viewModel.loadMoreEpisodes() }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerScreen(state: AppUiState, viewModel: AnimeViewModel) {
    when (val playback = state.playback) {
        LoadState.Loading, LoadState.Idle -> StatusMessage("正在准备播放…")
        is LoadState.Error -> RetryMessage(playback.message, viewModel::retryPlayback)
        is LoadState.Content -> VideoPlayer(playback.value)
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun VideoPlayer(source: PlayableSource) {
    val context = LocalContext.current
    val player = remember(source.headers) {
        val dataSourceFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory)).build()
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(source.url) {
        player.setMediaItem(MediaItem.fromUri(source.url))
        player.prepare()
        player.playWhenReady = true
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; useController = true } },
        modifier = Modifier.fillMaxSize(),
        update = { it.player = player },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PageSentinel(label: String, onPage: () -> Unit) {
    var triggered by remember { mutableStateOf(false) }
    Button(
        onClick = onPage,
        modifier = Modifier.onFocusChanged {
            if (it.isFocused && !triggered) {
                triggered = true
                onPage()
            } else if (!it.isFocused) {
                triggered = false
            }
        },
    ) { Text(label) }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatusMessage(message: String) {
    Text(message, modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RetryMessage(message: String, retry: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(message)
        Button(onClick = retry) { Text("重试") }
    }
}
