package com.androtim.timetable.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androtim.timetable.R
import com.androtim.timetable.data.Settings
import com.androtim.timetable.data.model.PARIS_ZONE
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Swatches offered by the single-color and manual pickers. */
private val PICKER_COLORS = listOf(
    0xFF3949AB, 0xFF1565C0, 0xFF0277BD, 0xFF00838F,
    0xFF00695C, 0xFF2E7D32, 0xFF558B2F, 0xFF827717,
    0xFFEF6C00, 0xFFD84315, 0xFFAD1457, 0xFF6A1B9A,
    0xFF4527A0, 0xFF5D4037, 0xFF455A64, 0xFF37474F,
).map { it.toInt() }

/** Multi-select group dropdown shown in the top bar. */
@Composable
fun GroupSelector(
    tokens: List<com.androtim.timetable.data.model.GroupToken>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when {
        selected.isEmpty() -> stringResource(R.string.all_groups)
        selected.size == 1 -> selected.first()
        else -> stringResource(R.string.groups_selected, selected.size)
    }
    TextButton(onClick = { expanded = true }) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.change_group))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.all_groups)) },
            leadingIcon = {
                if (selected.isEmpty()) Icon(Icons.Default.Check, contentDescription = null)
            },
            onClick = { onClear() },
        )
        tokens.forEach { info ->
            DropdownMenuItem(
                text = { Text("${info.token} (${info.count})") },
                leadingIcon = {
                    Checkbox(
                        checked = info.token in selected,
                        onCheckedChange = { onToggle(info.token) },
                    )
                },
                onClick = { onToggle(info.token) },
            )
        }
    }
}

@Composable
fun SettingsScreen(
    vm: TimetableViewModel,
    onChangeCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedUrl by vm.feedUrl.collectAsStateWithLifecycle()
    val tokens by vm.groupTokens.collectAsStateWithLifecycle()
    val widgetGroups by vm.widgetGroups.collectAsStateWithLifecycle()
    val lastSync by vm.lastSyncMillis.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val colorMode by vm.colorMode.collectAsStateWithLifecycle()
    val singleColor by vm.singleColor.collectAsStateWithLifecycle()
    val courseColors by vm.courseColors.collectAsStateWithLifecycle()
    val manualColors by vm.manualColors.collectAsStateWithLifecycle()
    var pickingCourse by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ----- Calendar -----
        Text(stringResource(R.string.calendar_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            feedUrl ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onChangeCalendar) {
            Text(stringResource(R.string.change_calendar))
        }

        SectionDivider()

        // ----- Widget groups -----
        Text(stringResource(R.string.widget_group_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.widget_group_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        tokens.forEach { info ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.toggleWidgetGroup(info.token) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = info.token in widgetGroups,
                    onCheckedChange = { vm.toggleWidgetGroup(info.token) },
                )
                Text("${info.token} (${info.count})")
            }
        }

        SectionDivider()

        // ----- Course colors -----
        Text(stringResource(R.string.color_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        ColorModeOption(R.string.color_mode_auto, colorMode == Settings.COLOR_MODE_AUTO) {
            vm.setColorMode(Settings.COLOR_MODE_AUTO)
        }
        ColorModeOption(R.string.color_mode_single, colorMode == Settings.COLOR_MODE_SINGLE) {
            vm.setColorMode(Settings.COLOR_MODE_SINGLE)
        }
        ColorModeOption(R.string.color_mode_manual, colorMode == Settings.COLOR_MODE_MANUAL) {
            vm.setColorMode(Settings.COLOR_MODE_MANUAL)
        }

        if (colorMode == Settings.COLOR_MODE_SINGLE) {
            Spacer(Modifier.height(8.dp))
            SwatchGrid(selected = singleColor, onPick = { vm.setSingleColor(it) })
        }

        if (colorMode == Settings.COLOR_MODE_MANUAL) {
            Spacer(Modifier.height(8.dp))
            courseColors.keys.sorted().forEach { courseKey ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { pickingCourse = courseKey }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(courseColors[courseKey] ?: Color.Gray, CircleShape)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(courseKey, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        SectionDivider()

        // ----- Sync -----
        Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::refreshNow, enabled = !isRefreshing) {
            if (isRefreshing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
            Spacer(Modifier.size(8.dp))
            Text(stringResource(if (isRefreshing) R.string.refreshing else R.string.refresh_now))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (lastSync == 0L) stringResource(R.string.last_sync_never)
            else stringResource(
                R.string.last_sync,
                Instant.ofEpochMilli(lastSync)
                    .atZone(PARIS_ZONE)
                    .format(DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.getDefault())),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.auto_sync_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionDivider()

        // ----- About / transparency -----
        val context = LocalContext.current
        val versionName = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "?"
        }
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.about_version, versionName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.about_ai),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Androtim/Timetable"))
                )
            }) { Text(stringResource(R.string.about_source)) }
            TextButton(onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Androtim/Timetable/blob/main/PRIVACY.md")
                    )
                )
            }) { Text(stringResource(R.string.about_privacy_link)) }
        }
    }

    pickingCourse?.let { courseKey ->
        AlertDialog(
            onDismissRequest = { pickingCourse = null },
            title = { Text(stringResource(R.string.pick_color)) },
            text = {
                Column {
                    Text(courseKey, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    SwatchGrid(
                        selected = manualColors[courseKey],
                        onPick = {
                            vm.setManualColor(courseKey, it)
                            pickingCourse = null
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        vm.setManualColor(courseKey, null)
                        pickingCourse = null
                    }) {
                        Text(stringResource(R.string.reset_color))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingCourse = null }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ColorModeOption(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwatchGrid(selected: Int?, onPick: (Int) -> Unit) {
    PICKER_COLORS.chunked(8).forEach { rowColors ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowColors.forEach { argb ->
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Color(argb), CircleShape)
                        .clickable { onPick(argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected == argb) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.wrapContentSize(),
                        )
                    }
                }
            }
        }
    }
}
