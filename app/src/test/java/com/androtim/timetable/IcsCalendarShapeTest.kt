package com.androtim.timetable

import com.androtim.timetable.data.ics.IcsParser
import com.androtim.timetable.data.model.ScheduleEvent
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The iCalendar container is standard (RFC 5545) but schools use different
 * parts of it. These cover the shapes a non-ADE feed is likely to arrive in:
 * zoned and floating times, all-day values, and RRULE series.
 *
 * A fixed fallback zone is passed everywhere so results do not depend on the
 * machine running the tests.
 */
class IcsCalendarShapeTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val newYork: ZoneId = ZoneId.of("America/New_York")

    private fun feed(vararg lines: String): String =
        (listOf("BEGIN:VCALENDAR", "VERSION:2.0") + lines + "END:VCALENDAR").joinToString("\r\n")

    private fun parse(ics: String, zone: ZoneId = berlin): List<ScheduleEvent> =
        IcsParser.parse(ics, zone).sortedBy { it.start }

    private fun ScheduleEvent.startIn(zone: ZoneId): ZonedDateTime = start.atZone(zone)

    // ---------- absolute vs. zoned vs. floating ----------

    @Test
    fun `UTC datetime is absolute`() {
        val e = parse(
            feed(
                "BEGIN:VEVENT", "UID:a", "SUMMARY:CS201 Algo CM",
                "DTSTART:20261119T123000Z", "DTEND:20261119T143000Z", "END:VEVENT"
            )
        ).single()
        assertEquals(13, e.startIn(berlin).hour) // 12:30Z is 13:30 in CET
    }

    @Test
    fun `TZID is honoured rather than read as UTC`() {
        val e = parse(
            feed(
                "BEGIN:VEVENT", "UID:b", "SUMMARY:Info Vorlesung",
                "DTSTART;TZID=Europe/Berlin:20261119T123000",
                "DTEND;TZID=Europe/Berlin:20261119T143000", "END:VEVENT"
            )
        ).single()
        assertEquals(12, e.startIn(berlin).hour)
        assertEquals(30, e.startIn(berlin).minute)
    }

    @Test
    fun `a Windows zone name from Outlook still resolves`() {
        val e = parse(
            feed(
                "BEGIN:VEVENT", "UID:c", "SUMMARY:Seminar",
                "DTSTART;TZID=\"W. Europe Standard Time\":20261119T123000",
                "DTEND;TZID=\"W. Europe Standard Time\":20261119T143000", "END:VEVENT"
            )
        ).single()
        assertEquals(12, e.startIn(berlin).hour)
    }

    @Test
    fun `floating time is local to the reader`() {
        val ics = feed(
            "BEGIN:VEVENT", "UID:d", "SUMMARY:Course",
            "DTSTART:20261119T123000", "DTEND:20261119T143000", "END:VEVENT"
        )
        assertEquals(12, parse(ics, berlin).single().startIn(berlin).hour)
        assertEquals(12, parse(ics, newYork).single().startIn(newYork).hour)
    }

    @Test
    fun `an unknown TZID falls back rather than dropping the event`() {
        val e = parse(
            feed(
                "BEGIN:VEVENT", "UID:e", "SUMMARY:Course",
                "DTSTART;TZID=Mars/Olympus:20261119T123000",
                "DTEND;TZID=Mars/Olympus:20261119T143000", "END:VEVENT"
            )
        ).single()
        assertEquals(12, e.startIn(berlin).hour)
    }

    // ---------- all-day ----------

    @Test
    fun `all-day event is kept and flagged`() {
        val e = parse(
            feed(
                "BEGIN:VEVENT", "UID:f", "SUMMARY:Public holiday",
                "DTSTART;VALUE=DATE:20261119", "DTEND;VALUE=DATE:20261120", "END:VEVENT"
            )
        ).single()
        assertTrue(e.isAllDay)
        assertEquals(19, e.startIn(berlin).dayOfMonth)
        assertEquals(0, e.startIn(berlin).hour)
    }

    @Test
    fun `DURATION is used when DTEND is absent`() {
        val e = parse(
            feed(
                "BEGIN:VEVENT", "UID:g", "SUMMARY:Lab",
                "DTSTART:20261119T123000Z", "DURATION:PT1H30M", "END:VEVENT"
            )
        ).single()
        assertEquals(90 * 60, e.end.epochSecond - e.start.epochSecond)
    }

    // ---------- recurrence ----------

    @Test
    fun `weekly RRULE expands to COUNT occurrences with unique uids`() {
        val events = parse(
            feed(
                "BEGIN:VEVENT", "UID:h", "SUMMARY:CS201 Algo CM",
                "DTSTART:20261119T123000Z", "DTEND:20261119T143000Z",
                "RRULE:FREQ=WEEKLY;COUNT=12", "END:VEVENT"
            )
        )
        assertEquals(12, events.size)
        assertEquals(12, events.map { it.uid }.toSet().size)
        assertEquals(7, events[1].startIn(berlin).dayOfYear - events[0].startIn(berlin).dayOfYear)
    }

    @Test
    fun `BYDAY generates one occurrence per listed weekday`() {
        val events = parse(
            feed(
                "BEGIN:VEVENT", "UID:i", "SUMMARY:Lecture",
                "DTSTART:20261116T080000Z", "DTEND:20261116T100000Z", // a Monday
                "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=4", "END:VEVENT"
            )
        )
        assertEquals(4, events.size)
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            events.map { it.startIn(berlin).dayOfWeek }
        )
    }

    @Test
    fun `UNTIL bounds the series`() {
        val events = parse(
            feed(
                "BEGIN:VEVENT", "UID:j", "SUMMARY:Lecture",
                "DTSTART:20261119T123000Z", "DTEND:20261119T143000Z",
                "RRULE:FREQ=WEEKLY;UNTIL=20261211T235959Z", "END:VEVENT"
            )
        )
        assertEquals(4, events.size) // 19 + 26 Nov, 3 + 10 Dec
    }

    @Test
    fun `EXDATE removes a cancelled instance`() {
        val events = parse(
            feed(
                "BEGIN:VEVENT", "UID:k", "SUMMARY:Lecture",
                "DTSTART:20261119T123000Z", "DTEND:20261119T143000Z",
                "RRULE:FREQ=WEEKLY;COUNT=4",
                "EXDATE:20261126T123000Z", "END:VEVENT"
            )
        )
        assertEquals(3, events.size)
        assertTrue(events.none { it.startIn(berlin).dayOfMonth == 26 })
    }

    @Test
    fun `RECURRENCE-ID replaces one instance instead of duplicating it`() {
        val events = parse(
            feed(
                "BEGIN:VEVENT", "UID:l", "SUMMARY:Lecture",
                "DTSTART:20261119T123000Z", "DTEND:20261119T143000Z",
                "RRULE:FREQ=WEEKLY;COUNT=3", "END:VEVENT",
                "BEGIN:VEVENT", "UID:l", "SUMMARY:Lecture moved",
                "RECURRENCE-ID:20261126T123000Z",
                "DTSTART:20261127T123000Z", "DTEND:20261127T143000Z", "END:VEVENT"
            )
        )
        assertEquals(3, events.size)
        assertEquals(1, events.count { it.rawSummary == "Lecture moved" })
        assertTrue(events.none { it.rawSummary == "Lecture" && it.startIn(berlin).dayOfMonth == 26 })
    }

    @Test
    fun `weekly series keeps local wall time across a DST change`() {
        // 2027-03-28 is the European spring-forward; an 08:15 class stays 08:15.
        val events = parse(
            feed(
                "BEGIN:VEVENT", "UID:m", "SUMMARY:Lecture",
                "DTSTART;TZID=Europe/Berlin:20270322T081500",
                "DTEND;TZID=Europe/Berlin:20270322T101500",
                "RRULE:FREQ=WEEKLY;COUNT=2", "END:VEVENT"
            )
        )
        assertEquals(2, events.size)
        assertTrue(events.all { it.startIn(berlin).hour == 8 && it.startIn(berlin).minute == 15 })
        // ...which means the absolute gap is 167h, not 168h.
        assertNotEquals(7 * 24 * 3600L, events[1].start.epochSecond - events[0].start.epochSecond)
    }

    @Test
    fun `an unsupported FREQ still yields the first occurrence`() {
        val events = parse(
            feed(
                "BEGIN:VEVENT", "UID:n", "SUMMARY:Lecture",
                "DTSTART:20261119T123000Z", "DTEND:20261119T143000Z",
                "RRULE:FREQ=HOURLY;COUNT=5", "END:VEVENT"
            )
        )
        assertEquals(1, events.size)
    }

    @Test
    fun `an open-ended RRULE stays bounded`() {
        val events = parse(
            feed(
                "BEGIN:VEVENT", "UID:o", "SUMMARY:Lecture",
                "DTSTART:20261119T123000Z", "DTEND:20261119T143000Z",
                "RRULE:FREQ=DAILY", "END:VEVENT"
            )
        )
        assertTrue("expanded ${events.size} events", events.size in 2..500)
    }

    @Test
    fun `a non-recurring event keeps its feed uid so notes survive`() {
        val e = parse(
            feed(
                "BEGIN:VEVENT", "UID:ADE6050", "SUMMARY:CS201 Algo CM",
                "DTSTART:20261119T123000Z", "DTEND:20261119T143000Z", "END:VEVENT"
            )
        ).single()
        assertEquals("ADE6050", e.uid)
    }
}
