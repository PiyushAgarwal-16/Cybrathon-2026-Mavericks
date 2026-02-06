package com.example.earbud_tracker.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listening_sessions")
data class ListeningSessionEntity(
    @PrimaryKey
    val id: String,
    val deviceName: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val avgVolume: Int,
    val maxVolume: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
