package com.example.earbud_tracker.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ListeningSessionDao {
    @Insert
    fun insertSession(session: ListeningSessionEntity)

    @Update
    fun updateSession(session: ListeningSessionEntity)

    @Query("SELECT * FROM listening_sessions WHERE id = :id LIMIT 1")
    fun getSessionById(id: String): ListeningSessionEntity?

    @Query("SELECT * FROM listening_sessions WHERE startTime BETWEEN :start AND :end")
    fun getSessionsByDateRange(start: Long, end: Long): List<ListeningSessionEntity>

    @Query("SELECT SUM(durationSeconds) FROM listening_sessions WHERE startTime BETWEEN :start AND :end")
    fun getTotalDurationInRange(start: Long, end: Long): Long?

    @Query("SELECT strftime('%Y-%m-%d', startTime / 1000, 'unixepoch', 'localtime') as date, SUM(durationSeconds) as totalDurationSeconds, AVG(avgVolume) as avgVolume, MAX(maxVolume) as maxVolume FROM listening_sessions GROUP BY date ORDER BY date DESC")
    fun getDailySummaries(): List<DailySummary>

    @Query("SELECT * FROM listening_sessions WHERE startTime BETWEEN :start AND :end ORDER BY startTime DESC")
    fun getSessionsByDay(start: Long, end: Long): List<ListeningSessionEntity>

    @Query("SELECT * FROM listening_sessions WHERE startTime >= :start ORDER BY startTime DESC")
    fun getRecentSessions(start: Long): List<ListeningSessionEntity>

    @Query("SELECT * FROM listening_sessions WHERE synced = 0")
    fun getUnsyncedSessions(): List<ListeningSessionEntity>

    @Query("UPDATE listening_sessions SET synced = 1 WHERE id = :id")
    fun markSessionAsSynced(id: String)

}
