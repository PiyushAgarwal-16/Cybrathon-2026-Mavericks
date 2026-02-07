package com.example.earbud_tracker.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ListeningSessionEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listeningSessionDao(): ListeningSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add column with temporary default 0
                database.execSQL("ALTER TABLE listening_sessions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                // Update existing rows to have updatedAt = endTime
                database.execSQL("UPDATE listening_sessions SET updatedAt = endTime")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "earbuds_tracker_db"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
