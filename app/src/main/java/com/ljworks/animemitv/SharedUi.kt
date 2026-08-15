package com.ljworks.animemitv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SideBar(
    onAnime: () -> Unit,
    onSeasonal: () -> Unit,
    onFollowed: () -> Unit,
    selected: AppScreen,
) {
    Column(
        modifier = Modifier.width(90.dp).fillMaxHeight().testTag("sidebar"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AnimeMiTV", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAnime,
            modifier = Modifier.testTag("sidebar-animation"),
            enabled = selected != AppScreen.AnimeList,
        ) {
            Text("动画", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSeasonal,
            modifier = Modifier.testTag("sidebar-seasonal"),
            enabled = selected != AppScreen.SeasonalList,
        ) {
            Text("季度新番", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onFollowed,
            modifier = Modifier.testTag("sidebar-followed"),
            enabled = selected != AppScreen.FollowedAnimeList,
        ) {
            Text("关注", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun StatusMessage(message: String) {
    Text(message, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyLarge)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ExitConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dismissRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        dismissRequester.requestFocus()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .testTag("exit-confirm-dialog")
                .background(Color(0xFF102B4D), RoundedCornerShape(16.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("确定要退出 AnimeMiTV 吗？", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .focusRequester(dismissRequester)
                        .testTag("exit-confirm-dismiss"),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.testTag("exit-confirm-confirm"),
                ) {
                    Text("退出")
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun RetryMessage(
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
