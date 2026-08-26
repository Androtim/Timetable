package com.androtim.timetable.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.androtim.timetable.data.CourseColorStore
import com.androtim.timetable.data.ScheduleRepository
import com.androtim.timetable.data.Settings
import com.androtim.timetable.data.model.ScheduleEvent
import com.androtim.timetable.sync.SyncWorker
import com.androtim.timetable.widget.TimetableWidgetProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ScheduleRepository.get(app)
    private val settings = Settings(app)
    private val colorStore = CourseColorStore(app)

    // ---------- Calendar feed ----------

    private val _feedUrl = MutableStateFlow(settings.feedUrl)
    val feedUrl: StateFlow<String?> = _feedUrl

    fun setFeedUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        settings.feedUrl = trimmed
        _feedUrl.value = trimmed
        SyncWorker.forceRefresh(getApplication())
    }

    val hasData: StateFlow<Boolean> = repo.observeCourseKeys()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** First/last day covered by the cached feed; null while the cache is empty. */
    val dateBounds: StateFlow<Pair<LocalDate, LocalDate>?> = repo.observeDateBounds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---------- Groups ----------

    /** Every distinct group token found in the feed with event counts, most-used first. */
    val groupTokens: StateFlow<List<com.androtim.timetable.data.model.GroupToken>> =
        repo.observeGroupTokens()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedGroups = MutableStateFlow(settings.appGroups)
    val selectedGroups: StateFlow<Set<String>> = _selectedGroups

    fun setSelectedGroups(groups: Set<String>) {
        settings.appGroups = groups
        _selectedGroups.value = groups
    }

    fun toggleGroup(token: String) {
        val current = _selectedGroups.value
        setSelectedGroups(if (token in current) current - token else current + token)
    }

    private val _widgetGroups = MutableStateFlow(settings.widgetGroups)
    val widgetGroups: StateFlow<Set<String>> = _widgetGroups

    fun setWidgetGroups(groups: Set<String>) {
        settings.widgetGroups = groups
        _widgetGroups.value = groups
        TimetableWidgetProvider.updateAllWidgets(getApplication())
    }

    fun toggleWidgetGroup(token: String) {
        val current = _widgetGroups.value
        setWidgetGroups(if (token in current) current - token else current + token)
    }

    // ---------- Sync status ----------

    val lastSyncMillis: StateFlow<Long> = callbackFlow {
        trySend(settings.lastSyncMillis)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Settings.KEY_LAST_SYNC) trySend(settings.lastSyncMillis)
        }
        settings.registerListener(listener)
        awaitClose { settings.unregisterListener(listener) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.lastSyncMillis)

    val isRefreshing: StateFlow<Boolean> = WorkManager.getInstance(app)
        .getWorkInfosForUniqueWorkFlow(SyncWorker.FORCE_WORK)
        .map { infos -> infos.any { !it.state.isFinished } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Configured but empty cache (e.g. after app data restore): populate right away.
        viewModelScope.launch {
            if (settings.feedUrl != null && !repo.hasData()) SyncWorker.forceRefresh(app)
        }
    }

    fun refreshNow() = SyncWorker.forceRefresh(getApplication())

    // ---------- Course colors ----------

    private val _colorMode = MutableStateFlow(settings.colorMode)
    val colorMode: StateFlow<String> = _colorMode

    fun setColorMode(mode: String) {
        settings.colorMode = mode
        _colorMode.value = mode
    }

    private val _singleColor = MutableStateFlow(settings.singleColor)
    val singleColor: StateFlow<Int> = _singleColor

    fun setSingleColor(argb: Int) {
        settings.singleColor = argb
        _singleColor.value = argb
    }

    private val _manualColors = MutableStateFlow(colorStore.all())
    val manualColors: StateFlow<Map<String, Int>> = _manualColors

    fun setManualColor(courseKey: String, argb: Int?) {
        if (argb == null) colorStore.clear(courseKey) else colorStore.set(courseKey, argb)
        _manualColors.value = colorStore.all()
    }

    /**
     * One color per course key according to the selected mode:
     *  - auto: hues spread evenly around the color wheel over the number of courses
     *  - single: the same user-chosen color for everything
     *  - manual: auto colors overridden by explicit per-course picks
     */
    val courseColors: StateFlow<Map<String, Color>> = combine(
        repo.observeCourseKeys(), _colorMode, _singleColor, _manualColors,
    ) { keys, mode, single, manual ->
        val n = keys.size.coerceAtLeast(1)
        val auto = keys.mapIndexed { i, key ->
            key to Color.hsv(i * 360f / n, 0.62f, 0.72f)
        }.toMap()
        when (mode) {
            Settings.COLOR_MODE_SINGLE -> keys.associateWith { Color(single) }
            Settings.COLOR_MODE_MANUAL -> auto + manual.mapValues { Color(it.value) }
            else -> auto
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---------- Events & notes ----------

    /** Events of one day for the groups currently selected in the app. */
    fun dayEvents(date: LocalDate): Flow<List<ScheduleEvent>> =
        selectedGroups.flatMapLatest { groups -> repo.observeDay(date, groups) }

    /** Events of [from, toExclusive) for the selected groups. */
    fun rangeEvents(from: LocalDate, toExclusive: LocalDate): Flow<List<ScheduleEvent>> =
        selectedGroups.flatMapLatest { groups -> repo.observeRange(from, toExclusive, groups) }

    /** uid → user note. */
    val notes: StateFlow<Map<String, String>> = repo.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun saveNote(uid: String, note: String) {
        viewModelScope.launch {
            repo.setNote(uid, note)
            TimetableWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    /** epochDay → whole-day note. */
    val dayNotes: StateFlow<Map<Long, String>> = repo.observeDayNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun saveDayNote(date: LocalDate, note: String) {
        viewModelScope.launch {
            repo.setDayNote(date.toEpochDay(), note)
            TimetableWidgetProvider.updateAllWidgets(getApplication())
        }
    }
}
