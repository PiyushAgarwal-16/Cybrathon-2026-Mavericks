package com.example.earbud_tracker.database

data class DailySummary(
    val date: String,
    val totalDurationSeconds: Long,
    val avgVolume: Int,
    val maxVolume: Int
)
