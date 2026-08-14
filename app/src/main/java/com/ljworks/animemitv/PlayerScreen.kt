package com.ljworks.animemitv

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class AnimePlayerView(context: Context, attrs: AttributeSet?) : PlayerView(context, attrs) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            showController()
            findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress).run {
                requestFocus()
                dispatchKeyEvent(event)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
internal fun PlayerScreen(state: AppUiState, viewModel: AnimeViewModel) {
    BackHandler { viewModel.back() }
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var controllerVisible by remember { mutableStateOf(false) }
    var hideController by remember { mutableStateOf<(() -> Unit)?>(null) }
    BackHandler(controllerVisible) { hideController?.invoke() }
    val player = remember(source.headers) {
        val dataSourceFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory)).build()
    }
    DisposableEffect(player, lifecycleOwner, episodeId) {
        var resumePlaybackOnStart = false
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                resumePlaybackOnStart = false
                onError(episodeId, error.message ?: "视频播放失败")
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumePlaybackOnStart = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (resumePlaybackOnStart) {
                        resumePlaybackOnStart = false
                        player.play()
                    }
                }
                else -> Unit
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
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
        factory = {
            (LayoutInflater.from(it).inflate(R.layout.anime_player_view, null) as AnimePlayerView).apply {
                this.player = player
                useController = true
                setShowPreviousButton(false)
                setShowNextButton(false)
                setShowRewindButton(false)
                setShowFastForwardButton(false)
                val timeBar = findViewById<ConfirmTimeBar>(androidx.media3.ui.R.id.exo_progress)
                timeBar.setKeyTimeIncrement(15_000L)
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                    controllerVisible = visibility == View.VISIBLE
                    if (!controllerVisible) {
                        findFocus()?.clearFocus()
                        requestFocus()
                    }
                })
                hideController = {
                    timeBar.cancelPreview()
                    hideController()
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { it.player = player },
    )
}
