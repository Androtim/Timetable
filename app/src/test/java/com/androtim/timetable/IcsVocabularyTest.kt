package com.androtim.timetable

import com.androtim.timetable.data.ics.IcsParser
import com.androtim.timetable.data.model.CourseType
import com.androtim.timetable.data.model.ScheduleEvent
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session-type detection across the scheduling conventions students actually
 * meet. The bucket drives the colour; the badge shows the feed's own word.
 *
 * Anything genuinely unrecognised must stay [CourseType.OTHER] rather than be
 * forced into a bucket: no badge is a better answer than a wrong one.
 */
class IcsVocabularyTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    private fun parseSummary(summary: String): ScheduleEvent {
        val ics = listOf(
            "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VEVENT", "UID:x",
            "SUMMARY:$summary",
            "DTSTART:20261119T123000Z", "DTEND:20261119T143000Z",
            "END:VEVENT", "END:VCALENDAR"
        ).joinToString("\r\n")
        return IcsParser.parse(ics, zone).single()
    }

    private fun assertType(summary: String, expected: CourseType, badge: String) {
        val e = parseSummary(summary)
        assertEquals("bucket for '$summary'", expected, e.type)
        assertEquals("badge for '$summary'", badge, e.typeLabel)
    }

    @Test
    fun `French, including the numbered forms plain word boundaries missed`() {
        assertType("R3.01 Dev Web CM", CourseType.CM, "CM")
        assertType("R3.01 Dev Web TD G1", CourseType.TD, "TD")
        assertType("R3.01 Dev Web TP GA2-1", CourseType.TP, "TP")
        assertType("R3.01 Dev Web TD1", CourseType.TD, "TD1")
        assertType("R3.01 Dev Web CM2", CourseType.CM, "CM2")
        assertType("R3.01 Dev Web 1CM", CourseType.CM, "1CM")
        assertType("R3.01 Dev Web TP-A", CourseType.TP, "TP")
    }

    @Test
    fun `English`() {
        assertType("CS201 Algorithms Lecture", CourseType.CM, "LECTURE")
        assertType("CS201 Algorithms Seminar", CourseType.TD, "SEMINAR")
        assertType("CS201 Algorithms Tutorial", CourseType.TD, "TUTORIAL")
        assertType("CS201 Algorithms Lab", CourseType.TP, "LAB")
        assertType("CS201 Algorithms Practical", CourseType.TP, "PRACTICAL")
        assertType("CS201 Algorithms Workshop", CourseType.TP, "WORKSHOP")
        assertType("CS201 Recitation", CourseType.TD, "RECITATION")
    }

    @Test
    fun `German, including the accented form the old boundary could never match`() {
        assertType("INF101 Programmierung Vorlesung", CourseType.CM, "VORLESUNG")
        assertType("INF101 Programmierung Übung", CourseType.TD, "ÜBUNG")
        assertType("INF101 Programmierung Ubung", CourseType.TD, "UBUNG")
        assertType("INF101 Programmierung Praktikum", CourseType.TP, "PRAKTIKUM")
        assertType("INF101 Programmierung Tutorium", CourseType.TD, "TUTORIUM")
    }

    @Test
    fun `Spanish and Portuguese`() {
        assertType("BD220 Bases de datos Clase", CourseType.CM, "CLASE")
        assertType("BD220 Bases de datos Prácticas", CourseType.TP, "PRÁCTICAS")
        assertType("BD220 Bases de datos Seminario", CourseType.TD, "SEMINARIO")
        assertType("ALG100 Algoritmos Teórica", CourseType.CM, "TEÓRICA")
    }

    @Test
    fun `Dutch, Italian and Polish`() {
        assertType("WIS101 Wiskunde Hoorcollege", CourseType.CM, "HOORCOLLEGE")
        assertType("WIS101 Wiskunde Werkcollege", CourseType.TD, "WERKCOLLEGE")
        assertType("ANA200 Analisi Lezione", CourseType.CM, "LEZIONE")
        assertType("ANA200 Analisi Esercitazione", CourseType.TD, "ESERCITAZIONE")
        assertType("ANA200 Analisi Laboratorio", CourseType.TP, "LABORATORIO")
        assertType("MAT300 Matematyka Wykład", CourseType.CM, "WYKŁAD")
        assertType("MAT300 Matematyka Ćwiczenia", CourseType.TD, "ĆWICZENIA")
    }

    @Test
    fun `a type word glued inside another word is not a match`() {
        // "STD" must not read as TD, "Collaboration" must not read as Lab.
        assertEquals(CourseType.OTHER, parseSummary("CS201 STDIO Basics").type)
        assertEquals(CourseType.OTHER, parseSummary("CS201 Collaboration Skills").type)
        assertEquals(CourseType.OTHER, parseSummary("CS201 Something").type)
    }

    @Test
    fun `session type is also read from CATEGORIES when SUMMARY has none`() {
        val ics = listOf(
            "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VEVENT", "UID:y",
            "SUMMARY:Algorithms and Data Structures",
            "CATEGORIES:Lecture",
            "DTSTART:20261119T123000Z", "DTEND:20261119T143000Z",
            "END:VEVENT", "END:VCALENDAR"
        ).joinToString("\r\n")
        val e = IcsParser.parse(ics, zone).single()
        assertEquals(CourseType.CM, e.type)
        assertEquals("LECTURE", e.typeLabel)
        // SUMMARY still supplies the course name.
        assertEquals("Algorithms and Data Structures", e.courseName)
    }

    @Test
    fun `exams are detected beyond French`() {
        assertTrue(parseSummary("DB220 Databases Exam").isExam)
        assertTrue(parseSummary("DB220 Bases de donnees Examen").isExam)
        assertTrue(parseSummary("INF101 Klausur").isExam)
        assertTrue(parseSummary("INF101 Prüfung").isExam)
        assertTrue(parseSummary("WIS101 Tentamen").isExam)
        assertTrue(parseSummary("ANA200 Esame").isExam)
        assertTrue(parseSummary("MAT300 Egzamin").isExam)
        assertTrue(parseSummary("CS201 Midterm").isExam)
    }

    @Test
    fun `exam words inside longer words do not flag an exam`() {
        assertFalse(parseSummary("CS201 Software Testing Lab").isExam)
        assertFalse(parseSummary("CS201 Examples and Exercises").isExam)
    }

    @Test
    fun `the course name stops at the session-type word in any language`() {
        assertEquals("Programmierung", parseSummary("INF101 Programmierung Vorlesung").courseName)
        assertEquals("Wiskunde", parseSummary("WIS101 Wiskunde Hoorcollege").courseName)
        assertEquals("Algorithms", parseSummary("CS201 Algorithms Lecture").courseName)
    }
}
