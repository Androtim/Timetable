package com.androtim.timetable.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** User note attached to a whole day (keyed by LocalDate.toEpochDay()). */
@Entity(tableName = "day_notes")
data class DayNoteEntity(
    @PrimaryKey val epochDay: Long,
    val note: String,
)

@Dao
interface DayNoteDao {
    @Query("SELECT * FROM day_notes")
    fun observeAll(): Flow<List<DayNoteEntity>>

    @Query("SELECT * FROM day_notes")
    suspend fun getAll(): List<DayNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: DayNoteEntity)

    @Query("DELETE FROM day_notes WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)
}
