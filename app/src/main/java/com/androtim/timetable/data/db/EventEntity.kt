package com.androtim.timetable.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.androtim.timetable.data.model.CourseType
import com.androtim.timetable.data.model.ScheduleEvent
import java.time.Instant

@Entity(tableName = "events", indices = [Index("startMillis")])
data class EventEntity(
    @PrimaryKey val uid: String,
    val rawSummary: String,
    val courseCode: String?,
    val courseName: String,
    val type: String,
    val typeLabel: String?,
    val isExam: Boolean,
    val location: String,
    /** newline-joined */
    val teachers: String,
    /** newline-joined group tokens from DESCRIPTION */
    val groupTokens: String,
    val startMillis: Long,
    val endMillis: Long,
) {
    fun toDomain(): ScheduleEvent = ScheduleEvent(
        uid = uid,
        rawSummary = rawSummary,
        courseCode = courseCode,
        courseName = courseName,
        type = runCatching { CourseType.valueOf(type) }.getOrDefault(CourseType.OTHER),
        typeLabel = typeLabel,
        isExam = isExam,
        location = location,
        teachers = teachers.split('\n').filter { it.isNotBlank() },
        groupTokens = groupTokens.split('\n').filter { it.isNotBlank() },
        start = Instant.ofEpochMilli(startMillis),
        end = Instant.ofEpochMilli(endMillis),
    )

    companion object {
        fun fromDomain(e: ScheduleEvent): EventEntity = EventEntity(
            uid = e.uid,
            rawSummary = e.rawSummary,
            courseCode = e.courseCode,
            courseName = e.courseName,
            type = e.type.name,
            typeLabel = e.typeLabel,
            isExam = e.isExam,
            location = e.location,
            teachers = e.teachers.joinToString("\n"),
            groupTokens = e.groupTokens.joinToString("\n"),
            startMillis = e.start.toEpochMilli(),
            endMillis = e.end.toEpochMilli(),
        )
    }
}
