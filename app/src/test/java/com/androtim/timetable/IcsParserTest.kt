package com.androtim.timetable

import com.androtim.timetable.data.ics.IcsParser
import com.androtim.timetable.data.model.CourseType
import com.androtim.timetable.data.model.GroupFilter
import com.androtim.timetable.data.model.PARIS_ZONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class IcsParserTest {

    private val events by lazy {
        val ics = javaClass.getResourceAsStream("/amu_feed_sample.ics")!!
            .readBytes().toString(Charsets.UTF_8)
        IcsParser.parse(ics)
    }

    /** The token set a "Class A2 – Group 2" student would select during setup. */
    private val a2g2 = setOf("GA2-2", "Groupe A2 an2", "2ème année")
    private val a1g1 = setOf("GA1-1", "Groupe A1 an2", "2ème année")

    @Test
    fun `parses all events from real feed`() {
        assertEquals(443, events.size)
    }

    @Test
    fun `parses a TP event with half-group and teacher`() {
        val e = events.first { it.rawSummary == "R3.12 Anglais TP GA2-2" && it.teachers.contains("VERDIER Philippine") }
        assertEquals("R3.12", e.courseCode)
        assertEquals("Anglais", e.courseName)
        assertEquals(CourseType.TP, e.type)
        assertFalse(e.isExam)
        assertTrue(e.groupTokens.contains("GA2-2"))
        assertTrue(GroupFilter.matches(setOf("GA2-2"), e.groupTokens))
        assertFalse(GroupFilter.matches(setOf("GA2-1"), e.groupTokens))
        assertFalse(GroupFilter.matches(setOf("GB-1", "Groupe B an2"), e.groupTokens))
    }

    @Test
    fun `teacher lines are separated from group tokens without a hardcoded list`() {
        // "DE SOLMINIHAC Pierre Alexis" spans a folded line in the raw feed
        val e = events.first { it.teachers.any { t -> t.startsWith("DE SOLMINIHAC") } }
        assertTrue(e.teachers.contains("DE SOLMINIHAC Pierre Alexis"))
        // group-like lines must never be classified as teachers
        events.forEach { ev ->
            ev.teachers.forEach { teacher ->
                assertFalse("'$teacher' looks like a group token", teacher.startsWith("Groupe "))
                assertFalse("'$teacher' looks like a group token", teacher.matches(Regex("""^G[AB]\d?-\d.*""")))
                assertFalse("'$teacher' looks like a year token", teacher.contains("année", ignoreCase = true))
            }
        }
    }

    @Test
    fun `exam events are flagged and typed correctly`() {
        val exams = events.filter { it.isExam }
        assertTrue(exams.isNotEmpty())
        val progSys = exams.first { it.rawSummary == "R3.05 Programmation système Examen" }
        assertEquals("R3.05", progSys.courseCode)
        assertEquals("Programmation système", progSys.courseName)
        assertEquals(CourseType.OTHER, progSys.type)
        // regular lectures must not be flagged
        assertFalse(events.first { it.rawSummary == "R3.03 Analyse CM (INFO)" }.isExam)
    }

    @Test
    fun `class-level TD matches students of both half groups of that class`() {
        val e = events.first { it.rawSummary == "R3.11 Droit des contrats et du numérique TD GA2" }
        assertTrue(e.groupTokens.contains("Groupe A2 an2"))
        assertTrue(GroupFilter.matches(a2g2, e.groupTokens))
        assertTrue(GroupFilter.matches(setOf("GA2-1", "Groupe A2 an2", "2ème année"), e.groupTokens))
        assertFalse(GroupFilter.matches(a1g1, e.groupTokens))
    }

    @Test
    fun `promo-wide events match every group selection`() {
        val e = events.first { it.rawSummary.startsWith("Ferié 11") }
        assertTrue(GroupFilter.matches(a1g1, e.groupTokens))
        assertTrue(GroupFilter.matches(a2g2, e.groupTokens))
        assertTrue(GroupFilter.matches(setOf("GB-2", "Groupe B an2", "2ème année"), e.groupTokens))
    }

    @Test
    fun `empty selection and token-less events always match`() {
        val e = events.first { it.rawSummary == "R3.12 Anglais TP GA2-2" }
        assertTrue(GroupFilter.matches(emptySet(), e.groupTokens))
        assertTrue(GroupFilter.matches(setOf("GB-1"), emptyList()))
    }

    @Test
    fun `multi-group list events match each listed half group`() {
        val e = events.first { it.groupTokens.containsAll(listOf("GA1-1", "GB-2")) }
        assertTrue(GroupFilter.matches(setOf("GA1-1"), e.groupTokens))
        assertTrue(GroupFilter.matches(setOf("GB-2"), e.groupTokens))
        assertTrue(e.teachers.contains("CASALI Alain"))
    }

    @Test
    fun `UTC timestamps convert to expected Paris local time`() {
        // DTSTART:20261124T143000Z is 15:30 in Paris (UTC+1 in winter)
        val e = events.first { it.start == java.time.Instant.parse("2026-11-24T14:30:00Z") && it.rawSummary.startsWith("R3.12") }
        val local = e.start.atZone(PARIS_ZONE).toLocalTime()
        assertEquals(LocalTime.of(15, 30), local)
    }

    @Test
    fun `events without course code keep full name`() {
        val e = events.first { it.rawSummary == "Conseil de département" }
        assertEquals(null, e.courseCode)
        assertEquals("Conseil de département", e.courseName)
    }

    @Test
    fun `every event has uid location field and valid times`() {
        events.forEach { e ->
            assertNotNull(e.uid)
            assertTrue(e.uid.isNotBlank())
            assertTrue(e.end.isAfter(e.start))
        }
        assertEquals("distinct uids", events.size, events.map { it.uid }.distinct().size)
    }
}
