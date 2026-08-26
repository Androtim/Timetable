package com.androtim.timetable.data.model

import java.time.Instant
import java.time.ZoneId

val PARIS_ZONE: ZoneId = ZoneId.of("Europe/Paris")

/**
 * Group filtering over the free-form group tokens found in each VEVENT's
 * DESCRIPTION. Groups are whatever the feed publishes (e.g. "GA1-1",
 * "Groupe A1 an2", "2ème année") — the user selects the set of tokens that
 * apply to them, and an event is shown when any of its tokens is selected.
 * Events without group tokens (holidays, all-hands) are shown to everyone,
 * and an empty selection means "show everything".
 */
object GroupFilter {
    fun matches(selected: Set<String>, eventTokens: List<String>): Boolean {
        if (selected.isEmpty() || eventTokens.isEmpty()) return true
        return eventTokens.any { token ->
            selected.any { it.equals(token, ignoreCase = true) }
        }
    }
}

enum class CourseType { CM, TD, TP, OTHER }

/** A group tag found in the feed, with how many events carry it. */
data class GroupToken(val token: String, val count: Int)

/** One parsed VEVENT from the ADE feed. */
data class ScheduleEvent(
    val uid: String,
    val rawSummary: String,
    val courseCode: String?,   // e.g. "R3.01"
    val courseName: String,    // e.g. "Développement web"
    /** Semantic bucket used for coloring; the feed's own word is in [typeLabel]. */
    val type: CourseType,
    /** The type word as written in the feed ("TP", "LAB", "LECTURE"…), null if none. */
    val typeLabel: String? = null,
    val isExam: Boolean,
    val location: String,
    val teachers: List<String>,
    val groupTokens: List<String>,
    val start: Instant,
    val end: Instant,
)
