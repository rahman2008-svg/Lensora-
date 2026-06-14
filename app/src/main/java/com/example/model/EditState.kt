package com.example.model

import java.util.UUID

data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val color: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0xAA000000,
    val size: Float = 24f,
    val xPct: Float = 0.5f,
    val yPct: Float = 0.5f
)

data class StickerOverlay(
    val id: String = UUID.randomUUID().toString(),
    val sticker: String,
    val size: Float = 48f,
    val xPct: Float = 0.5f,
    val yPct: Float = 0.5f
)

data class EditState(
    val brightness: Float = 0f,     // -100f to 100f
    val contrast: Float = 0f,       // -100f to 100f
    val saturation: Float = 0f,     // -100f to 100f
    val exposure: Float = 0f,       // -100f to 100f
    val highlights: Float = 0f,     // -100f to 100f
    val shadows: Float = 0f,        // -100f to 100f
    val temperature: Float = 0f,    // -100f to 100f
    val tint: Float = 0f,           // -100f to 100f
    val sharpness: Float = 0f,      // 0f to 100f
    val blur: Float = 0f,           // 0f to 100f
    val vignette: Float = 0f,       // 0f to 100f
    val backgroundBlur: Float = 0f, // 0f to 100f
    val filterName: String = "None", // "None", "Vintage", "B&W", "Punchy", "Cinematic", "Warm", "Cool", "Chrome"
    val presetName: String = "Default", // "Default", "Golden Hour", "Matte Retro", "Noir Drama", "Vivid Pop", "Warm Portrait"
    val rotationAngle: Float = 0f,  // 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropRatio: String = "Original", // "Original", "1:1", "4:3", "16:9", "2:3", "3:4"
    val frameType: String = "None",     // "None", "Classic White", "Studio Black", "Vintage Film", "Polaroid Border"
    val textOverlays: List<TextOverlay> = emptyList(),
    val stickerOverlays: List<StickerOverlay> = emptyList()
) {
    fun isModified(): Boolean {
        return brightness != 0f ||
                contrast != 0f ||
                saturation != 0f ||
                exposure != 0f ||
                highlights != 0f ||
                shadows != 0f ||
                temperature != 0f ||
                tint != 0f ||
                sharpness != 0f ||
                blur != 0f ||
                vignette != 0f ||
                backgroundBlur != 0f ||
                filterName != "None" ||
                presetName != "Default" ||
                rotationAngle != 0f ||
                flipHorizontal ||
                flipVertical ||
                cropRatio != "Original" ||
                frameType != "None" ||
                textOverlays.isNotEmpty() ||
                stickerOverlays.isNotEmpty()
    }
}
