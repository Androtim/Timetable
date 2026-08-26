package com.androtim.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.androtim.timetable.R
import com.androtim.timetable.data.ScheduleRepository
import com.androtim.timetable.data.Settings
import com.androtim.timetable.data.model.CourseType
import com.androtim.timetable.data.model.PARIS_ZONE
import com.androtim.timetable.data.model.ScheduleEvent
import com.androtim.timetable.ui.MainActivity
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private data class WidgetSnapshot(
    val events: List<ScheduleEvent>,
    val hasAnyData: Boolean,
    val notes: Map<String, String>,
    val dayNote: String?,
)

/**
 * Home-screen widget with two size-responsive layouts:
 *  - compact (≈3×2): one full day at a glance, cards side by side, arrows = ±1 day
 *  - large (≈4 rows tall or more): the whole week as day columns, arrows = ±1 week
 * Always locked to the group chosen in the app's "Group to show in widget" setting.
 */
class TimetableWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateWidget(context, manager, id) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) {
        updateWidget(context, manager, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach {
            prefs.remove(dateKey(it)).remove(bgKey(it)).remove(scaleKey(it)).remove(cardKey(it))
        }
        prefs.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (id == -1) return
        val manager = AppWidgetManager.getInstance(context)
        val current = loadDate(context, id)
        // In week mode the arrows jump a whole week
        val delta = if (isWeekMode(manager, id)) 7L else 1L
        when (intent.action) {
            ACTION_PREV_DAY -> saveDateAndRedraw(context, id, current.minusDays(delta))
            ACTION_NEXT_DAY -> saveDateAndRedraw(context, id, current.plusDays(delta))
            ACTION_TODAY -> saveDateAndRedraw(context, id, LocalDate.now(PARIS_ZONE))
        }
    }

    private fun saveDateAndRedraw(context: Context, id: Int, date: LocalDate) {
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .edit().putLong(dateKey(id), date.toEpochDay()).apply()
        updateWidget(context, AppWidgetManager.getInstance(context), id)
    }

    companion object {
        const val ACTION_PREV_DAY = "com.androtim.timetable.widget.PREV_DAY"
        const val ACTION_NEXT_DAY = "com.androtim.timetable.widget.NEXT_DAY"
        const val ACTION_TODAY = "com.androtim.timetable.widget.TODAY"

        internal const val STATE_PREFS = "widget_state"
        private const val MAX_CARDS = 8
        private const val MAX_WEEK_CARDS_PER_DAY = 7

        /** Portrait height (dp) above which the widget flips to the weekly layout. */
        private const val WEEK_MODE_MIN_HEIGHT_DP = 280
        private const val WEEK_MODE_MIN_WIDTH_DP = 200

        /** Background opacity choices offered in the widget config screen. */
        internal val BG_OPTIONS = intArrayOf(
            R.drawable.widget_background_100,
            R.drawable.widget_background,      // default, ~93%
            R.drawable.widget_background_60,
            R.drawable.widget_background_30,
            R.drawable.widget_background_0,
        )
        internal const val DEFAULT_BG_INDEX = 1

        /** Text scale choices (Small / Medium / Large). */
        internal val TEXT_SCALES = floatArrayOf(0.85f, 1f, 1.2f)
        internal const val DEFAULT_SCALE_INDEX = 1

        /** Course-card color choices; exams always stay red. */
        internal val CARD_OPTIONS = intArrayOf(
            R.drawable.widget_card_background,   // default slate
            R.drawable.widget_card_charcoal,
            R.drawable.widget_card_indigo,
            R.drawable.widget_card_teal,
            R.drawable.widget_card_purple,
        )
        internal const val DEFAULT_CARD_INDEX = 0

        private val HEADER_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
        private val WEEK_DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
        private val WEEK_RANGE_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

        private fun dateKey(id: Int) = "date_$id"
        internal fun bgKey(id: Int) = "bg_$id"
        internal fun scaleKey(id: Int) = "scale_$id"
        internal fun cardKey(id: Int) = "card_$id"

        private fun loadDate(context: Context, id: Int): LocalDate {
            val epochDay = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                .getLong(dateKey(id), Long.MIN_VALUE)
            return if (epochDay == Long.MIN_VALUE) LocalDate.now(PARIS_ZONE)
            else LocalDate.ofEpochDay(epochDay)
        }

        private fun isWeekMode(manager: AppWidgetManager, id: Int): Boolean {
            val options = manager.getAppWidgetOptions(id)
            val portraitHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val portraitWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            return portraitHeight >= WEEK_MODE_MIN_HEIGHT_DP && portraitWidth >= WEEK_MODE_MIN_WIDTH_DP
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TimetableWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
        }

        /** Re-render one widget; used by the config screen after saving. */
        fun requestUpdate(context: Context, id: Int) =
            updateWidget(context, AppWidgetManager.getInstance(context), id)

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            val bgRes = BG_OPTIONS[
                state.getInt(bgKey(id), DEFAULT_BG_INDEX).coerceIn(0, BG_OPTIONS.lastIndex)
            ]
            val scale = TEXT_SCALES[
                state.getInt(scaleKey(id), DEFAULT_SCALE_INDEX).coerceIn(0, TEXT_SCALES.lastIndex)
            ]
            val cardRes = CARD_OPTIONS[
                state.getInt(cardKey(id), DEFAULT_CARD_INDEX).coerceIn(0, CARD_OPTIONS.lastIndex)
            ]
            val date = loadDate(context, id)

            val views = if (isWeekMode(manager, id)) {
                val portraitHeightDp =
                    manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                buildWeekWidget(context, id, date, bgRes, scale, cardRes, portraitHeightDp)
            } else {
                buildDayWidget(context, id, date, bgRes, scale, cardRes)
            }
            manager.updateAppWidget(id, views)
        }

        // ---------- Compact day layout ----------

        private fun buildDayWidget(
            context: Context,
            id: Int,
            date: LocalDate,
            bgRes: Int,
            scale: Float,
            cardRes: Int,
        ): RemoteViews {
            val settings = Settings(context)
            val repo = ScheduleRepository.get(context)

            // Local Room queries only — fast enough to run synchronously here.
            val snapshot = runBlocking {
                WidgetSnapshot(
                    events = repo.getDay(date, settings.widgetGroups).sortedBy { it.start },
                    hasAnyData = repo.hasData(),
                    notes = repo.getNotesMap(),
                    dayNote = repo.getDayNotesMap()[date.toEpochDay()],
                )
            }
            val events = snapshot.events

            val views = RemoteViews(context.packageName, R.layout.widget_timetable)
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
            views.setTextViewText(R.id.txt_date, date.format(HEADER_FORMAT))
            views.setTextViewTextSize(R.id.txt_date, TypedValue.COMPLEX_UNIT_SP, 16f * scale)

            if (snapshot.dayNote != null) {
                views.setTextViewText(R.id.widget_day_note, "📝 ${snapshot.dayNote}")
                views.setViewVisibility(R.id.widget_day_note, View.VISIBLE)
                views.setTextViewTextSize(R.id.widget_day_note, TypedValue.COMPLEX_UNIT_SP, 11f * scale)
            } else {
                views.setViewVisibility(R.id.widget_day_note, View.GONE)
            }

            attachClickIntents(context, views, id)

            views.removeAllViews(R.id.cards_container)
            if (events.isEmpty()) {
                views.setViewVisibility(R.id.cards_container, View.GONE)
                views.setViewVisibility(R.id.txt_empty, View.VISIBLE)
                views.setTextViewText(
                    R.id.txt_empty,
                    context.getString(
                        if (snapshot.hasAnyData) R.string.widget_no_classes else R.string.widget_no_data
                    )
                )
            } else {
                views.setViewVisibility(R.id.cards_container, View.VISIBLE)
                views.setViewVisibility(R.id.txt_empty, View.GONE)
                events.take(MAX_CARDS).forEach { event ->
                    views.addView(
                        R.id.cards_container,
                        buildCard(context, event, scale, cardRes, snapshot.notes[event.uid])
                    )
                }
            }
            return views
        }

        private fun buildCard(
            context: Context,
            event: ScheduleEvent,
            scale: Float,
            cardRes: Int,
            note: String?,
        ): RemoteViews {
            val card = RemoteViews(context.packageName, R.layout.widget_event_card)
            if (note != null) {
                card.setTextViewText(R.id.card_note, "📝 $note")
                card.setViewVisibility(R.id.card_note, View.VISIBLE)
                card.setTextViewTextSize(R.id.card_note, TypedValue.COMPLEX_UNIT_SP, 10f * scale)
            } else {
                card.setViewVisibility(R.id.card_note, View.GONE)
            }
            card.setTextViewTextSize(R.id.card_time, TypedValue.COMPLEX_UNIT_SP, 11f * scale)
            card.setTextViewTextSize(R.id.card_title, TypedValue.COMPLEX_UNIT_SP, 13f * scale)
            card.setTextViewTextSize(R.id.card_badge, TypedValue.COMPLEX_UNIT_SP, 10f * scale)
            card.setTextViewTextSize(R.id.card_room, TypedValue.COMPLEX_UNIT_SP, 12f * scale)
            card.setTextViewTextSize(R.id.card_teacher, TypedValue.COMPLEX_UNIT_SP, 11f * scale)

            val start = event.start.atZone(PARIS_ZONE).toLocalTime().format(TIME_FORMAT)
            val end = event.end.atZone(PARIS_ZONE).toLocalTime().format(TIME_FORMAT)
            card.setTextViewText(R.id.card_time, "$start – $end")

            val title = listOfNotNull(event.courseCode, event.courseName)
                .joinToString(" - ").ifEmpty { event.rawSummary }
            card.setTextViewText(R.id.card_title, title)
            card.setTextViewText(R.id.card_room, event.location)
            card.setTextViewText(R.id.card_teacher, event.teachers.joinToString(", "))

            if (event.isExam) {
                // Exams: no badge, prominent red card
                card.setInt(R.id.card_root, "setBackgroundResource", R.drawable.widget_card_background_exam)
                card.setViewVisibility(R.id.card_badge, View.GONE)
            } else {
                card.setInt(R.id.card_root, "setBackgroundResource", cardRes)
                val badgeRes = typeBadgeRes(event.type)
                if (badgeRes != 0) {
                    card.setViewVisibility(R.id.card_badge, View.VISIBLE)
                    card.setTextViewText(R.id.card_badge, event.typeLabel ?: event.type.name)
                    card.setInt(R.id.card_badge, "setBackgroundResource", badgeRes)
                } else {
                    card.setViewVisibility(R.id.card_badge, View.GONE)
                }
            }
            return card
        }

        // ---------- Large weekly layout ----------

        private fun buildWeekWidget(
            context: Context,
            id: Int,
            date: LocalDate,
            bgRes: Int,
            scale: Float,
            cardRes: Int,
            portraitHeightDp: Int,
        ): RemoteViews {
            val settings = Settings(context)
            val repo = ScheduleRepository.get(context)
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

            val snapshot = runBlocking {
                WidgetSnapshot(
                    events = repo.getRange(monday, monday.plusDays(7), settings.widgetGroups),
                    hasAnyData = repo.hasData(),
                    notes = repo.getNotesMap(),
                    dayNote = null,
                )
            }
            val byDay = snapshot.events.groupBy { it.start.atZone(PARIS_ZONE).toLocalDate() }
            val dayNotes = runBlocking { repo.getDayNotesMap() }
            val today = LocalDate.now(PARIS_ZONE)

            val saturday = monday.plusDays(5)
            val days = buildList {
                for (i in 0..4) add(monday.plusDays(i.toLong()))
                if (byDay[saturday].orEmpty().isNotEmpty()) add(saturday)
            }

            val views = RemoteViews(context.packageName, R.layout.widget_timetable_week)
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
            views.setTextViewText(
                R.id.txt_date,
                monday.format(WEEK_RANGE_FORMAT) + " – " + days.last().format(WEEK_RANGE_FORMAT)
            )
            views.setTextViewTextSize(R.id.txt_date, TypedValue.COMPLEX_UNIT_SP, 16f * scale)
            attachClickIntents(context, views, id)

            views.removeAllViews(R.id.week_container)
            if (snapshot.events.isEmpty()) {
                views.setViewVisibility(R.id.week_container, View.GONE)
                views.setViewVisibility(R.id.txt_empty, View.VISIBLE)
                views.setTextViewText(
                    R.id.txt_empty,
                    context.getString(
                        if (snapshot.hasAnyData) R.string.widget_no_classes else R.string.widget_no_data
                    )
                )
                return views
            }
            views.setViewVisibility(R.id.week_container, View.VISIBLE)
            views.setViewVisibility(R.id.txt_empty, View.GONE)

            // Time window shared by all columns so heights are comparable across days
            fun minuteOfDay(e: ScheduleEvent, end: Boolean): Int {
                val t = (if (end) e.end else e.start).atZone(PARIS_ZONE).toLocalTime()
                return t.hour * 60 + t.minute
            }

            val shown = days.flatMap { byDay[it].orEmpty() }
            val windowStart = ((shown.minOfOrNull { minuteOfDay(it, false) } ?: 480) / 60) * 60
            val windowEnd = shown.maxOfOrNull { minuteOfDay(it, true) }
                ?.let { ((it + 59) / 60) * 60 } ?: 1080
            val windowMinutes = (windowEnd - windowStart).coerceAtLeast(60)
            // Rough chrome estimate: widget padding + header row + day header
            val usableDp = (portraitHeightDp - 90).coerceAtLeast(120)
            val dpPerMinute = usableDp.toFloat() / windowMinutes
            val proportional = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

            days.forEach { day ->
                val column = RemoteViews(context.packageName, R.layout.widget_week_day)
                val noteMark = if (dayNotes.containsKey(day.toEpochDay())) " 📝" else ""
                column.setTextViewText(
                    R.id.day_header,
                    day.format(WEEK_DAY_FORMAT).uppercase() + noteMark
                )
                column.setTextViewTextSize(R.id.day_header, TypedValue.COMPLEX_UNIT_SP, 10f * scale)
                if (day == today) {
                    column.setTextColor(R.id.day_header, Color.parseColor("#8AB4F8"))
                }
                var cursor = windowStart
                byDay[day].orEmpty().sortedBy { it.start }
                    .take(MAX_WEEK_CARDS_PER_DAY)
                    .forEach { event ->
                        val startMin = minuteOfDay(event, end = false)
                        val endMin = minuteOfDay(event, end = true)
                        if (proportional) {
                            // Empty spacer for the gap before this event (e.g. lunch break)
                            val gap = startMin - cursor
                            if (gap > 5) {
                                val spacer = RemoteViews(context.packageName, R.layout.widget_week_spacer)
                                spacer.setViewLayoutHeight(
                                    R.id.week_spacer,
                                    gap * dpPerMinute,
                                    TypedValue.COMPLEX_UNIT_DIP
                                )
                                column.addView(R.id.day_events, spacer)
                            }
                        }
                        val card = buildWeekCard(
                            context, event, scale, cardRes,
                            snapshot.notes[event.uid],
                            durationMinutes = endMin - startMin,
                        )
                        if (proportional) {
                            val heightDp = ((endMin - startMin) * dpPerMinute - 2f).coerceAtLeast(16f)
                            card.setViewLayoutHeight(R.id.week_card, heightDp, TypedValue.COMPLEX_UNIT_DIP)
                        }
                        column.addView(R.id.day_events, card)
                        cursor = maxOf(cursor, endMin)
                    }
                views.addView(R.id.week_container, column)
            }
            return views
        }

        private fun buildWeekCard(
            context: Context,
            event: ScheduleEvent,
            scale: Float,
            cardRes: Int,
            note: String?,
            durationMinutes: Int,
        ): RemoteViews {
            val card = RemoteViews(context.packageName, R.layout.widget_week_event)
            card.setInt(
                R.id.week_card,
                "setBackgroundResource",
                if (event.isExam) R.drawable.widget_card_background_exam else cardRes
            )
            card.setTextViewText(
                R.id.we_time,
                event.start.atZone(PARIS_ZONE).toLocalTime().format(TIME_FORMAT)
            )
            card.setTextViewTextSize(R.id.we_time, TypedValue.COMPLEX_UNIT_SP, 8f * scale)

            val marker = if (note != null) " 📝" else ""
            card.setTextViewText(R.id.we_title, (event.courseCode ?: event.courseName) + marker)
            card.setTextViewTextSize(R.id.we_title, TypedValue.COMPLEX_UNIT_SP, 9f * scale)
            // Short events: one title line so the room chip stays visible
            card.setInt(R.id.we_title, "setMaxLines", if (durationMinutes < 90) 1 else 2)

            if (event.location.isNotBlank() && !event.isExam) {
                card.setViewVisibility(R.id.we_room, View.VISIBLE)
                card.setTextViewText(R.id.we_room, event.location)
                card.setTextViewTextSize(R.id.we_room, TypedValue.COMPLEX_UNIT_SP, 8f * scale)
                card.setInt(
                    R.id.we_room,
                    "setBackgroundResource",
                    typeBadgeRes(event.type).takeIf { it != 0 } ?: R.drawable.badge_other
                )
            } else {
                card.setViewVisibility(R.id.we_room, View.GONE)
            }
            return card
        }

        // ---------- Shared helpers ----------

        private fun typeBadgeRes(type: CourseType): Int = when (type) {
            CourseType.TP -> R.drawable.badge_tp
            CourseType.TD -> R.drawable.badge_td
            CourseType.CM -> R.drawable.badge_cm
            CourseType.OTHER -> 0
        }

        private fun attachClickIntents(context: Context, views: RemoteViews, id: Int) {
            views.setOnClickPendingIntent(R.id.btn_prev, actionIntent(context, id, ACTION_PREV_DAY))
            views.setOnClickPendingIntent(R.id.btn_next, actionIntent(context, id, ACTION_NEXT_DAY))
            views.setOnClickPendingIntent(R.id.txt_date, actionIntent(context, id, ACTION_TODAY))
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        }

        private fun actionIntent(context: Context, id: Int, action: String): PendingIntent {
            val intent = Intent(context, TimetableWidgetProvider::class.java)
                .setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            val actionCode = when (action) {
                ACTION_PREV_DAY -> 1
                ACTION_NEXT_DAY -> 2
                else -> 3
            }
            return PendingIntent.getBroadcast(
                context,
                id * 10 + actionCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
