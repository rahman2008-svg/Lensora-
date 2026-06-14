package com.example.database

import kotlinx.coroutines.flow.Flow

class EditorRepository(private val editorDao: EditorDao) {

    val allPresets: Flow<List<PresetEntity>> = editorDao.getAllPresets()
    val recentEdits: Flow<List<RecentEditEntity>> = editorDao.getRecentEdits()

    suspend fun insertPreset(preset: PresetEntity) {
        editorDao.insertPreset(preset)
    }

    suspend fun deletePreset(name: String) {
        editorDao.deletePresetByName(name)
    }

    suspend fun insertRecentEdit(edit: RecentEditEntity) {
        editorDao.insertRecentEdit(edit)
    }

    suspend fun deleteRecentEdit(id: Long) {
        editorDao.deleteRecentEditById(id)
    }

    suspend fun clearRecentEdits() {
        editorDao.clearAllRecentEdits()
    }
}
