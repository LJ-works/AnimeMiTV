package com.ljworks.animemitv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Alignment
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SeasonalListScreen(state: AppUiState, viewModel: AnimeViewModel) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(20.dp)) {
            SideBar(viewModel::navigateToAnime, viewModel::openSeasonal, AppScreen.SeasonalList)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.fillMaxSize()) {
                when (val discovery = state.seasonalDiscovery) {
                    LoadState.Idle, LoadState.Loading -> StatusMessage("正在发现当前季度…")
                    is LoadState.Error -> RetryMessage(discovery.message, viewModel::retrySeasonal)
                    is LoadState.Content -> SeasonalContent(state, discovery.value, viewModel)
                }
            }
        }
        state.unavailableMessage?.let { message ->
            Text(
                message,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 32.dp, vertical = 20.dp)
                    .testTag("unavailable-message"),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonalContent(state: AppUiState, seasons: List<AnimeSeason>, viewModel: AnimeViewModel) {
    val seasonScroll = rememberScrollState()
    val selectedSeasonRequester = remember { FocusRequester() }
    val firstCardRequester = remember { FocusRequester() }
    val seasonScope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(seasonScroll).testTag("season-selector"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.width(12.dp))
            seasons.forEach { season ->
                val selected = state.selectedSeason == season
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                Button(
                    onClick = { viewModel.selectSeason(season) },
                    modifier = Modifier
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                seasonScope.launch { bringIntoViewRequester.bringIntoView() }
                            }
                        }
                        .focusProperties { down = firstCardRequester }
                        .testTag("season-${season.label}")
                        .then(if (selected) Modifier.focusRequester(selectedSeasonRequester) else Modifier),
                    colors = if (selected) {
                        ButtonDefaults.colors(
                            containerColor = Color(0xFF162B4A),
                            contentColor = Color(0xFF7B8AA3),
                        )
                    } else {
                        ButtonDefaults.colors()
                    },
                ) { Text(season.label) }
            }
        }
        when (val schedule = state.seasonalSchedule) {
            LoadState.Idle, LoadState.Loading -> StatusMessage("正在加载季度排期…")
            is LoadState.Error -> RetryMessage(
                schedule.message,
                retry = { state.selectedSeason?.let(viewModel::selectSeason) },
            )
            is LoadState.Content -> ScheduleGrid(
                state,
                schedule.value,
                selectedSeasonRequester,
                viewModel,
                firstCardRequester,
                Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScheduleGrid(
    state: AppUiState,
    schedule: AnimeSchedule,
    selectedSeasonRequester: FocusRequester,
    viewModel: AnimeViewModel,
    firstCardRequester: FocusRequester,
    modifier: Modifier,
) {
    val rows = (0 until (schedule.days.maxOfOrNull { it.size } ?: 0)).toList()
    val first = schedule.days.flatten().firstOrNull()?.id
    val target = state.focusedSeasonalAnimeId ?: first
    val listState = rememberLazyListState(state.seasonalScrollIndex + 1)
    LaunchedEffect(state.selectedSeason, state.seasonalSchedule) {
        listState.scrollToItem((state.seasonalScrollIndex - 2).coerceAtLeast(0) + 1)
        if (target != null) firstCardRequester.requestFocus()
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("seasonal-schedule"),
        state = listState,
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                schedule.days.forEachIndexed { index, _ ->
                    Text(
                        listOf("日", "一", "二", "三", "四", "五", "六")[index],
                        modifier = Modifier
                            .weight(1f)
                            .testTag(if (isCurrentDay(state, index)) "current-weekday-$index" else "weekday-$index"),
                        color = if (isCurrentDay(state, index)) Color(0xFF8FE3E0) else Color.Unspecified,
                    )
                }
            }
        }
        itemsIndexed(rows) { rowIndex, _ ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                schedule.days.forEach { day ->
                    val anime = day.getOrNull(rowIndex)
                    if (anime == null) Spacer(Modifier.weight(1f).height(92.dp))
                    else SeasonalCard(
                        anime,
                        rowIndex,
                        if (anime.id == target) firstCardRequester else null,
                        if (rowIndex == 0) selectedSeasonRequester else null,
                        Modifier.weight(1f),
                        viewModel,
                    )
                }
            }
        }
    }
}

private fun isCurrentDay(state: AppUiState, day: Int): Boolean =
    state.selectedSeason == state.currentSeason && day == LocalDate.now().dayOfWeek.toSundayIndex()

private fun DayOfWeek.toSundayIndex(): Int = (value % 7)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonalCard(
    anime: SeasonalAnime,
    rowIndex: Int,
    focusRequester: FocusRequester?,
    upRequester: FocusRequester?,
    modifier: Modifier,
    viewModel: AnimeViewModel,
) {
    val cardModifier = modifier
        .height(92.dp)
        .then(upRequester?.let { requester -> Modifier.focusProperties { up = requester } } ?: Modifier)
        .onFocusChanged { if (it.isFocused) viewModel.rememberSeasonalFocus(anime.id, rowIndex) }
        .testTag("seasonal-card-${anime.id}")
        .then(focusRequester?.let(Modifier::focusRequester) ?: Modifier)
    Card(
        onClick = { viewModel.confirmSeasonalAnime(anime) },
        modifier = cardModifier,
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color(0xFF8FE3E0)),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
        colors = CardDefaults.colors(
            containerColor = if (anime.hasResource) Color(0xFF243A5C) else Color(0xFF3A3A3A),
            focusedContainerColor = if (anime.hasResource) Color(0xFF29466F) else Color(0xFF555555),
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(anime.title, maxLines = 3, style = MaterialTheme.typography.bodySmall)
            if (!anime.hasResource) Text("暂无资源", color = Color.LightGray)
        }
    }
}
