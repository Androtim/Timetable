package com.androtim.timetable.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androtim.timetable.R
import com.androtim.timetable.data.model.CourseType
import com.androtim.timetable.data.model.PARIS_ZONE
import com.androtim.timetable.data.model.ScheduleEvent
import com.androtim.timetable.ui.theme.BadgeCm
import com.androtim.timetable.ui.theme.BadgeTd
import com.androtim.timetable.ui.theme.BadgeTp
import com.androtim.timetable.ui.theme.ExamRed
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_HEADER_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

/** Navigable date range, derived from the cached feed's first/last events. */
data class TimetableBounds(val start: LocalDate, val endInclusive: LocalDate) {
    val totalDays: Int = (ChronoUnit.DAYS.between(start, endInclusive) + 1).toInt().coerceAtLeast(1)
    val weekStart: LocalDate = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val totalWeeks: Int = (ChronoUnit.WEEKS.between(weekStart, endInclusive) + 1).toInt().coerceAtLeast(1)

    fun clamp(date: LocalDate): LocalDate = when {
        date.isBefore(start) -> start
        date.isAfter(endInclusive) -> endInclusive
        else -> date
    }
}

@Composable
fun TimetableScreen(
    vm: TimetableViewModel,
    viewMode: ViewMode,
    onModeChange: (ViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val boundsPair by vm.dateBounds.collectAsStateWithLifecycle()
    val today = LocalDate.now(PARIS_ZONE)
    val bounds = boundsPair?.let { TimetableBounds(it.first, it.second) }
        ?: TimetableBounds(today.minusDays(7), today.plusDays(30))

    var selectedEpochDay by rememberSaveable {
        mutableLongStateOf(today.toEpochDay())
    }
    val selectedDate = bounds.clamp(LocalDate.ofEpochDay(selectedEpochDay))

    // Back from day view returns to the week view instead of leaving the app
    BackHandler(enabled = viewMode == ViewMode.DAY) { onModeChange(ViewMode.WEEK) }

    Column(modifier.fillMaxSize()) {
        key(bounds) {
            when (viewMode) {
                ViewMode.DAY -> DayModeContent(
                    vm = vm,
                    bounds = bounds,
                    selectedDate = selectedDate,
                    onDateChange = { selectedEpochDay = it.toEpochDay() },
                )
                ViewMode.WEEK -> WeekModeContent(
                    vm = vm,
                    bounds = bounds,
                    selectedDate = selectedDate,
                    onDayClick = { date ->
                        selectedEpochDay = date.toEpochDay()
                        onModeChange(ViewMode.DAY)
                    },
                )
            }
        }
    }
}

// ---------- Day mode ----------

@Composable
private fun DayModeContent(
    vm: TimetableViewModel,
    bounds: TimetableBounds,
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    val initialPage = ChronoUnit.DAYS.between(bounds.start, selectedDate).toInt()
        .coerceIn(0, bounds.totalDays - 1)
    val pagerState = rememberPagerState(initialPage = initialPage) { bounds.totalDays }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        onDateChange(bounds.start.plusDays(pagerState.currentPage.toLong()))
    }

    val pageDate = bounds.start.plusDays(pagerState.currentPage.toLong())
    val dayNotes by vm.dayNotes.collectAsStateWithLifecycle()
    var dayNoteTarget by remember { mutableStateOf<LocalDate?>(null) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
            enabled = pagerState.currentPage > 0,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_day))
        }
        TextButton(
            onClick = {
                val today = bounds.clamp(LocalDate.now(PARIS_ZONE))
                scope.launch {
                    pagerState.animateScrollToPage(ChronoUnit.DAYS.between(bounds.start, today).toInt())
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(
                pageDate.format(DAY_HEADER_FORMAT).replaceFirstChar { it.uppercase() },
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        IconButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            enabled = pagerState.currentPage < bounds.totalDays - 1,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_day))
        }
        IconButton(onClick = { dayNoteTarget = pageDate }) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.day_note_button),
                tint = if (dayNotes.containsKey(pageDate.toEpochDay()))
                    Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        DayPage(vm, bounds.start.plusDays(page.toLong()))
    }

    dayNoteTarget?.let { date ->
        DayNoteDialog(
            date = date,
            initialNote = dayNotes[date.toEpochDay()].orEmpty(),
            onSave = { vm.saveDayNote(date, it) },
            onDismiss = { dayNoteTarget = null },
        )
    }
}

@Composable
private fun DayPage(vm: TimetableViewModel, date: LocalDate) {
    val events by remember(date) { vm.dayEvents(date) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val colors by vm.courseColors.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val dayNotes by vm.dayNotes.collectAsStateWithLifecycle()
    val dayNote = dayNotes[date.toEpochDay()]
    var selected by remember { mutableStateOf<ScheduleEvent?>(null) }

    if (events.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            if (dayNote != null) {
                DayNoteBanner(dayNote, Modifier.padding(12.dp))
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_classes), style = MaterialTheme.typography.bodyLarge)
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (dayNote != null) {
                item { DayNoteBanner(dayNote) }
            }
            items(events.size) { i ->
                EventCard(
                    event = events[i],
                    stripe = courseColor(events[i], colors),
                    note = notes[events[i].uid],
                    onClick = { selected = events[i] },
                )
            }
        }
    }

    selected?.let { event ->
        EventDetailDialog(
            event = event,
            initialNote = notes[event.uid].orEmpty(),
            color = courseColor(event, colors),
            onSave = { vm.saveNote(event.uid, it) },
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun DayNoteBanner(note: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            "📝 $note",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventCard(
    event: ScheduleEvent,
    stripe: Color,
    note: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val isExam = event.isExam
    // Stripe carries the course's semester color; the badge shows TP/TD/CM
    val stripeColor = if (isExam) Color.White else stripe
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isExam) ExamRed else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Color-coded stripe: blue TP, green TD, purple CM, white on red exams
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(stripeColor)
            )
            Row(Modifier.padding(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    event.start.atZone(PARIS_ZONE).toLocalTime().format(TIME_FORMAT),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColorFor(isExam),
                )
                Text(
                    event.end.atZone(PARIS_ZONE).toLocalTime().format(TIME_FORMAT),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColorFor(isExam).copy(alpha = 0.7f),
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isExam && event.type != CourseType.OTHER) {
                        TypeBadge(event.type, event.typeLabel)
                    }
                    if (isExam) {
                        Text(
                            stringResource(R.string.exam),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0x33000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    listOfNotNull(event.courseCode, event.courseName).joinToString(" - ")
                        .ifEmpty { event.rawSummary },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColorFor(isExam),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
                if (event.location.isNotBlank()) {
                    Text(
                        event.location,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = contentColorFor(isExam),
                    )
                }
                if (event.teachers.isNotEmpty()) {
                    Text(
                        event.teachers.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColorFor(isExam).copy(alpha = 0.75f),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
                if (note != null) {
                    Text(
                        "📝 $note",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isExam) Color(0xFFFFE082) else Color(0xFFFFB300),
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun contentColorFor(isExam: Boolean): Color =
    if (isExam) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun TypeBadge(type: CourseType, label: String? = null, small: Boolean = false) {
    val color = when (type) {
        CourseType.TP -> BadgeTp
        CourseType.TD -> BadgeTd
        CourseType.CM -> BadgeCm
        CourseType.OTHER -> MaterialTheme.colorScheme.outline
    }
    Text(
        label ?: type.name,
        color = Color.White,
        fontSize = if (small) 9.sp else 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(end = 6.dp)
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ---------- Week mode ----------

@Composable
private fun WeekModeContent(
    vm: TimetableViewModel,
    bounds: TimetableBounds,
    selectedDate: LocalDate,
    onDayClick: (LocalDate) -> Unit,
) {
    val initialWeek = ChronoUnit.WEEKS.between(
        bounds.weekStart,
        selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    ).toInt().coerceIn(0, bounds.totalWeeks - 1)
    val pagerState = rememberPagerState(initialPage = initialWeek) { bounds.totalWeeks }
    val scope = rememberCoroutineScope()

    val weekStart = bounds.weekStart.plusWeeks(pagerState.currentPage.toLong())
    val weekLabel = "%s – %s".format(
        weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
        weekStart.plusDays(5).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
    )

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
            enabled = pagerState.currentPage > 0,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_week))
        }
        Text(
            weekLabel,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
        )
        IconButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            enabled = pagerState.currentPage < bounds.totalWeeks - 1,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_week))
        }
    }

    // Legend: which color means what
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(BadgeTp, "TP")
        LegendDot(BadgeTd, "TD")
        LegendDot(BadgeCm, "CM")
        LegendDot(ExamRed, stringResource(R.string.legend_exam))
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        WeekPage(vm, bounds.weekStart.plusWeeks(page.toLong()), onDayClick)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 6.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(5.dp))
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

private val GUTTER_WIDTH = 34.dp

/** Stable per-course colors: one color per course code for the whole semester. */
private val COURSE_PALETTE = listOf(
    Color(0xFF3949AB), // indigo
    Color(0xFF00838F), // cyan
    Color(0xFF6A1B9A), // purple
    Color(0xFF2E7D32), // green
    Color(0xFFAD1457), // pink
    Color(0xFFEF6C00), // orange
    Color(0xFF5D4037), // brown
    Color(0xFF455A64), // blue grey
    Color(0xFF00695C), // teal
    Color(0xFF827717), // olive
    Color(0xFF4527A0), // deep purple
    Color(0xFF1565C0), // blue
)

/**
 * Course color: exams red; otherwise the evenly-spaced hue assigned by the
 * ViewModel, falling back to a hashed palette color until that map loads.
 */
fun courseColor(event: ScheduleEvent, colors: Map<String, Color>): Color {
    if (event.isExam) return ExamRed
    val key = event.courseCode ?: event.courseName
    return colors[key] ?: COURSE_PALETTE[abs(key.hashCode()) % COURSE_PALETTE.size]
}

private fun typePillColor(type: CourseType): Color = when (type) {
    CourseType.TP -> BadgeTp
    CourseType.TD -> BadgeTd
    CourseType.CM -> BadgeCm
    CourseType.OTHER -> Color(0xFF546E7A)
}

@Composable
private fun WeekPage(vm: TimetableViewModel, monday: LocalDate, onDayClick: (LocalDate) -> Unit) {
    val events by remember(monday) { vm.rangeEvents(monday, monday.plusDays(7)) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val byDay = remember(events) {
        events.groupBy { it.start.atZone(PARIS_ZONE).toLocalDate() }
    }
    val today = LocalDate.now(PARIS_ZONE)
    val colors by vm.courseColors.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val dayNotes by vm.dayNotes.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<ScheduleEvent?>(null) }
    var dayNoteTarget by remember { mutableStateOf<LocalDate?>(null) }

    // Saturday column only when that week actually has something on Saturday
    val saturday = monday.plusDays(5)
    val days = buildList {
        for (i in 0..4) add(monday.plusDays(i.toLong()))
        if (byDay[saturday].orEmpty().isNotEmpty()) add(saturday)
    }

    val shown = days.flatMap { byDay[it].orEmpty() }
    val startHour = minOf(8, shown.minOfOrNull { it.start.atZone(PARIS_ZONE).hour } ?: 8)
    val endHour = maxOf(18, shown.maxOfOrNull {
        val z = it.end.atZone(PARIS_ZONE)
        if (z.minute > 0) z.hour + 1 else z.hour
    } ?: 18)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(GUTTER_WIDTH))
            days.forEach { date ->
                Surface(
                    color = if (date == today) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clickable { dayNoteTarget = date },
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Text(
                            date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            date.dayOfMonth.toString() +
                                if (dayNotes.containsKey(date.toEpochDay())) " 📝" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // Fixed-height grid: the whole day range fits on screen, no scrolling
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 4.dp, bottom = 4.dp),
        ) {
            val dpPerHour = maxHeight / (endHour - startHour)
            Row(Modifier.fillMaxSize()) {
                // Hour scale down the left, complementing the exact times in the blocks
                Box(Modifier.width(GUTTER_WIDTH).fillMaxHeight()) {
                    for (h in startHour..endHour) {
                        Text(
                            "%02dh".format(h),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = dpPerHour * (h - startHour) - 6.dp)
                                .padding(end = 4.dp),
                        )
                    }
                }
                days.forEach { date ->
                    DayTimeColumn(
                        events = byDay[date].orEmpty().sortedBy { it.start },
                        startHour = startHour,
                        dpPerHour = dpPerHour,
                        endHour = endHour,
                        colors = colors,
                        notes = notes,
                        onEventClick = { selected = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }

    selected?.let { event ->
        EventDetailDialog(
            event = event,
            initialNote = notes[event.uid].orEmpty(),
            color = courseColor(event, colors),
            onSave = { vm.saveNote(event.uid, it) },
            onDismiss = { selected = null },
        )
    }

    dayNoteTarget?.let { date ->
        DayNoteDialog(
            date = date,
            initialNote = dayNotes[date.toEpochDay()].orEmpty(),
            onOpenDay = {
                dayNoteTarget = null
                onDayClick(date)
            },
            onSave = { vm.saveDayNote(date, it) },
            onDismiss = { dayNoteTarget = null },
        )
    }
}

@Composable
fun DayNoteDialog(
    date: LocalDate,
    initialNote: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenDay: (() -> Unit)? = null,
) {
    var text by remember(date) { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "📝 " + date.format(DAY_HEADER_FORMAT).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.day_note_label)) },
                    placeholder = { Text(stringResource(R.string.day_note_placeholder)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onOpenDay != null) {
                    TextButton(onClick = onOpenDay) { Text(stringResource(R.string.open_day_view)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(text)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun DayTimeColumn(
    events: List<ScheduleEvent>,
    startHour: Int,
    endHour: Int,
    dpPerHour: Dp,
    colors: Map<String, Color>,
    notes: Map<String, String>,
    onEventClick: (ScheduleEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(horizontal = 1.dp)) {
        for (h in startHour..endHour) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .offset(y = dpPerHour * (h - startHour))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
        events.forEach { event ->
            val startZ = event.start.atZone(PARIS_ZONE)
            val topMinutes = (startZ.hour - startHour) * 60 + startZ.minute
            val durationMinutes = Duration.between(event.start, event.end).toMinutes()
                .toInt().coerceAtLeast(30)
            WeekBlock(
                event = event,
                background = courseColor(event, colors),
                note = notes[event.uid],
                onClick = { onEventClick(event) },
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = dpPerHour * (topMinutes / 60f))
                    .height(dpPerHour * (durationMinutes / 60f))
                    .padding(vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun WeekBlock(
    event: ScheduleEvent,
    background: Color,
    note: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            event.start.atZone(PARIS_ZONE).toLocalTime().format(TIME_FORMAT) +
                "–" + event.end.atZone(PARIS_ZONE).toLocalTime().format(TIME_FORMAT),
            fontSize = 8.sp,
            lineHeight = 9.sp,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
        )
        Text(
            event.courseCode ?: event.courseName,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = Color.White,
        )
        // Flexible line: the course name gives way first so the room pill
        // below always stays visible, even in short blocks.
        if (event.courseCode != null) {
            Text(
                event.courseName,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (note != null) {
            Text(
                "📝 $note",
                fontSize = 9.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFE082),
            )
        }
        if (event.isExam) {
            Text(
                stringResource(R.string.exam),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        } else if (event.location.isNotBlank()) {
            // Room pill carries the TP/TD/CM color
            Text(
                event.location,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .background(typePillColor(event.type), RoundedCornerShape(50))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun EventDetailDialog(
    event: ScheduleEvent,
    initialNote: String,
    color: Color,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var noteText by remember(event.uid) { mutableStateOf(initialNote) }
    val dateLine = event.start.atZone(PARIS_ZONE)
        .format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
        .replaceFirstChar { it.uppercase() } +
        " • " + event.start.atZone(PARIS_ZONE).toLocalTime().format(TIME_FORMAT) +
        " – " + event.end.atZone(PARIS_ZONE).toLocalTime().format(TIME_FORMAT)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(color, RoundedCornerShape(4.dp))
                )
                Text(
                    listOfNotNull(event.courseCode, event.courseName).joinToString(" - ")
                        .ifEmpty { event.rawSummary },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.isExam) {
                        Text(
                            stringResource(R.string.exam),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(ExamRed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    } else if (event.type != CourseType.OTHER) {
                        Text(
                            event.typeLabel ?: event.type.name,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(typePillColor(event.type), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(dateLine, style = MaterialTheme.typography.bodyMedium)
                if (event.location.isNotBlank()) {
                    Text(
                        stringResource(R.string.room_label, event.location),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (event.teachers.isNotEmpty()) {
                    Text(
                        stringResource(R.string.teacher_label, event.teachers.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (event.groupTokens.isNotEmpty()) {
                    Text(
                        stringResource(R.string.groups_label, event.groupTokens.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.note)) },
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(noteText)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
