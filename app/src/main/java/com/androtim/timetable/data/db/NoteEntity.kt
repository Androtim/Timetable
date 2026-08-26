package com.androtim.timetable.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** User note attached to one timetable event (keyed by the event's iCal UID). */
@Entity(tableName = "event_notes")
data class NoteEntity(
    @PrimaryKey val uid: String,
    val note: String,
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM event_notes")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM event_notes")
    suspend fun getAll(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM event_notes WHERE uid = :uid")
    suspend fun delete(uid: String)
}
