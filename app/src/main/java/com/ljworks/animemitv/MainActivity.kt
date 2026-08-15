package com.ljworks.animemitv

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.ljworks.animemitv.ui.theme.AnimeMiTVTheme
import com.ljworks.animemitv.ui.theme.BackgroundEnd
import com.ljworks.animemitv.ui.theme.BackgroundStart

class MainActivity : ComponentActivity() {
    private val animeViewModel by viewModels<AnimeViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AnimeViewModel(
                    Anime1HttpDataSource(),
                    followedAnimeStore = SharedPreferencesFollowedAnimeStore(this@MainActivity),
                ) as T
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnimeMiTVTheme {
                AnimeMiTVApp(animeViewModel, onExit = ::finish)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AnimeMiTVApp(
    viewModel: AnimeViewModel,
    onExit: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.followedAnimeSaveError) {
        state.followedAnimeSaveError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearFollowedAnimeSaveError()
        }
    }
    if (state.screen != AppScreen.AnimeList &&
        state.screen != AppScreen.SeasonalList &&
        state.screen != AppScreen.FollowedAnimeList
    ) {
        BackHandler { viewModel.back() }
    } else {
        BackHandler(enabled = !state.isExitConfirmVisible && !state.isAnimeSearchActive) {
            viewModel.requestExit()
        }
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
                AppScreen.SeasonalList -> SeasonalListScreen(state, viewModel)
                AppScreen.FollowedAnimeList -> AnimeListScreen(state, viewModel)
                AppScreen.EpisodeList -> EpisodeListScreen(state, viewModel)
                AppScreen.Player -> PlayerScreen(state, viewModel)
            }
        }
    }

    if (state.isExitConfirmVisible) {
        ExitConfirmDialog(
            onDismiss = viewModel::dismissExit,
            onConfirm = {
                viewModel.dismissExit()
                onExit()
            },
        )
    }
}
