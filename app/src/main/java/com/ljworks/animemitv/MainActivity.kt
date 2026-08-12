package com.ljworks.animemitv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
internal fun AnimeMiTVApp(viewModel: AnimeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.screen == AppScreen.EpisodeList) BackHandler { viewModel.back() }

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
