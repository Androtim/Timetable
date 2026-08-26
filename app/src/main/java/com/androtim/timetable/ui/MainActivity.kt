package com.androtim.timetable.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androtim.timetable.R
import com.androtim.timetable.ui.theme.TimetableTheme

enum class ViewMode { DAY, WEEK }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: TimetableViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            TimetableTheme(themeMode) {
                MainScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: TimetableViewModel = viewModel()) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    // Week view is the default entry point, including when launched from the widget.
    var viewMode by rememberSaveable { mutableStateOf(ViewMode.WEEK) }
    val selectedGroups by vm.selectedGroups.collectAsStateWithLifecycle()
    val groupTokens by vm.groupTokens.collectAsStateWithLifecycle()
    val feedUrl by vm.feedUrl.collectAsStateWithLifecycle()
    val needsSetup = feedUrl == null

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showSetup && !needsSetup) { showSetup = false }

    if (needsSetup || showSetup) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.setup_title)) },
                    navigationIcon = {
                        if (!needsSetup) {
                            IconButton(onClick = { showSetup = false }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        }
                    },
                )
            }
        ) { padding ->
            SetupScreen(vm, onDone = { showSetup = false }, Modifier.padding(padding))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSettings) {
                        Text(stringResource(R.string.settings))
                    } else {
                        GroupSelector(
                            tokens = groupTokens,
                            selected = selectedGroups,
                            onToggle = vm::toggleGroup,
                            onClear = { vm.setSelectedGroups(emptySet()) },
                        )
                    }
                },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = {
                            viewMode = if (viewMode == ViewMode.DAY) ViewMode.WEEK else ViewMode.DAY
                        }) {
                            Icon(
                                if (viewMode == ViewMode.DAY) Icons.Default.CalendarViewWeek
                                else Icons.Default.CalendarViewDay,
                                contentDescription = stringResource(R.string.toggle_view)
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (showSettings) {
            SettingsScreen(
                vm,
                onChangeCalendar = { showSetup = true },
                Modifier.padding(padding),
            )
        } else {
            TimetableScreen(vm, viewMode, onModeChange = { viewMode = it }, Modifier.padding(padding))
        }
    }
}
