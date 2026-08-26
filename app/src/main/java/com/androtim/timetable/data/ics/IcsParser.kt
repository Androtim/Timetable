package com.androtim.timetable.data.ics

import com.androtim.timetable.data.model.CourseType
import com.androtim.timetable.data.model.ScheduleEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Parser for the AMU ADE iCal export.
 *
 * Observed VEVENT shape (after unfolding):
 *   SUMMARY:R3.04 Qualité de développement TP GA2-1
 *   LOCATION:TP I-104
 *   DESCRIPTION:\n\nGA2-1\nDE SOLMINIHAC Pierre Alexis\n\n(Modifié le:…)
 *   DTSTART:20261119T123000Z          (always UTC date-time)
 *   DTEND:20261119T163000Z
 *   UID:ADE6050…
 *
 * DESCRIPTION carries group tokens first, then teacher names, then a
 * "(Modifié le:…)" trailer. Teacher lines are whatever is not a known group token.
 */
object IcsParser {

    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    // NOTE: no (?U)/(?u) inline flags and no \b next to accented letters —
    // Android's ICU regex engine rejects (?U) (class-load crash) and the JVM
    // treats accented chars as non-word without it. Explicit boundaries via
    // \p{L} and literal accent classes behave identically on both platforms.
    /**
     * Session-type vocabulary across common scheduling conventions, mapped to
     * three semantic buckets (used for coloring). The badge shown to the user
     * is the word the feed actually used, so any school's terminology fits.
     */
    private val TYPE_PATTERNS = listOf(
        CourseType.TP to Regex("""\b(TP|Labo?|Practical|Workshop|Prácticas)\b""", RegexOption.IGNORE_CASE),
        CourseType.TD to Regex("""\b(TD|Tutorial|Seminar|Seminario|Übung)\b""", RegexOption.IGNORE_CASE),
        CourseType.CM to Regex("""\b(CM|Lecture|Cours|Clase)\b""", RegexOption.IGNORE_CASE),
    )
    private val EXAM_REGEX = Regex(
        """(?i)(?:^|[^\p{L}])(examen|exam|ds|devoir surveill[ée]|contr[ôo]le|partiel|[ée]valuation|qcm)(?=[^\p{L}]|$)"""
    )

    /** Tokens that terminate the course-name portion of a SUMMARY. */
    private val NAME_TERMINATOR_REGEX = Regex(
        """(?i)(?:^|\s)(CM|TD|TP|Labo?|Practical|Workshop|Prácticas|Tutorial|Seminar|Seminario|Lecture|Cours|Clase|Examen|Soutenance|ORE|DS|Contr[ôo]le|Partiel)(?=\s|$)"""
    )

    fun parse(ics: String): List<ScheduleEvent> {
        val unfolded = ics
            .replace("\r\n", "\n")
            .replace("\n ", "")
            .replace("\n\t", "")

        return unfolded
            .split("BEGIN:VEVENT")
            .drop(1)
            .mapNotNull { block -> parseEvent(block.substringBefore("END:VEVENT")) }
    }

    private fun parseEvent(block: String): ScheduleEvent? {
        val props = HashMap<String, String>()
        for (line in block.split('\n')) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val name = line.substring(0, idx).substringBefore(';').uppercase()
            props[name] = line.substring(idx + 1)
        }

        val uid = props["UID"]?.trim() ?: return null
        val summary = unescape(props["SUMMARY"] ?: "").trim()
        val location = unescape(props["LOCATION"] ?: "").trim()
        val description = unescape(props["DESCRIPTION"] ?: "")
        val start = parseUtc(props["DTSTART"]) ?: return null
        val end = parseUtc(props["DTEND"]) ?: return null

        val (groupTokens, teachers) = parseDescription(description)
        val (code, name) = parseCodeAndName(summary)
        val (type, typeLabel) = parseType(summary)

        return ScheduleEvent(
            uid = uid,
            rawSummary = summary,
            courseCode = code,
            courseName = name,
            type = type,
            typeLabel = typeLabel,
            isExam = EXAM_REGEX.containsMatchIn(summary),
            location = location,
            teachers = teachers,
            groupTokens = groupTokens,
            start = start,
            end = end,
        )
    }

    private fun parseUtc(value: String?): Instant? {
        val v = value?.trim()?.removeSuffix("Z") ?: return null
        return try {
            LocalDateTime.parse(v, UTC_FORMAT).toInstant(ZoneOffset.UTC)
        } catch (_: Exception) {
            null
        }
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

    private fun parseType(summary: String): Pair<CourseType, String?> {
        for ((type, regex) in TYPE_PATTERNS) {
            regex.find(summary)?.let { return type to it.value.uppercase() }
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
