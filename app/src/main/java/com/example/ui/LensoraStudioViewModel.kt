package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.*
import com.example.model.EditState
import com.example.model.StickerOverlay
import com.example.model.TextOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

@OptIn(FlowPreview::class)
class LensoraStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val database = EditorDatabase.getDatabase(application)
    private val repository = EditorRepository(database.editorDao())

    // --- State Observables ---
    private val _currentEditState = MutableStateFlow(EditState())
    val currentEditState: StateFlow<EditState> = _currentEditState.asStateFlow()

    // Original loaded Bitmap
    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    // Standard preview Bitmap currently displayed
    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    // Loading indicator
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Preset list from Room
    val savedPresets: StateFlow<List<PresetEntity>> = repository.allPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // History of Recents from Room
    val recentEdits: StateFlow<List<RecentEditEntity>> = repository.recentEdits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Undo / Redo Stacks
    private val undoStack = mutableListOf<EditState>()
    private val redoStack = mutableListOf<EditState>()

    // Current URI or Asset identifier of the active image
    private var activeImageIdentifier: String = "synthetic_sunset"

    // Alert feedback
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Selected Overlay item ID for dragging
    private val _selectedOverlayId = MutableStateFlow<String?>(null)
    val selectedOverlayId: StateFlow<String?> = _selectedOverlayId.asStateFlow()

    init {
        // Load default synthetic asset on first launch
        loadSyntheticImage("sunset")
        
        // Listen to edit state changes to re-render preview with small debounce to prevent slider stutter
        viewModelScope.launch {
            _currentEditState
                .debounce(60) // quick debounce for smooth continuous slider scrolling
                .collect { state ->
                    renderPreview(state)
                }
        }
    }

    /**
     * Set toast message
     */
    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    /**
     * Selects an overlay to focus/edit
     */
    fun selectOverlay(id: String?) {
        _selectedOverlayId.value = id
    }

    /**
     * Loads a built-in high-quality colorful synthetic photo.
     * Perfect for offline/emulator/empty-gallery initial states.
     */
    fun loadSyntheticImage(type: String) {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val bitmap = when (type) {
                "sunset" -> createSyntheticSunset()
                "aurora" -> createSyntheticAurora()
                "sand" -> createSyntheticSand()
                else -> createSyntheticForest()
            }
            activeImageIdentifier = "synthetic_$type"
            
            withContext(Dispatchers.Main) {
                undoStack.clear()
                redoStack.clear()
                _originalBitmap.value = bitmap
                _currentEditState.value = EditState() // Reset sliders
                _isLoading.value = false
            }
        }
    }

    /**
     * Direct imports from local Gallery Uri
     */
    fun loadGalleryImage(uri: Uri) {
        _isLoading.value = true
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (original != null) {
                    val scaleBmp = resizeToSafeLimit(original, 1600)
                    activeImageIdentifier = uri.toString()
                    withContext(Dispatchers.Main) {
                        undoStack.clear()
                        redoStack.clear()
                        _originalBitmap.value = scaleBmp
                        _currentEditState.value = EditState() // Reset adjustments
                        _isLoading.value = false
                        showToast("Image imported successfully!")
                    }
                } else {
                    _isLoading.value = false
                    showToast("Failed to load image format.")
                }
            } catch (e: Exception) {
                _isLoading.value = false
                showToast("Error loading image: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Triggers active live preview rendering
     */
    private suspend fun renderPreview(state: EditState) {
        val original = _originalBitmap.value ?: return
        
        withContext(Dispatchers.Default) {
            // Scale down preview internally to around 800px to ensure ultra 60 FPS slider movement!
            val maxDimension = if (original.width > original.height) original.width else original.height
            val scaleFactor = 800f / maxDimension.toFloat()
            val previewSrc = if (scaleFactor < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scaleFactor).toInt(),
                    (original.height * scaleFactor).toInt(),
                    true
                )
            } else {
                original
            }

            val processed = ImageProcessor.processImage(previewSrc, state)
            withContext(Dispatchers.Main) {
                _previewBitmap.value = processed
            }
        }
    }

    /**
     * History Tracking: Saved state on slider release or button click
     */
    fun commitStateToHistory() {
        val currentState = _currentEditState.value
        if (undoStack.isEmpty() || undoStack.last() != currentState) {
            undoStack.add(currentState)
            if (undoStack.size > 20) undoStack.removeAt(0) // keep heap optimal
            redoStack.clear() // break linear redo branch
        }
    }

    /**
     * Undo action
     */
    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = _currentEditState.value
            redoStack.add(currentState)
            
            // Pop
            val previousState = undoStack.removeAt(undoStack.lastIndex)
            _currentEditState.value = previousState
            showToast("Undo applied")
        }
    }

    /**
     * Redo action
     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = _currentEditState.value
            undoStack.add(currentState)

            val nextState = redoStack.removeAt(redoStack.lastIndex)
            _currentEditState.value = nextState
            showToast("Redo applied")
        }
    }

    fun hasUndo(): Boolean = undoStack.isNotEmpty()
    fun hasRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Updates individual float properties of active adjustments
     */
    fun updateSlider(updater: (EditState) -> EditState) {
        _currentEditState.value = updater(_currentEditState.value)
    }

    /**
     * Apply a Lightroom-like Preset directly
     */
    fun applyPreset(name: String) {
        commitStateToHistory()
        val newState = when (name) {
            "Golden Hour" -> EditState(
                brightness = 10f, contrast = 12f, saturation = 25f,
                temperature = 35f, tint = 5f, highlights = 15f, shadows = 5f, vignette = 10f,
                presetName = "Golden Hour"
            )
            "Nordic Chill" -> EditState(
                brightness = -5f, contrast = 8f, saturation = -15f,
                temperature = -30f, tint = -10f, vignette = 15f,
                presetName = "Nordic Chill"
            )
            "Matte Retro" -> EditState(
                brightness = 15f, contrast = -10f, saturation = -5f,
                temperature = 15f, tint = 5f, vignette = 20f,
                presetName = "Matte Retro"
            )
            "Noir Drama" -> EditState(
                brightness = -5f, contrast = 25f, saturation = -100f,
                sharpness = 30f, vignette = 35f,
                presetName = "Noir Drama"
            )
            "Vivid Pop" -> EditState(
                brightness = 5f, contrast = 15f, saturation = 40f,
                sharpness = 20f,
                presetName = "Vivid Pop"
            )
            "Warm Portrait" -> EditState(
                brightness = 10f, saturation = 10f, temperature = 15f,
                tint = 10f, blur = 15f,
                presetName = "Warm Portrait"
            )
            else -> EditState() // Default
        }
        _currentEditState.value = newState
        showToast("Preset Applied: $name")
    }

    /**
     * Offline Database custom preset saving
     */
    fun saveCustomPreset(presetName: String) {
        if (presetName.isBlank()) return
        val current = _currentEditState.value
        viewModelScope.launch(Dispatchers.IO) {
            val entity = PresetEntity(
                name = presetName,
                brightness = current.brightness,
                contrast = current.contrast,
                saturation = current.saturation,
                exposure = current.exposure,
                highlights = current.highlights,
                shadows = current.shadows,
                temperature = current.temperature,
                tint = current.tint,
                sharpness = current.sharpness,
                blur = current.blur,
                vignette = current.vignette,
                backgroundBlur = current.backgroundBlur,
                filterName = current.filterName,
                frameType = current.frameType
            )
            repository.insertPreset(entity)
            withContext(Dispatchers.Main) {
                showToast("Custom preset '$presetName' saved permanently!")
            }
        }
    }

    /**
     * Permanent delete database preset
     */
    fun deleteCustomPreset(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePreset(name)
            withContext(Dispatchers.Main) {
                showToast("Preset '$name' deleted.")
            }
        }
    }

    /**
     * Applies stored preset values retrieved from database Entity
     */
    fun applyPresetEntity(preset: PresetEntity) {
        commitStateToHistory()
        _currentEditState.value = EditState(
            brightness = preset.brightness,
            contrast = preset.contrast,
            saturation = preset.saturation,
            exposure = preset.exposure,
            highlights = preset.highlights,
            shadows = preset.shadows,
            temperature = preset.temperature,
            tint = preset.tint,
            sharpness = preset.sharpness,
            blur = preset.blur,
            vignette = preset.vignette,
            backgroundBlur = preset.backgroundBlur,
            filterName = preset.filterName,
            frameType = preset.frameType,
            presetName = preset.name
        )
        showToast("Applied Preset: ${preset.name}")
    }

    /**
     * Saves editing session locally to Room RecentEdit history log
     */
    fun saveEditToHistoryLog() {
        val current = _currentEditState.value
        if (!current.isModified()) return // No edits to log

        viewModelScope.launch(Dispatchers.IO) {
            val entity = RecentEditEntity(
                imagePath = activeImageIdentifier,
                brightness = current.brightness,
                contrast = current.contrast,
                saturation = current.saturation,
                exposure = current.exposure,
                highlights = current.highlights,
                shadows = current.shadows,
                temperature = current.temperature,
                tint = current.tint,
                sharpness = current.sharpness,
                blur = current.blur,
                vignette = current.vignette,
                backgroundBlur = current.backgroundBlur,
                filterName = current.filterName,
                presetName = current.presetName,
                rotationAngle = current.rotationAngle,
                flipHorizontal = current.flipHorizontal,
                flipVertical = current.flipVertical,
                cropRatio = current.cropRatio,
                frameType = current.frameType
            )
            repository.insertRecentEdit(entity)
        }
    }

    /**
     * Restores an edit session directly from a Room RecentEdit item.
     */
    fun restoreEditFromLog(recent: RecentEditEntity) {
        viewModelScope.launch(Dispatchers.Default) {
            _isLoading.value = true
            // Load Bitmap from identifier (either a synthetic name or file path)
            var targetBitmap: Bitmap? = null
            if (recent.imagePath.startsWith("synthetic_")) {
                val type = recent.imagePath.substringAfter("synthetic_")
                targetBitmap = when (type) {
                    "sunset" -> createSyntheticSunset()
                    "aurora" -> createSyntheticAurora()
                    "sand" -> createSyntheticSand()
                    else -> createSyntheticForest()
                }
            } else {
                // Parse Gallery Uri securely
                try {
                    val context = getApplication<Application>()
                    val inputStream = context.contentResolver.openInputStream(Uri.parse(recent.imagePath))
                    val original = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (original != null) {
                        targetBitmap = resizeToSafeLimit(original, 1600)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                if (targetBitmap != null) {
                    undoStack.clear()
                    redoStack.clear()
                    activeImageIdentifier = recent.imagePath
                    _originalBitmap.value = targetBitmap
                    _currentEditState.value = EditState(
                        brightness = recent.brightness,
                        contrast = recent.contrast,
                        saturation = recent.saturation,
                        exposure = recent.exposure,
                        highlights = recent.highlights,
                        shadows = recent.shadows,
                        temperature = recent.temperature,
                        tint = recent.tint,
                        sharpness = recent.sharpness,
                        blur = recent.blur,
                        vignette = recent.vignette,
                        backgroundBlur = recent.backgroundBlur,
                        filterName = recent.filterName,
                        presetName = recent.presetName,
                        rotationAngle = recent.rotationAngle,
                        flipHorizontal = recent.flipHorizontal,
                        flipVertical = recent.flipVertical,
                        cropRatio = recent.cropRatio,
                        frameType = recent.frameType
                    )
                    showToast("Restored edit session!")
                } else {
                    showToast("Original image could not be loaded locally.")
                }
                _isLoading.value = false
            }
        }
    }

    /**
     * Clears local edits history logs
     */
    fun clearHistories() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearRecentEdits()
            withContext(Dispatchers.Main) {
                showToast("History log cleared.")
            }
        }
    }

    /**
     * Dynamic text overlay support
     */
    fun addTextOverlay(text: String, color: Long = 0xFFFFFFFF, bg: Long = 0xAA000000) {
        commitStateToHistory()
        updateSlider { state ->
            val list = state.textOverlays.toMutableList()
            val item = TextOverlay(text = text, color = color, backgroundColor = bg)
            list.add(item)
            _selectedOverlayId.value = item.id
            state.copy(textOverlays = list)
        }
        showToast("Text added! Drag to position.")
    }

    fun updateTextOverlay(id: String, x: Float, y: Float) {
        updateSlider { state ->
            val list = state.textOverlays.map {
                if (it.id == id) it.copy(xPct = x.coerceIn(0.01f, 0.99f), yPct = y.coerceIn(0.01f, 0.99f)) else it
            }
            state.copy(textOverlays = list)
        }
    }

    fun removeTextOverlay(id: String) {
        commitStateToHistory()
        updateSlider { state ->
            val list = state.textOverlays.filter { it.id != id }
            if (_selectedOverlayId.value == id) _selectedOverlayId.value = null
            state.copy(textOverlays = list)
        }
    }

    /**
     * Dynamic sticker support
     */
    fun addStickerOverlay(sticker: String) {
        commitStateToHistory()
        updateSlider { state ->
            val list = state.stickerOverlays.toMutableList()
            val item = StickerOverlay(sticker = sticker)
            list.add(item)
            _selectedOverlayId.value = item.id
            state.copy(stickerOverlays = list)
        }
        showToast("Sticker added! Drag to position.")
    }

    fun updateStickerOverlay(id: String, x: Float, y: Float) {
        updateSlider { state ->
            val list = state.stickerOverlays.map {
                if (it.id == id) it.copy(xPct = x.coerceIn(0.01f, 0.99f), yPct = y.coerceIn(0.01f, 0.99f)) else it
            }
            state.copy(stickerOverlays = list)
        }
    }

    fun removeStickerOverlay(id: String) {
        commitStateToHistory()
        updateSlider { state ->
            val list = state.stickerOverlays.filter { it.id != id }
            if (_selectedOverlayId.value == id) _selectedOverlayId.value = null
            state.copy(stickerOverlays = list)
        }
    }

    /**
     * Final high-resolution flat export in PNG/JPG formats
     */
    fun exportFinalImage(
        context: Context,
        format: Bitmap.CompressFormat,
        exportName: String,
        onComplete: (File?) -> Unit
    ) {
        val original = _originalBitmap.value
        if (original == null) {
            onComplete(null)
            return
        }

        val state = _currentEditState.value
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Apply all adjustments and annotations onto the original full resolution image
                val exportedBmp = ImageProcessor.finalizeExport(original, state)

                // Save permanently offline to App Downloads or standard offline Pictures App Directory
                val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val file = File(dir, "Lensora_${exportName.replace(" ", "_")}_${System.currentTimeMillis()}.$ext")

                val outputStream: OutputStream = FileOutputStream(file)
                val compLevel = if (format == Bitmap.CompressFormat.PNG) 100 else 90
                exportedBmp.compress(format, compLevel, outputStream)
                outputStream.flush()
                outputStream.close()

                // Save the session details to recents history log!
                saveEditToHistoryLog()

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    onComplete(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    onComplete(null)
                }
            }
        }
    }

    // --- Core High-Fidelity Synthetic Image Renderers ---

    private fun createSyntheticSunset(): Bitmap {
        val bmp = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Linear Sky blend
        val grad = LinearGradient(0f, 0f, 0f, 900f,
            intArrayOf(
                Color.parseColor("#0F2027"),
                Color.parseColor("#203A43"),
                Color.parseColor("#2C5364"),
                Color.parseColor("#f12711"),
                Color.parseColor("#f5af19")
            ),
            floatArrayOf(0f, 0.25f, 0.5f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { shader = grad }
        canvas.drawRect(0f, 0f, 1200f, 900f, paint)

        // Golden Solar Circle
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFEFC6")
        }
        canvas.drawCircle(600f, 500f, 120f, sunPaint)

        // Mountain Silhouetting
        val path = Path()
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#15031C")
            style = Paint.Style.FILL
        }

        path.moveTo(0f, 900f)
        path.lineTo(180f, 620f)
        path.lineTo(400f, 750f)
        path.lineTo(680f, 550f)
        path.lineTo(950f, 720f)
        path.lineTo(1200f, 480f)
        path.lineTo(1200f, 900f)
        path.close()
        canvas.drawPath(path, fillPaint)

        return bmp
    }

    private fun createSyntheticAurora(): Bitmap {
        val bmp = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Deep star field dark canvas
        canvas.drawColor(Color.parseColor("#07091B"))

        // Add small random stars
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val random = java.util.Random(1337)
        for (i in 0 until 120) {
            val sx = random.nextFloat() * 1200f
            val sy = random.nextFloat() * 550f
            val size = 1f + random.nextFloat() * 2.5f
            canvas.drawCircle(sx, sy, size, starPaint)
        }

        // Swooping glowing Aurora Wave
        val thickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 60f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        // Multi-layered waves
        for (idx in 0 until 3) {
            val offset = idx * 80f
            val alpha = (160 - idx * 45).coerceIn(40, 255)
            thickPaint.color = Color.argb(alpha, 3, 240, 160 - idx * 30)

            val wavePath = Path()
            wavePath.moveTo(-50f, 350f + offset)
            wavePath.quadTo(350f, 100f + offset, 700f, 300f + offset)
            wavePath.quadTo(1000f, 450f + offset, 1250f, 200f + offset)

            // Blur mask simulate using simple paint shadow
            thickPaint.setShadowLayer(40f, 0f, 0f, Color.parseColor("#00FF88"))
            canvas.drawPath(wavePath, thickPaint)
        }

        return bmp
    }

    private fun createSyntheticSand(): Bitmap {
        val bmp = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Desert warm gradient base
        val grad = LinearGradient(0f, 0f, 1200f, 900f,
            intArrayOf(Color.parseColor("#EDC99F"), Color.parseColor("#ECA36B"), Color.parseColor("#B3663C")),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 1200f, 900f, Paint().apply { shader = grad })

        // Smooth wave layer shadows mimicking dunes
        val dunePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val duneColors = intArrayOf(
            Color.parseColor("#E89B62"),
            Color.parseColor("#D37A3C"),
            Color.parseColor("#A14F21")
        )

        for (i in 0 until 3) {
            dunePaint.color = duneColors[i]
            val path = Path()
            val startY = 500f + i * 110f
            path.moveTo(-10f, 910f)
            path.lineTo(-10f, startY)
            path.quadTo(300f + i * 150f, startY - 140f, 650f + i * 80f, startY - 20f)
            path.quadTo(950f, startY + 80f, 1210f, startY - 100f)
            path.lineTo(1210f, 910f)
            path.close()
            canvas.drawPath(path, dunePaint)
        }

        return bmp
    }

    private fun createSyntheticForest(): Bitmap {
        val bmp = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Foggy Emerald forest sky
        val skyGrad = LinearGradient(0f, 0f, 0f, 900f,
            intArrayOf(Color.parseColor("#1D3332"), Color.parseColor("#3F5E5A"), Color.parseColor("#6FA19C")),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 1200f, 900f, Paint().apply { shader = skyGrad })

        // Silhouette multiple Pine Tree rows with increasing density/darkness
        val treePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val treeRowColors = intArrayOf(
            Color.parseColor("#3C524F"), // distant gray-green
            Color.parseColor("#263D3B"), // mid ground
            Color.parseColor("#112422"), // close ground
        )

        for (row in 0 until 3) {
            treePaint.color = treeRowColors[row]
            val count = 12 - row * 2
            val spacing = 1200f / (count - 1)
            val baseRowHeight = 550f + row * 100f

            val treeRandom = java.util.Random(row * 42L)
            for (ti in 0 until count) {
                // Draw triangles to build a pine tree silhouette
                val tx = ti * spacing + (treeRandom.nextFloat() - 0.5f) * 40f
                val th = 150f + row * 90f + treeRandom.nextFloat() * 60f
                val ty = baseRowHeight - th

                val tPath = Path()
                tPath.moveTo(tx, ty)
                tPath.lineTo(tx - 35f - row * 15f, ty + th * 0.4f)
                tPath.lineTo(tx - 15f - row * 5f, ty + th * 0.4f)
                tPath.lineTo(tx - 50f - row * 20f, ty + th * 0.7f)
                tPath.lineTo(tx - 20f - row * 8f, ty + th * 0.7f)
                tPath.lineTo(tx - 70f - row * 25f, ty + th)
                tPath.lineTo(tx + 70f + row * 25f, ty + th)
                tPath.lineTo(tx + 20f + row * 8f, ty + th * 0.7f)
                tPath.lineTo(tx + 50f + row * 20f, ty + th * 0.7f)
                tPath.lineTo(tx + 15f + row * 5f, ty + th * 0.4f)
                tPath.lineTo(tx + 35f + row * 15f, ty + th * 0.4f)
                tPath.close()
                canvas.drawPath(tPath, treePaint)
            }

            // Ground cover linking trees together
            canvas.drawRect(0f, baseRowHeight, 1200f, 900f, treePaint)
        }

        return bmp
    }

    private fun resizeToSafeLimit(src: Bitmap, maxDim: Int): Bitmap {
        if (src.width <= maxDim && src.height <= maxDim) return src
        val ratio = src.width.toFloat() / src.height.toFloat()
        val newW: Int
        val newH: Int
        if (src.width > src.height) {
            newW = maxDim
            newH = (maxDim / ratio).toInt()
        } else {
            newH = maxDim
            newW = (maxDim * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }
}
