package com.androtim.timetable.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events WHERE startMillis >= :fromMillis AND startMillis < :toMillis ORDER BY startMillis")
    fun observeRange(fromMillis: Long, toMillis: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE startMillis >= :fromMillis AND startMillis < :toMillis ORDER BY startMillis")
    suspend fun getRange(fromMillis: Long, toMillis: Long): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    @Query("SELECT * FROM events")
    fun observeAll(): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("DELETE FROM events")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(events: List<EventEntity>) {
        clear()
        insertAll(events)
    }
}
