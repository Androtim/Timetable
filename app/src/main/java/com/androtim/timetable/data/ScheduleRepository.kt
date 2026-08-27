package com.androtim.timetable.data

import android.content.Context
import com.androtim.timetable.data.db.AppDatabase
import com.androtim.timetable.data.db.DayNoteEntity
import com.androtim.timetable.data.db.EventEntity
import com.androtim.timetable.data.db.NoteEntity
import com.androtim.timetable.data.ics.IcsParser
import com.androtim.timetable.data.model.GroupFilter
import com.androtim.timetable.data.model.DISPLAY_ZONE
import com.androtim.timetable.data.model.ScheduleEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ScheduleRepository(context: Context) {

    private val dao = AppDatabase.get(context).eventDao()
    private val noteDao = AppDatabase.get(context).noteDao()
    private val dayNoteDao = AppDatabase.get(context).dayNoteDao()
    private val settings = Settings(context)

    // ADE servers can take minutes to generate a year-long export under load,
    // and their gateway 504s at exactly 5 minutes — the read timeout must
    // outlast that so failures surface as HTTP errors, not socket timeouts.
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.MINUTES)
        .callTimeout(8, TimeUnit.MINUTES)
        .build()

    fun observeDay(date: LocalDate, groups: Set<String>): Flow<List<ScheduleEvent>> {
        val (from, to) = date.dayBoundsMillis()
        return dao.observeRange(from, to).map { list -> list.toDomainFor(groups) }
    }

    fun observeRange(from: LocalDate, toExclusive: LocalDate, groups: Set<String>): Flow<List<ScheduleEvent>> {
        val fromMillis = from.atStartOfDay(DISPLAY_ZONE).toInstant().toEpochMilli()
        val toMillis = toExclusive.atStartOfDay(DISPLAY_ZONE).toInstant().toEpochMilli()
        return dao.observeRange(fromMillis, toMillis).map { list -> list.toDomainFor(groups) }
    }

    suspend fun getDay(date: LocalDate, groups: Set<String>): List<ScheduleEvent> {
        val (from, to) = date.dayBoundsMillis()
        return dao.getRange(from, to).toDomainFor(groups)
    }

    suspend fun getRange(from: LocalDate, toExclusive: LocalDate, groups: Set<String>): List<ScheduleEvent> {
        val fromMillis = from.atStartOfDay(DISPLAY_ZONE).toInstant().toEpochMilli()
        val toMillis = toExclusive.atStartOfDay(DISPLAY_ZONE).toInstant().toEpochMilli()
        return dao.getRange(fromMillis, toMillis).toDomainFor(groups)
    }

    suspend fun hasData(): Boolean = dao.count() > 0

    /** Distinct course keys (code, or full name when uncoded), sorted for stable color assignment. */
    fun observeCourseKeys(): Flow<List<com.androtim.timetable.data.model.CourseKey>> =
        dao.observeAll().map { list ->
            list.map {
                com.androtim.timetable.data.model.CourseKey(
                    key = it.courseCode ?: it.courseName,
                    hasCode = it.courseCode != null,
                )
            }.distinct().sortedBy { it.key }
        }

    /**
     * All distinct group tokens found in the cached feed with their event counts,
     * most-used first — the count is the best available signal for which tags
     * are real schedule groups vs. incidental ones ("Prof", other years).
     */
    fun observeGroupTokens(): Flow<List<com.androtim.timetable.data.model.GroupToken>> =
        dao.observeAll().map { list ->
            list.flatMap { entity -> entity.groupTokens.split('\n').filter { it.isNotBlank() } }
                .groupingBy { it }
                .eachCount()
                .map { (token, count) -> com.androtim.timetable.data.model.GroupToken(token, count) }
                .sortedWith(compareByDescending<com.androtim.timetable.data.model.GroupToken> { it.count }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.token })
        }

    /** First/last event instants of the cached feed, or null when the cache is empty. */
    fun observeDateBounds(): Flow<Pair<LocalDate, LocalDate>?> =
        dao.observeAll().map { list ->
            if (list.isEmpty()) null
            else {
                val min = list.minOf { it.startMillis }
                val max = list.maxOf { it.startMillis }
                java.time.Instant.ofEpochMilli(min).atZone(DISPLAY_ZONE).toLocalDate() to
                    java.time.Instant.ofEpochMilli(max).atZone(DISPLAY_ZONE).toLocalDate()
            }
        }

    fun observeNotes(): Flow<Map<String, String>> =
        noteDao.observeAll().map { list -> list.associate { it.uid to it.note } }

    suspend fun getNotesMap(): Map<String, String> =
        noteDao.getAll().associate { it.uid to it.note }

    suspend fun setNote(uid: String, note: String) {
        if (note.isBlank()) noteDao.delete(uid)
        else noteDao.upsert(NoteEntity(uid, note.trim()))
    }

    fun observeDayNotes(): Flow<Map<Long, String>> =
        dayNoteDao.observeAll().map { list -> list.associate { it.epochDay to it.note } }

    suspend fun getDayNotesMap(): Map<Long, String> =
        dayNoteDao.getAll().associate { it.epochDay to it.note }

    suspend fun setDayNote(epochDay: Long, note: String) {
        if (note.isBlank()) dayNoteDao.delete(epochDay)
        else dayNoteDao.upsert(DayNoteEntity(epochDay, note.trim()))
    }

    /**
     * Downloads the configured .ics feed, parses it and atomically replaces the
     * local cache. No-op returning 0 when no feed URL has been configured yet.
     * @throws IOException on network or HTTP failure — callers decide how to surface it.
     */
    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val url = settings.feedUrl ?: return@withContext 0
        val request = Request.Builder().url(url).build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty response body")
        }
        val events = IcsParser.parse(body)
        if (events.isEmpty()) throw IOException("Feed parsed to 0 events — refusing to wipe cache")
        dao.replaceAll(events.map(EventEntity::fromDomain))
        settings.lastSyncMillis = System.currentTimeMillis()
        events.size
    }

    private fun List<EventEntity>.toDomainFor(groups: Set<String>): List<ScheduleEvent> =
        map { it.toDomain() }.filter { GroupFilter.matches(groups, it.groupTokens) }

    private fun LocalDate.dayBoundsMillis(): Pair<Long, Long> {
        val start = atStartOfDay(DISPLAY_ZONE).toInstant().toEpochMilli()
        val end = plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant().toEpochMilli()
        return start to end
    }

    companion object {
        @Volatile
        private var instance: ScheduleRepository? = null

        fun get(context: Context): ScheduleRepository =
            instance ?: synchronized(this) {
                instance ?: ScheduleRepository(context.applicationContext).also { instance = it }
            }
    }
}
