package com.ljworks.animemitv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SideBar() {
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
internal fun StatusMessage(message: String) {
    Text(message, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyLarge)
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
