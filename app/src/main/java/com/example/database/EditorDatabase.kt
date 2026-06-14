package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PresetEntity::class, RecentEditEntity::class], version = 1, exportSchema = false)
abstract class EditorDatabase : RoomDatabase() {
    abstract fun editorDao(): EditorDao

    companion object {
        @Volatile
        private var INSTANCE: EditorDatabase? = null

        fun getDatabase(context: Context): EditorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EditorDatabase::class.java,
                    "lensora_studio_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
