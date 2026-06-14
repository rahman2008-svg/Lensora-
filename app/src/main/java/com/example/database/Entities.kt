package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val name: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val exposure: Float,
    val highlights: Float,
    val shadows: Float,
    val temperature: Float,
    val tint: Float,
    val sharpness: Float,
    val blur: Float,
    val vignette: Float,
    val backgroundBlur: Float,
    val filterName: String,
    val frameType: String
)

@Entity(tableName = "recent_edits")
data class RecentEditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String, // Preset resource drawing path or absolute uri
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val exposure: Float,
    val highlights: Float,
    val shadows: Float,
    val temperature: Float,
    val tint: Float,
    val sharpness: Float,
    val blur: Float,
    val vignette: Float,
    val backgroundBlur: Float,
    val filterName: String,
    val presetName: String,
    val rotationAngle: Float,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val cropRatio: String,
    val frameType: String
)
