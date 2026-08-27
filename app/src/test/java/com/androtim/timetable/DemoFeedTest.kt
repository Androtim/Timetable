package com.androtim.timetable

import com.androtim.timetable.data.ics.IcsParser
import java.io.File
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The demo feed is what a reviewer or a curious student will paste in first,
 * so it must render a full timetable on any day of the academic year rather
 * than an empty week.
 */
class DemoFeedTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val events by lazy {
        IcsParser.parse(File("../demo/demo.ics").readText(), zone)
    }

    @Test
    fun `demo feed covers the whole academic year`() {
        val days = events.map { it.start.atZone(zone).toLocalDate() }
        val first = days.min()
        val last = days.max()
        println("demo: ${events.size} events, $first .. $last")
        assertTrue("expands to a term of classes, got ${events.size}", events.size > 300)
        assertTrue("starts in September", first.monthValue == 9)
        assertTrue("runs into June", last.monthValue >= 6)
    }

    @Test
    fun `demo feed exercises the features the app advertises`() {
        assertTrue("has all-day entries", events.any { it.isAllDay })
        assertTrue("has exams", events.any { it.isExam })
        assertTrue("has group tags", events.any { it.groupTokens.isNotEmpty() })
        assertTrue("has session types", events.any { it.typeLabel != null })
        assertTrue("breaks are excluded",
            events.none { it.start.atZone(zone).toLocalDate() == java.time.LocalDate.of(2026, 12, 28) })
    }
}
