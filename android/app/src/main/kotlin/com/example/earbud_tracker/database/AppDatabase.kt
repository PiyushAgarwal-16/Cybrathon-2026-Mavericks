package com.example.earbud_tracker.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ListeningSessionEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listeningSessionDao(): ListeningSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "earbuds_tracker_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
