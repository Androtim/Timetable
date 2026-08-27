package com.androtim.timetable.data.ics

import com.androtim.timetable.data.model.CourseType
import com.androtim.timetable.data.model.DISPLAY_ZONE
import com.androtim.timetable.data.model.ScheduleEvent
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Parser for iCalendar (RFC 5545) timetable feeds.
 *
 * The container is standard; what schools put inside it is not. The shape this
 * parser reads best is ADE/Adesoft, common in French universities:
 *   SUMMARY:R3.04 Qualite de developpement TP GA2-1
 *   LOCATION:TP I-104
 *   DESCRIPTION:(blank)(blank)GA2-1(nl)DE SOLMINIHAC Pierre Alexis(nl)(trailer)
 *   DTSTART:20261119T123000Z
 *   UID:ADE6050...
 * DESCRIPTION carries group tokens first, then teacher names, then a trailer;
 * teacher lines are whatever is not a group token. Feeds that do not follow
 * that convention still parse, they simply yield no course code or groups.
 *
 * The standard parts are handled generally: DTSTART is read as UTC, as a TZID
 * zone, as a floating local time, or as a date-only all-day value, and RRULE
 * series are expanded, honouring EXDATE and RECURRENCE-ID overrides.
 */
object IcsParser {

    /** Safety rails so an open-ended RRULE cannot generate unbounded events. */
    private const val MAX_OCCURRENCES = 500
    private const val MAX_YEARS = 2L

    // NOTE: no (?U)/(?u) inline flags and no \b next to accented letters --
    // Android's ICU regex engine rejects (?U) (class-load crash) and the JVM
    // treats accented chars as non-word without it, so \bUbung\b never matches.
    // Explicit \p{L} boundaries and literal accent classes behave identically
    // on both platforms. IGNORE_CASE only folds ASCII here, which is why each
    // accented letter is written as a class covering its unaccented spelling.

    /** Practical / lab sessions. */
    private const val TP_WORDS =
        """TP|Travaux pratiques|Laboratoire|Laboratorium|Laboratorio|Labo|Lab""" +
        """|Practicum|Practical|Praktikum|Pr[áa]cticas|Pr[áa]ctica|Pr[áa]tica""" +
        """|Workshop|Atelier"""

    /** Tutorial / small-group sessions. */
    private const val TD_WORDS =
        """TD|Travaux dirig[ée]s|Tutorial|Tutorium|Tutor[íi]a|S[ée]minaire""" +
        """|Seminarium|Seminario|Seminar|[ÜU]bung|[ĆC]wiczenia|Esercitazione""" +
        """|Werkcollege|Recitation"""

    /** Lectures. */
    private const val CM_WORDS =
        """CM|Cours magistral|Vorlesung|Hoorcollege|Lezione|Wyk[łl]ad""" +
        """|Te[óo]rica|Magistral|Lecture|Cours|Clase"""

    /** Words that mark an assessment rather than a normal session. */
    private const val EXAM_WORDS =
        """examen|exam|ds|devoir surveill[ée]|contr[ôo]le|partiel|[ée]valuation""" +
        """|qcm|klausur|pr[üu]fung|tentamen|esame|egzamin|prova|midterm"""

    // A token may carry a short digit run ("TD1", "CM2", "1CM") and must not be
    // glued to surrounding letters.
    private fun typeRegex(words: String) = Regex(
        """(?:^|[^\p{L}])(\p{N}{0,2}(?:""" + words + """)\p{N}{0,2})(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Session-type vocabulary across common scheduling conventions, mapped to
     * three semantic buckets (used for coloring). The badge shown to the user
     * is the word the feed actually used, so any school's terminology fits;
     * anything unrecognised stays [CourseType.OTHER] and shows no badge, which
     * is a better failure than asserting a type the school does not use.
     */
    private val TYPE_PATTERNS = listOf(
        CourseType.TP to typeRegex(TP_WORDS),
        CourseType.TD to typeRegex(TD_WORDS),
        CourseType.CM to typeRegex(CM_WORDS),
    )

    private val EXAM_REGEX = Regex(
        """(?i)(?:^|[^\p{L}])(?:""" + EXAM_WORDS + """)(?=[^\p{L}]|$)"""
    )

    /** Tokens that terminate the course-name portion of a SUMMARY. */
    private val NAME_TERMINATOR_REGEX = Regex(
        """(?:^|\s)\p{N}{0,2}(?:""" + TP_WORDS + "|" + TD_WORDS + "|" + CM_WORDS +
        """|Examen|Exam|Soutenance|ORE|DS|Contr[ôo]le|Partiel|Klausur|Pr[üu]fung""" +
        """|Tentamen|Esame|Egzamin)\p{N}{0,2}(?=[\s\-]|$)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(ics: String): List<ScheduleEvent> = parse(ics, DISPLAY_ZONE)

    /**
     * [fallbackZone] resolves floating times and unrecognised TZIDs, which
     * RFC 5545 defines as local to the observer.
     */
    fun parse(ics: String, fallbackZone: ZoneId): List<ScheduleEvent> {
        val unfolded = ics
            .replace("\r\n", "\n")
            .replace("\n ", "")
            .replace("\n\t", "")

        val raw = unfolded
            .split("BEGIN:VEVENT")
            .drop(1)
            .mapNotNull { block -> parseRaw(block.substringBefore("END:VEVENT"), fallbackZone) }

        // A VEVENT carrying RECURRENCE-ID replaces one instance of the series
        // with the same UID, so that instance must not also be generated.
        val replaced = raw.mapNotNull { r -> r.recurrenceId?.let { r.uid to it } }.toSet()

        val out = ArrayList<ScheduleEvent>()
        for (r in raw) {
            if (r.rrule == null) {
                out += r.toEvent(r.start, r.uid)
                continue
            }
            for (at in occurrences(r)) {
                val instant = at.toInstant()
                if (instant in r.exDates) continue
                if ((r.uid to instant) in replaced) continue
                // Occurrence UIDs must be unique (uid is the primary key) and
                // stable across syncs, so per-event notes survive a refresh.
                out += r.toEvent(at, r.uid + "#" + instant.epochSecond)
            }
        }
        return out
    }

    private fun parseRaw(block: String, fallbackZone: ZoneId): RawEvent? {
        var uid: String? = null
        var summary = ""
        var location = ""
        var description = ""
        var categories = ""
        var startProp: Prop? = null
        var endProp: Prop? = null
        var durationValue: String? = null
        var rrule: String? = null
        var recurrenceProp: Prop? = null
        val exDateProps = ArrayList<Prop>()

        for (line in block.split('\n')) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val head = line.substring(0, idx)
            val params = head.substringAfter(';', "")
            val value = line.substring(idx + 1)
            when (head.substringBefore(';').trim().uppercase()) {
                "UID" -> uid = value.trim()
                "SUMMARY" -> summary = unescape(value).trim()
                "LOCATION" -> location = unescape(value).trim()
                "DESCRIPTION" -> description = unescape(value)
                "CATEGORIES" -> categories = unescape(value).trim()
                "DTSTART" -> startProp = Prop(params, value)
                "DTEND" -> endProp = Prop(params, value)
                "DURATION" -> durationValue = value.trim()
                "RRULE" -> rrule = value.trim()
                "RECURRENCE-ID" -> recurrenceProp = Prop(params, value)
                "EXDATE" -> exDateProps += Prop(params, value)
            }
        }

        val id = uid ?: return null
        val start = parseIcsTime(startProp ?: return null, fallbackZone) ?: return null
        val end = endProp?.let { parseIcsTime(it, fallbackZone) }

        val span = when {
            end != null -> Duration.between(start.at, end.at)
            durationValue != null -> runCatching { Duration.parse(durationValue) }.getOrNull()
            else -> null
        }
        val fallbackSpan = if (start.isDate) Duration.ofDays(1) else Duration.ofHours(1)
        val duration = if (span == null || span.isNegative || span.isZero) fallbackSpan else span

        // EXDATE may repeat, and each line may carry several comma-separated values.
        val exDates = exDateProps.flatMap { prop ->
            prop.value.split(',').mapNotNull { v ->
                parseIcsTime(Prop(prop.params, v), fallbackZone)?.at?.toInstant()
            }
        }.toSet()

        return RawEvent(
            uid = id,
            summary = summary,
            location = location,
            description = description,
            categories = categories,
            start = start.at,
            duration = duration,
            isAllDay = start.isDate,
            rrule = rrule,
            exDates = exDates,
            recurrenceId = recurrenceProp?.let { parseIcsTime(it, fallbackZone)?.at?.toInstant() },
        )
    }

    private fun RawEvent.toEvent(at: ZonedDateTime, newUid: String): ScheduleEvent {
        val (groupTokens, teachers) = parseDescription(description)
        val (code, name) = parseCodeAndName(summary)
        // Some systems put the session type in CATEGORIES rather than SUMMARY.
        val (type, typeLabel) = parseType(summary)
            .takeIf { it.first != CourseType.OTHER }
            ?: parseType(categories)
        val startInstant = at.toInstant()
        return ScheduleEvent(
            uid = newUid,
            rawSummary = summary,
            courseCode = code,
            courseName = name,
            type = type,
            typeLabel = typeLabel,
            isExam = EXAM_REGEX.containsMatchIn(summary),
            location = location,
            teachers = teachers,
            groupTokens = groupTokens,
            start = startInstant,
            end = startInstant.plus(duration),
            isAllDay = isAllDay,
        )
    }

    /**
     * Reads a DTSTART/DTEND-style value in any of the shapes RFC 5545 allows:
     * UTC ("...Z"), a named TZID zone, a floating local time, or a date-only
     * value meaning an all-day event.
     */
    private fun parseIcsTime(prop: Prop, fallbackZone: ZoneId): IcsTime? {
        val v = prop.value.trim()
        if (v.isEmpty()) return null
        if (!v.contains('T')) {
            val date = runCatching { LocalDate.parse(v, ICS_DATE) }.getOrNull() ?: return null
            return IcsTime(date.atStartOfDay(zoneOf(prop.params, fallbackZone)), isDate = true)
        }
        val isUtc = v.endsWith("Z")
        val local = runCatching { LocalDateTime.parse(v.removeSuffix("Z"), ICS_DATE_TIME) }
            .getOrNull() ?: return null
        val zone = if (isUtc) ZoneOffset.UTC else zoneOf(prop.params, fallbackZone)
        return IcsTime(local.atZone(zone), isDate = false)
    }

    /**
     * Expands an RRULE into concrete start times. Recurrence is stepped in local
     * wall-clock terms so a weekly 08:15 class stays at 08:15 across a DST shift.
     */
    private fun occurrences(r: RawEvent): List<ZonedDateTime> {
        val rule = RRule(r.rrule ?: return listOf(r.start))
        val zone = r.start.zone
        val first = r.start.toLocalDateTime()
        val time: LocalTime = first.toLocalTime()
        val horizon = first.plusYears(MAX_YEARS)
        val out = ArrayList<ZonedDateTime>()

        // Returns false once generation should stop entirely.
        fun add(dt: LocalDateTime): Boolean {
            if (dt.isBefore(first)) return true
            if (dt.isAfter(horizon)) return false
            rule.until?.let { if (dt.atZone(zone).toInstant().isAfter(it)) return false }
            out += dt.atZone(zone)
            if (rule.count != null && out.size >= rule.count) return false
            return out.size < MAX_OCCURRENCES
        }

        when (rule.freq) {
            "WEEKLY" -> {
                val days = rule.byDay.ifEmpty { listOf(first.dayOfWeek) }.sortedBy { it.value }
                var week = first.toLocalDate().with(TemporalAdjusters.previousOrSame(rule.weekStart))
                var guard = 0L
                weeks@ while (guard++ < MAX_YEARS * 53 + 2) {
                    for (d in days) {
                        val date = week.with(TemporalAdjusters.nextOrSame(d))
                        if (!add(date.atTime(time))) break@weeks
                    }
                    week = week.plusWeeks(rule.interval)
                }
            }
            "DAILY" -> {
                var i = 0L
                while (add(first.plusDays(i * rule.interval))) i++
            }
            "MONTHLY" -> {
                val wanted = rule.byMonthDay.ifEmpty { listOf(first.dayOfMonth) }.sorted()
                var i = 0L
                months@ while (i < (MAX_YEARS + 1) * 12) {
                    val month = first.toLocalDate().withDayOfMonth(1).plusMonths(i * rule.interval)
                    for (dom in wanted) {
                        val len = month.lengthOfMonth()
                        val day = if (dom > 0) dom else len + 1 + dom
                        if (day < 1 || day > len) continue // RFC 5545: invalid dates are skipped
                        if (!add(month.withDayOfMonth(day).atTime(time))) break@months
                    }
                    i++
                }
            }
            "YEARLY" -> {
                var i = 0L
                while (add(first.plusYears(i * rule.interval))) i++
            }
        }
        // An unsupported or malformed FREQ must not make the event disappear.
        return out.ifEmpty { listOf(r.start) }
    }

    /**
     * ADE teacher lines look like "VERDIER Philippine" or
     * "DE SOLMINIHAC Pierre Alexis": one or more ALL-CAPS surname words
     * followed by a capitalized given name. Everything else is a group token —
     * this keeps the parser feed-agnostic instead of hardcoding group names.
     */
    private val TEACHER_REGEX =
        Regex("""^[\p{Lu}][\p{Lu}'\- ]+\s\p{Lu}\p{Ll}[\p{L}\-]*(\s\p{L}[\p{L}\-]*)*$""")

    private fun parseDescription(description: String): Pair<List<String>, List<String>> {
        val groups = mutableListOf<String>()
        val teachers = mutableListOf<String>()
        for (rawLine in description.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("(Modifié") || line.startsWith("(Exporté")) continue
            if (TEACHER_REGEX.matches(line)) teachers += line else groups += line
        }
        return groups to teachers
    }

    /** "R3.04 Qualité de développement TP GA2-1" -> ("R3.04", "Qualité de développement") */
    private fun parseCodeAndName(summary: String): Pair<String?, String> {
        val firstSpace = summary.indexOf(' ')
        val firstToken = if (firstSpace > 0) summary.substring(0, firstSpace) else summary
        val hasCode = firstSpace > 0 && firstToken.any(Char::isDigit)
        val code = if (hasCode) firstToken else null
        val rest = if (hasCode) summary.substring(firstSpace + 1).trim() else summary

        val terminator = NAME_TERMINATOR_REGEX.find(rest)
        val name = (terminator?.let { rest.substring(0, it.range.first) } ?: rest).trim()
        return code to name.ifEmpty { rest }
    }

    private fun parseType(text: String): Pair<CourseType, String?> {
        for ((type, regex) in TYPE_PATTERNS) {
            // group 1 excludes the boundary character the pattern had to consume
            regex.find(text)?.let { return type to it.groupValues[1].uppercase() }
        }
        return CourseType.OTHER to null
    }

    private fun unescape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}

private val ICS_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
private val ICS_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

/** One content line: its parameters (after ';') and its value (after ':'). */
private data class Prop(val params: String, val value: String)

/** A resolved DTSTART/DTEND, and whether it was date-only (all-day). */
private data class IcsTime(val at: ZonedDateTime, val isDate: Boolean)

private data class RawEvent(
    val uid: String,
    val summary: String,
    val location: String,
    val description: String,
    val categories: String,
    val start: ZonedDateTime,
    val duration: Duration,
    val isAllDay: Boolean,
    val rrule: String?,
    val exDates: Set<Instant>,
    val recurrenceId: Instant?,
)

/**
 * Outlook and Exchange export Windows zone names rather than IANA ones. Only
 * the zones a student body is likely to hit are mapped; anything unrecognised
 * falls back to the device zone.
 */
private val WINDOWS_ZONES = mapOf(
    "W. Europe Standard Time" to "Europe/Berlin",
    "Romance Standard Time" to "Europe/Paris",
    "Central Europe Standard Time" to "Europe/Budapest",
    "Central European Standard Time" to "Europe/Warsaw",
    "GMT Standard Time" to "Europe/London",
    "Greenwich Standard Time" to "Atlantic/Reykjavik",
    "E. Europe Standard Time" to "Europe/Chisinau",
    "FLE Standard Time" to "Europe/Helsinki",
    "Turkey Standard Time" to "Europe/Istanbul",
    "Eastern Standard Time" to "America/New_York",
    "Central Standard Time" to "America/Chicago",
    "Mountain Standard Time" to "America/Denver",
    "Pacific Standard Time" to "America/Los_Angeles",
    "Atlantic Standard Time" to "America/Halifax",
    "India Standard Time" to "Asia/Kolkata",
    "China Standard Time" to "Asia/Shanghai",
    "Tokyo Standard Time" to "Asia/Tokyo",
    "AUS Eastern Standard Time" to "Australia/Sydney",
)

/** TZID=Europe/Berlin resolves to that zone; absent or unknown to [fallbackZone]. */
private fun zoneOf(params: String, fallbackZone: ZoneId): ZoneId {
    val tzid = params.split(';')
        .firstOrNull { it.trim().startsWith("TZID=", ignoreCase = true) }
        ?.substringAfter('=')?.trim()?.trim('"')
        ?: return fallbackZone
    return runCatching { ZoneId.of(tzid) }.getOrNull()
        ?: WINDOWS_ZONES[tzid]?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        ?: fallbackZone
}

/** "MO", "2MO" and "-1FR" all name a weekday; the ordinal prefix is not applied. */
private fun dayOfWeek(token: String): DayOfWeek? = when (token.trim().uppercase().takeLast(2)) {
    "MO" -> DayOfWeek.MONDAY
    "TU" -> DayOfWeek.TUESDAY
    "WE" -> DayOfWeek.WEDNESDAY
    "TH" -> DayOfWeek.THURSDAY
    "FR" -> DayOfWeek.FRIDAY
    "SA" -> DayOfWeek.SATURDAY
    "SU" -> DayOfWeek.SUNDAY
    else -> null
}

/** UNTIL is a UTC date-time, or a date meaning "through the end of that day". */
private fun parseUntil(value: String): Instant? {
    val v = value.trim()
    return if (v.contains('T')) {
        runCatching {
            LocalDateTime.parse(v.removeSuffix("Z"), ICS_DATE_TIME).toInstant(ZoneOffset.UTC)
        }.getOrNull()
    } else {
        runCatching {
            LocalDate.parse(v, ICS_DATE).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        }.getOrNull()
    }
}

/**
 * The subset of RRULE that timetable feeds actually use. Ordinal BYDAY
 * ("2MO" = second Monday) is read as a plain weekday, and BYSETPOS/BYWEEKNO
 * are not implemented; both are vanishingly rare in class schedules.
 */
private class RRule(spec: String) {
    private val parts: Map<String, String> = spec.split(';').mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else it.substring(0, i).trim().uppercase() to it.substring(i + 1).trim()
    }.toMap()

    val freq: String? = parts["FREQ"]?.uppercase()
    val interval: Long = parts["INTERVAL"]?.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
    val count: Int? = parts["COUNT"]?.toIntOrNull()
    val until: Instant? = parts["UNTIL"]?.let { parseUntil(it) }
    val weekStart: DayOfWeek = parts["WKST"]?.let { dayOfWeek(it) } ?: DayOfWeek.MONDAY
    val byDay: List<DayOfWeek> = parts["BYDAY"]?.split(',')?.mapNotNull { dayOfWeek(it) } ?: emptyList()
    val byMonthDay: List<Int> =
        parts["BYMONTHDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
}
