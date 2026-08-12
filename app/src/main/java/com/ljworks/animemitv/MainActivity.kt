package com.ljworks.animemitv

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
import com.ljworks.animemitv.ui.theme.BackgroundEnd
import com.ljworks.animemitv.ui.theme.BackgroundStart

internal fun seekPosition(currentPosition: Long, duration: Long, offset: Long): Long {
    val target = (currentPosition + offset).coerceAtLeast(0L)
    return if (duration != C.TIME_UNSET && duration >= 0) target.coerceAtMost(duration) else target
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private class AnimePlayerView(context: Context) : PlayerView(context) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) {
            return super.dispatchKeyEvent(event)
        }
        val activePlayer = player ?: return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play()
                showController()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                seekBy(activePlayer, -10_000L)
                showController()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                seekBy(activePlayer, 10_000L)
                showController()
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun seekBy(activePlayer: Player, offset: Long) {
        activePlayer.seekTo(seekPosition(activePlayer.currentPosition, activePlayer.duration, offset))
    }
}

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(BackgroundStart, BackgroundEnd)))
                .testTag("screen-background"),
        ) {
            when (state.screen) {
                AppScreen.AnimeList -> AnimeListScreen(state, viewModel)
                AppScreen.EpisodeList -> EpisodeListScreen(state, viewModel)
                AppScreen.Player -> PlayerScreen(state, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AnimeListScreen(state: AppUiState, viewModel: AnimeViewModel) {
    LaunchedEffect(Unit) {
        if (state.anime is LoadState.Loading || state.anime is LoadState.Idle) viewModel.loadAnime()
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Column(modifier = Modifier.padding(top = 20.dp)) {
            SideBar()
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
private fun SideBar() {
    Column(
        modifier = Modifier.width(90.dp).fillMaxHeight().testTag("sidebar"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AnimeMiTV", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        Button(onClick = {}, modifier = Modifier.testTag("sidebar-animation")) {
            Text("动画", style = MaterialTheme.typography.bodyMedium)
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
    LaunchedEffect(targetAnimeId) {
        if (targetAnimeIndex >= 0) gridState.scrollToItem(targetAnimeIndex)
    }
    if (items.isEmpty()) {
        StatusMessage("没有可显示的动画")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
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
    Row(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        SideBar()
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                anime.title,
                modifier = Modifier.testTag("episode-title"),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
    val gridState = rememberLazyGridState()
    val firstCardRequester = remember { FocusRequester() }
    val targetEpisodeId = episodes.firstOrNull { it.id == state.focusedEpisodeId }?.id ?: episodes.firstOrNull()?.id
    val targetEpisodeIndex = episodes.indexOfFirst { it.id == targetEpisodeId }
    var focusRestored by remember(targetEpisodeId) { mutableStateOf(false) }
    LaunchedEffect(state.episodeSort, targetEpisodeId) {
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
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(episodes, key = { it.id }) { episode ->
                    val cardModifier = Modifier
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
                        modifier = cardModifier.testTag("episode-card-${episode.id}").height(105.dp).fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                            Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("episode-bottom-bar"),
        ) {
            if (state.episodeLoadError != null) {
                RetryMessage(state.episodeLoadError, viewModel::loadMoreEpisodes)
            } else if (state.loadingMoreEpisodes) {
                StatusMessage("正在加载更早剧集…")
            } else if (state.nextEpisodePageUrl != null) {
                PageSentinel("加载更早剧集") { viewModel.loadMoreEpisodes() }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerScreen(state: AppUiState, viewModel: AnimeViewModel) {
    when (val playback = state.playback) {
        LoadState.Loading, LoadState.Idle -> StatusMessage("正在准备播放…")
        is LoadState.Error -> RetryMessage(playback.message, viewModel::retryPlayback, "返回", viewModel::back)
        is LoadState.Content -> {
            val episodeId = state.selectedEpisode?.id
            if (episodeId == null) {
                RetryMessage("没有找到当前剧集", viewModel::retryPlayback, "返回", viewModel::back)
            } else {
                VideoPlayer(playback.value, episodeId, viewModel::playbackError)
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun VideoPlayer(
    source: PlayableSource,
    episodeId: String,
    onError: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val player = remember(source.headers) {
        val dataSourceFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory)).build()
    }
    DisposableEffect(player, episodeId) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError(episodeId, error.message ?: "视频播放失败")
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(source.url) {
        player.setMediaItem(MediaItem.fromUri(source.url))
        player.prepare()
        player.playWhenReady = true
    }
    AndroidView(
        factory = { AnimePlayerView(it).apply { this.player = player; useController = true } },
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
        modifier = Modifier.testTag("page-sentinel-$label").onFocusChanged {
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
    Text(message, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyLarge)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RetryMessage(
    message: String,
    retry: () -> Unit,
    secondaryLabel: String? = null,
    secondary: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = retry) { Text("重试") }
            if (secondaryLabel != null && secondary != null) {
                Button(onClick = secondary) { Text(secondaryLabel) }
            }
        }
    }
}
