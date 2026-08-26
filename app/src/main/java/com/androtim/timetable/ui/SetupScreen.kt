package com.androtim.timetable.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androtim.timetable.R

/**
 * First-launch calendar setup, also reachable later from Settings.
 * Step 1: paste the .ics URL and load it. Step 2 (once data arrived):
 * pick the group tokens that apply to you.
 */
@Composable
fun SetupScreen(vm: TimetableViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val feedUrl by vm.feedUrl.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val hasData by vm.hasData.collectAsStateWithLifecycle()
    val tokens by vm.groupTokens.collectAsStateWithLifecycle()
    val selected by vm.selectedGroups.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(feedUrl ?: "") }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.setup_url_label)) },
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.setFeedUrl(url) },
            enabled = url.trim().startsWith("http") && !isRefreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.refreshing))
            } else {
                Text(stringResource(R.string.setup_load))
            }
        }
        if (isRefreshing) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.setup_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (hasData) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.setup_pick_groups), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.setup_groups_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            tokens.forEach { info ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggleGroup(info.token) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = info.token in selected,
                        onCheckedChange = { vm.toggleGroup(info.token) },
                    )
                    Text("${info.token} (${info.count})")
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    // Widget follows the same groups until customized separately
                    if (vm.widgetGroups.value.isEmpty()) vm.setWidgetGroups(selected)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.done))
            }
        }
    }
}
