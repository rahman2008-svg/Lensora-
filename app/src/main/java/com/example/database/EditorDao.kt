package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EditorDao {
    @Query("SELECT * FROM presets ORDER BY name ASC")
    fun getAllPresets(): Flow<List<PresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE name = :name")
    suspend fun deletePresetByName(name: String)

    @Query("SELECT * FROM recent_edits ORDER BY timestamp DESC LIMIT 30")
    fun getRecentEdits(): Flow<List<RecentEditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentEdit(edit: RecentEditEntity)

    @Query("DELETE FROM recent_edits WHERE id = :id")
    suspend fun deleteRecentEditById(id: Long)

    @Query("DELETE FROM recent_edits")
    suspend fun clearAllRecentEdits()
}
