package com.example.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.PresetEntity
import com.example.database.RecentEditEntity
import com.example.model.EditState
import com.example.model.StickerOverlay
import com.example.model.TextOverlay
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LensoraStudioApp(
    viewModel: LensoraStudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val editState by viewModel.currentEditState.collectAsStateWithLifecycle()
    val rawOriginal by viewModel.originalBitmap.collectAsStateWithLifecycle()
    val previewBmp by viewModel.previewBitmap.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val toastMsg by viewModel.toastMessage.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedOverlayId.collectAsStateWithLifecycle()

    val savedPresets by viewModel.savedPresets.collectAsStateWithLifecycle()
    val recentEdits by viewModel.recentEdits.collectAsStateWithLifecycle()

    // UI Configuration States
    var currentTab by remember { mutableStateOf("Adjust") } // "Preset", "Adjust", "Filter", "Crop", "Overlay", "MyPresets", "History"
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showPresetSaveDialog by remember { mutableStateOf(false) }
    var queryPresetName by remember { mutableStateOf("") }

    // Dialog state for texting overlays
    var showAddTextDialog by remember { mutableStateOf(false) }
    var textOverlayInput by remember { mutableStateOf("") }
    var textOverlayColor by remember { mutableStateOf(0xFFFFFFFFL) }
    var textOverlayBgColor by remember { mutableStateOf(0xAA000000L) }

    // Temporary compared state (Press and hold compare button)
    var isComparingOriginal by remember { mutableStateOf(false) }

    // Toast listener
    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // Media picking handler
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadGalleryImage(uri)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("lensora_studio_app")
            .drawBehind {
                // Frosted Glass deep dark ambient backdrop with a glowing soft indigo radial gradient on top-left and soft cyan on bottom-right
                drawRect(Color(0xFF0A0A0F))
                
                // Soft elegant Indigo glow orb in top-left
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x336366F1), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.minDimension * 0.9f
                    ),
                    radius = size.minDimension * 0.9f,
                    center = Offset(0f, 0f)
                )

                // Soft elegant Cyan / Ice Blue glow orb in bottom-right
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1F818CF8), Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = size.minDimension * 0.9f
                    ),
                    radius = size.minDimension * 0.9f,
                    center = Offset(size.width, size.height)
                )
            },
        containerColor = Color.Transparent, // Let the beautiful gradient glow orbs show through!
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0x66000000), // Sleek highly translucent frosted glass bar
                        titleContentColor = Color.White
                    ),
                    modifier = Modifier.drawBehind {
                        drawLine(
                            color = Color(0x10FFFFFF),
                            start = Offset(0f, size.height - 1f),
                            end = Offset(size.width, size.height - 1f),
                            strokeWidth = 1f
                        )
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FilterCenterFocus,
                                contentDescription = null,
                                tint = Color(0xFF6366F1), // Dynamic Indigo highlight
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LENSORA",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "STUDIO",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Light,
                                color = Color.White.copy(alpha = 0.6f),
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    },
                    actions = {
                        // Undo action
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = viewModel.hasUndo()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = "Undo",
                                tint = if (viewModel.hasUndo()) Color.White else Color.Gray.copy(alpha = 0.4f)
                            )
                        }

                        // Redo action
                        IconButton(
                            onClick = { viewModel.redo() },
                            enabled = viewModel.hasRedo()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Redo,
                                contentDescription = "Redo",
                                tint = if (viewModel.hasRedo()) Color.White else Color.Gray.copy(alpha = 0.4f)
                            )
                        }

                        // Reset edits
                        IconButton(onClick = {
                            viewModel.commitStateToHistory()
                            viewModel.updateSlider { EditState() } // Reset sliders
                            viewModel.showToast("All edits reset.")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Settings",
                                tint = Color.White
                            )
                        }

                        // Info About Dialogue
                        IconButton(onClick = { showAboutDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Credits & About",
                                tint = Color(0xFF818CF8)
                            )
                        }

                        // Save Template Preset
                        IconButton(onClick = { showPresetSaveDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.BookmarkBorder,
                                contentDescription = "Save Custom Preset",
                                tint = Color.LightGray
                            )
                        }
                    }
                )

                // Sub header holding file inputs and export button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33000000))
                        .drawBehind {
                            drawLine(
                                color = Color(0x10FFFFFF),
                                start = Offset(0f, size.height - 1f),
                                end = Offset(size.width, size.height - 1f),
                                strokeWidth = 1f
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Open photos
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x1AFFFFFF),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0x12FFFFFF)),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import Photos", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Export output image file saving
                    Button(
                        onClick = { showExportDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Studio", fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ------------------ IMAGE VIEWPORT WORKSPACE CANVAS ------------------
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0x11000000))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Render Image container
                    val renderBitmap = if (isComparingOriginal) rawOriginal else previewBmp

                    if (renderBitmap != null) {
                        var canvasW by remember { mutableStateOf(1) }
                        var canvasH by remember { mutableStateOf(1) }

                        Box(
                            modifier = Modifier
                                .wrapContentSize()
                                .onGloballyPositioned {
                                    canvasW = if (it.size.width > 0) it.size.width else 1
                                    canvasH = if (it.size.height > 0) it.size.height else 1
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                bitmap = renderBitmap.asImageBitmap(),
                                contentDescription = "Active Project Canvas",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.heightIn(max = 450.dp)
                            )

                            // Render Stickers and Text overlays live only when not looking at compared raw original
                            if (!isComparingOriginal) {
                                // Stickers overlays
                                editState.stickerOverlays.forEach { sticker ->
                                    val isSelected = sticker.id == selectedId
                                    Box(
                                        modifier = Modifier
                                            .absoluteOffset {
                                                IntOffset(
                                                    (sticker.xPct * canvasW).roundToInt() - 48,
                                                    (sticker.yPct * canvasH).roundToInt() - 48
                                                )
                                            }
                                            .pointerInput(sticker.id) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    viewModel.selectOverlay(sticker.id)
                                                    val nextX = sticker.xPct + dragAmount.x / canvasW
                                                    val nextY = sticker.yPct + dragAmount.y / canvasH
                                                    viewModel.updateStickerOverlay(sticker.id, nextX, nextY)
                                                }
                                            }
                                            .pointerInput(sticker.id) {
                                                detectTapGestures {
                                                    viewModel.selectOverlay(sticker.id)
                                                }
                                            }
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.dp,
                                                color = if (isSelected) Color(0xFF6366F1) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = sticker.sticker,
                                                fontSize = (sticker.size / 1.5f).sp
                                            )
                                            if (isSelected) {
                                                IconButton(
                                                    onClick = { viewModel.removeStickerOverlay(sticker.id) },
                                                    modifier = Modifier.size(16.dp).background(Color.Red, CircleShape)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Delete",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Text Overlays
                                editState.textOverlays.forEach { textOverlay ->
                                    val isSelected = textOverlay.id == selectedId
                                    Box(
                                        modifier = Modifier
                                            .absoluteOffset {
                                                IntOffset(
                                                    (textOverlay.xPct * canvasW).roundToInt() - 100,
                                                    (textOverlay.yPct * canvasH).roundToInt() - 25
                                                )
                                            }
                                            .pointerInput(textOverlay.id) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    viewModel.selectOverlay(textOverlay.id)
                                                    val nextX = textOverlay.xPct + dragAmount.x / canvasW
                                                    val nextY = textOverlay.yPct + dragAmount.y / canvasH
                                                    viewModel.updateTextOverlay(textOverlay.id, nextX, nextY)
                                                }
                                            }
                                            .pointerInput(textOverlay.id) {
                                                detectTapGestures {
                                                    viewModel.selectOverlay(textOverlay.id)
                                                }
                                            }
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.dp,
                                                color = if (isSelected) Color(0xFF6366F1) else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .background(
                                                color = Color((textOverlay.backgroundColor)),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = textOverlay.text,
                                                color = Color(textOverlay.color),
                                                fontSize = (textOverlay.size / 1.5f).sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                            if (isSelected) {
                                                IconButton(
                                                    onClick = { viewModel.removeTextOverlay(textOverlay.id) },
                                                    modifier = Modifier.size(16.dp).background(Color.Red, CircleShape)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Delete",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty states
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF818CF8))
                        }
                    }

                    // Circular comparison FAB on right edge
                    if (rawOriginal != null && previewBmp != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isComparingOriginal = true
                                            tryAwaitRelease()
                                            isComparingOriginal = false
                                        }
                                    )
                                }
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.82f))
                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CompareArrows,
                                    contentDescription = "Hold to compare Original",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Hold to Compare",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Indicator if showing compared raw original
                    if (isComparingOriginal) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Red.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                        ) {
                            Text(
                                "SHOWING ORIGINAL (BEFORE)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Small indicator overlay for loading
                    if (isLoading) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.82f),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFF818CF8), modifier = Modifier.size(24.dp))
                                Text("Processing offline...", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // ------------------ MID SECTION DYNAMIC FEEDBACK CONTROLS ------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC000000)) // Highly translucent dark glass cover
                        .border(
                            BorderStroke(1.dp, Color(0x1BFFFFFF)), // Pristine glassy border line
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Dynamically render panels based on selected navigation tab
                        when (currentTab) {
                            "Preset" -> PresetPanel(onSelect = { viewModel.applyPreset(it) })
                            "Adjust" -> AdjustPanel(state = editState, onUpdate = { viewModel.updateSlider(it) }, onFinished = { viewModel.commitStateToHistory() })
                            "Filter" -> FilterPanel(activeFilter = editState.filterName, onSelect = {
                                viewModel.commitStateToHistory()
                                viewModel.updateSlider { state -> state.copy(filterName = it) }
                            })
                            "Crop" -> CropPanel(state = editState, onUpdate = { viewModel.updateSlider(it) })
                            "Overlay" -> OverlayPanel(
                                state = editState,
                                onAddText = { textOverlayInput = ""; showAddTextDialog = true },
                                onAddSticker = { viewModel.addStickerOverlay(it) },
                                onCloseFocus = { viewModel.selectOverlay(null) }
                            )
                            "MyPresets" -> MyPresetsPanel(
                                savedPresets = savedPresets,
                                onApply = { viewModel.applyPresetEntity(it) },
                                onDelete = { viewModel.deleteCustomPreset(it) }
                            )
                            "History" -> HistoryPanel(
                                recentEdits = recentEdits,
                                onRestore = { viewModel.restoreEditFromLog(it) },
                                onClearAll = { viewModel.clearHistories() }
                            )
                        }
                    }
                }

                // ------------------ BOTTOM MULTI-TAB CONTROLS NAVIGATION BAR ------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x99000000))
                        .drawBehind {
                            drawLine(
                                color = Color(0x10FFFFFF),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1f
                            )
                        }
                ) {
                    // Inline horizontal choice of synthetic images to switch easily (increases fun and utility)
                    val preloadedPhotos = listOf(
                        "sunset" to "🌅 Sunset Bliss",
                        "aurora" to "🌌 Cosmic Aurora",
                        "sand" to "🏜️ Sand Dunes",
                        "forest" to "🌲 Misty Pines"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Samples:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray.copy(alpha = 0.6f)
                        )
                        preloadedPhotos.forEach { (key, title) ->
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x15FFFFFF))
                                    .border(1.dp, Color(0x0EFFFFFF), RoundedCornerShape(8.dp))
                                    .clickable { viewModel.loadSyntheticImage(key) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0x12FFFFFF))

                    // Studio Bottom Navigation Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val tabs = listOf(
                            "Adjust" to Icons.Default.Tune,
                            "Preset" to Icons.Default.AutoAwesome,
                            "Filter" to Icons.Default.Palette,
                            "Crop" to Icons.Default.Crop,
                            "Overlay" to Icons.Default.Layers,
                            "MyPresets" to Icons.Default.Star,
                            "History" to Icons.Default.History
                        )

                        tabs.forEach { (tabName, icon) ->
                            val isSelected = currentTab == tabName
                            Column(
                                modifier = Modifier
                                    .clickable { currentTab = tabName }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tabName,
                                    tint = if (isSelected) Color(0xFF818CF8) else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (tabName == "MyPresets") "My Presets" else tabName,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF818CF8) else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------ ALL DIALOGS (ABOUT, EXPORT, SAVE PRESET, TEXTS) ------------------

    // 1. About Prince AR Abdur Rahman & Lensora Studio Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = Color(0xFF14141F),
            modifier = Modifier.border(1.dp, Color(0x1F6366F1), RoundedCornerShape(20.dp)),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterCenterFocus,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("About Lensora Studio", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Lensora Studio is an extremely powerful offline image editor inspired by Lightroom. " +
                        "It provides professional photo adjustment controls, beautiful filters, crop, rotations, " +
                        "overlays, custom presets and historic session logging running 100% on-device for total privacy.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(color = Color(0x15FFFFFF))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("App Information", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF818CF8))
                        Text("• App Name: Lensora Studio", fontSize = 12.sp)
                        Text("• Version: 1.0.0", fontSize = 12.sp)
                        Text("• Offline Status: 100% Complete Offline Engine", fontSize = 12.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("About Developer", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF818CF8))
                        Text("• Name: Prince AR Abdur Rahman", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Prince AR Abdur Rahman is an independent app developer passionate about building modern Android applications, " +
                            "productivity tools, AI-powered experiences, media players, educational apps, and next-generation digital products.",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("About Company Office", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF818CF8))
                        Text("• Company: NexVora Lab's Ofc", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "NexVora Lab's Ofc has a strict mission to build fast, beautiful, privacy-friendly, " +
                            "and user-focused applications accessible to everyone.",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }

                    HorizontalDivider(color = Color(0x15FFFFFF))

                    Text(
                        "Credits\nDeveloped by Prince AR Abdur Rahman\nPublished by NexVora Lab's Ofc\n© 2026 NexVora Lab's Ofc. All Rights Reserved",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Excellent")
                }
            }
        )
    }

    // 2. Export Studio Dialog
    if (showExportDialog) {
        var exportFormat by remember { mutableStateOf(Bitmap.CompressFormat.JPEG) } // PNG or JPEG
        var exportNameInput by remember { mutableStateOf("My_Lensora_Art") }
        var savedFileResult by remember { mutableStateOf<File?>(null) }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = Color(0xFF14141F),
            modifier = Modifier.border(1.dp, Color(0x1F6366F1), RoundedCornerShape(20.dp)),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = Color(0xFF818CF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Options", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (savedFileResult == null) {
                        Text(
                            "Configure your export options below. Lensora Studio processes all filters, adjustments " +
                            "and overlays onto the original high-resolution photo on-device, saving it privately.",
                            fontSize = 13.sp
                        )

                        OutlinedTextField(
                            value = exportNameInput,
                            onValueChange = { exportNameInput = it },
                            label = { Text("File Name Prefix") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Column {
                            Text("Export Format", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { exportFormat = Bitmap.CompressFormat.JPEG }
                                ) {
                                    RadioButton(
                                        selected = exportFormat == Bitmap.CompressFormat.JPEG,
                                        onClick = { exportFormat = Bitmap.CompressFormat.JPEG },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF818CF8))
                                    )
                                    Text("JPG (Compressed)", fontSize = 13.sp, color = Color.White)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { exportFormat = Bitmap.CompressFormat.PNG }
                                ) {
                                    RadioButton(
                                        selected = exportFormat == Bitmap.CompressFormat.PNG,
                                        onClick = { exportFormat = Bitmap.CompressFormat.PNG },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF818CF8))
                                    )
                                    Text("PNG (Lossless)", fontSize = 13.sp, color = Color.White)
                                }
                            }
                        }
                    } else {
                        // Display Saved Success Information
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00FF66),
                                modifier = Modifier.size(52.dp)
                            )
                            Text("Artwork Exported Successfully!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                text = "Saved path:\n${savedFileResult?.absolutePath}",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (savedFileResult == null) {
                    Button(
                        onClick = {
                            viewModel.exportFinalImage(context, exportFormat, exportNameInput) { file ->
                                if (file != null) {
                                    savedFileResult = file
                                    viewModel.showToast("Saved locally!")
                                } else {
                                    viewModel.showToast("Export failed.")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start Export")
                    }
                } else {
                    Button(
                        onClick = {
                            showExportDialog = false
                            savedFileResult = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x25FFFFFF), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (savedFileResult == null) {
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Cancel", color = Color.LightGray)
                    }
                }
            }
        )
    }

    // 3. Save Custom Preset Dialog
    if (showPresetSaveDialog) {
        AlertDialog(
            onDismissRequest = { showPresetSaveDialog = false },
            containerColor = Color(0xFF14141F),
            modifier = Modifier.border(1.dp, Color(0x1F6366F1), RoundedCornerShape(20.dp)),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            title = { Text("Save Custom Preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Save your current set of adjustments as a persistent custom preset template.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = queryPresetName,
                        onValueChange = { queryPresetName = it },
                        label = { Text("Preset Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (queryPresetName.isNotBlank()) {
                            viewModel.saveCustomPreset(queryPresetName)
                            showPresetSaveDialog = false
                            queryPresetName = ""
                        } else {
                            viewModel.showToast("Preset name is required.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Preset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPresetSaveDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }

    // 4. Custom Text Overlay Input Dialog
    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            containerColor = Color(0xFF14141F),
            modifier = Modifier.border(1.dp, Color(0x1F6366F1), RoundedCornerShape(20.dp)),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            title = { Text("Add Text Overlay") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = textOverlayInput,
                        onValueChange = { textOverlayInput = it },
                        placeholder = { Text("Enter your text...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Text color rows
                    Column {
                        Text("Text Color", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val colors = listOf(
                                0xFFFFFFFFL to Color.White,
                                0xFFFFEB3BL to Color.Yellow,
                                0xFFFF5722L to Color(0xFFFF5722),
                                0xFF818CF8L to Color(0xFF818CF8),
                                0xFF00FF66L to Color(0xFF00FF66),
                                0xFFFF4081L to Color(0xFFFF4081)
                            )
                            colors.forEach { (longVal, col) ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (textOverlayColor == longVal) 2.dp else 0.dp,
                                            color = Color.LightGray,
                                            shape = CircleShape
                                        )
                                        .clickable { textOverlayColor = longVal }
                                )
                            }
                        }
                    }

                    // Text background choice
                    Column {
                        Text("Background Box Opacity", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            val bgOptions = listOf(
                                0x00000000L to "None",
                                0x66000000L to "Light Tint",
                                0xAA000000L to "Medium Tint",
                                0xFF000000L to "Solid Black"
                            )
                            bgOptions.forEach { (bgLong, lbl) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (textOverlayBgColor == bgLong) Color(0x336366F1) else Color(0x15FFFFFF))
                                        .border(width = 1.dp, color = if (textOverlayBgColor == bgLong) Color(0xFF818CF8) else Color(0x0EFFFFFF), shape = RoundedCornerShape(8.dp))
                                        .clickable { textOverlayBgColor = bgLong }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(lbl, fontSize = 10.sp, color = if (textOverlayBgColor == bgLong) Color(0xFF818CF8) else Color.White)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val text = textOverlayInput.trim()
                        if (text.isNotEmpty()) {
                            viewModel.addTextOverlay(text, textOverlayColor, textOverlayBgColor)
                            showAddTextDialog = false
                        } else {
                            viewModel.showToast("Text overlay cannot be empty")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add Overlay")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }
}

// ------------------ PRIMARY DYNAMIC PANELS COMPOSABLES ------------------

@Composable
fun PresetPanel(onSelect: (String) -> Unit) {
    val presetOptions = listOf(
        "Default" to Icons.Default.Refresh,
        "Golden Hour" to Icons.Default.WbSunny,
        "Nordic Chill" to Icons.Default.AcUnit,
        "Matte Retro" to Icons.Default.HistoryToggleOff,
        "Noir Drama" to Icons.Default.Brightness2,
        "Vivid Pop" to Icons.Default.FlashOn,
        "Warm Portrait" to Icons.Default.Face
    )

    Column {
        Text(
            "Studio Presets",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(presetOptions) { (name, icon) ->
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x15FFFFFF))
                        .border(1.dp, Color(0x0EFFFFFF), RoundedCornerShape(12.dp))
                        .clickable { onSelect(name) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

data class SliderConfig(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val value: Float,
    val copyFn: (Float) -> EditState
)

@Composable
fun AdjustPanel(
    state: EditState,
    onUpdate: ((EditState) -> EditState) -> Unit,
    onFinished: () -> Unit
) {
    // List of active sliders
    val adjustSliders = listOf(
        SliderConfig("Exposure", -100f..100f, state.exposure) { v: Float -> state.copy(exposure = v) },
        SliderConfig("Brightness", -100f..100f, state.brightness) { v: Float -> state.copy(brightness = v) },
        SliderConfig("Contrast", -100f..100f, state.contrast) { v: Float -> state.copy(contrast = v) },
        SliderConfig("Saturation", -100f..100f, state.saturation) { v: Float -> state.copy(saturation = v) },
        SliderConfig("Highlights", -100f..100f, state.highlights) { v: Float -> state.copy(highlights = v) },
        SliderConfig("Shadows", -100f..100f, state.shadows) { v: Float -> state.copy(shadows = v) },
        SliderConfig("Temperature", -100f..100f, state.temperature) { v: Float -> state.copy(temperature = v) },
        SliderConfig("Tint", -100f..100f, state.tint) { v: Float -> state.copy(tint = v) },
        SliderConfig("Sharpness", 0f..100f, state.sharpness) { v: Float -> state.copy(sharpness = v) },
        SliderConfig("Blur", 0f..100f, state.blur) { v: Float -> state.copy(blur = v) },
        SliderConfig("Vignette", 0f..100f, state.vignette) { v: Float -> state.copy(vignette = v) },
        SliderConfig("Background Blur", 0f..100f, state.backgroundBlur) { v: Float -> state.copy(backgroundBlur = v) }
    )

    Column {
        Text(
            "Intensity Adjustments",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Column(
            modifier = Modifier
                .heightIn(max = 160.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            adjustSliders.forEach { config ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = config.label,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(110.dp)
                    )
                    Slider(
                        value = config.value,
                        onValueChange = { floatValue -> onUpdate { config.copyFn(floatValue) } },
                        onValueChangeFinished = onFinished,
                        valueRange = config.range,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF818CF8),
                            activeTrackColor = Color(0xFF6366F1),
                            inactiveTrackColor = Color(0x33FFFFFF)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                    )
                    Text(
                        text = config.value.roundToInt().toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FilterPanel(activeFilter: String, onSelect: (String) -> Unit) {
    val filters = listOf(
        "None",
        "Vintage",
        "B&W",
        "Punchy",
        "Cinematic",
        "Warm",
        "Cool",
        "Chrome"
    )

    Column {
        Text(
            "Visual Filters",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filters) { fName ->
                val isSelected = activeFilter == fName
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0x336366F1) else Color(0x15FFFFFF))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFF818CF8) else Color(0x10FFFFFF),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelect(fName) }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fName,
                        color = if (isSelected) Color(0xFF818CF8) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CropPanel(state: EditState, onUpdate: ((EditState) -> EditState) -> Unit) {
    val crRatios = listOf("Original", "1:1", "4:3", "16:9", "2:3", "3:4")
    val frameBorders = listOf("None", "Classic White", "Studio Black", "Vintage Film", "Polaroid Border")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Rotations bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rotate & Flips:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Rot Left
                IconButton(onClick = {
                    onUpdate { s ->
                        val next = (s.rotationAngle - 90f) % 360f
                        s.copy(rotationAngle = if (next < 0) next + 360f else next)
                    }
                }, modifier = Modifier.size(36.dp).background(Color(0xFF202024), CircleShape)) {
                    Icon(imageVector = Icons.Default.RotateLeft, contentDescription = "Rotate Left", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                // Rot Right
                IconButton(onClick = {
                    onUpdate { s ->
                        s.copy(rotationAngle = (s.rotationAngle + 90f) % 360f)
                    }
                }, modifier = Modifier.size(36.dp).background(Color(0xFF202024), CircleShape)) {
                    Icon(imageVector = Icons.Default.RotateRight, contentDescription = "Rotate Right", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                // Flip X
                IconButton(onClick = {
                    onUpdate { s -> s.copy(flipHorizontal = !s.flipHorizontal) }
                }, modifier = Modifier.size(36.dp).background(Color(0xFF202024), CircleShape)) {
                    Icon(imageVector = Icons.Default.Flip, contentDescription = "Flip Horiz", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Aspect Crop list
        Column {
            Text("Crop Aspect Ratios", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(crRatios) { ratio ->
                    val isSelected = state.cropRatio == ratio
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0x336366F1) else Color(0x15FFFFFF))
                            .border(width = 1.dp, color = if (isSelected) Color(0xFF818CF8) else Color(0x10FFFFFF), shape = RoundedCornerShape(8.dp))
                            .clickable { onUpdate { s -> s.copy(cropRatio = ratio) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(ratio, fontSize = 11.sp, color = if (isSelected) Color(0xFF818CF8) else Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Frame Border Selection
        Column {
            Text("Aesthetic Borders & Frames", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(frameBorders) { frame ->
                    val isSelected = state.frameType == frame
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0x336366F1) else Color(0x15FFFFFF))
                            .border(width = 1.dp, color = if (isSelected) Color(0xFF818CF8) else Color(0x10FFFFFF), shape = RoundedCornerShape(8.dp))
                            .clickable { onUpdate { s -> s.copy(frameType = frame) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(frame, fontSize = 11.sp, color = if (isSelected) Color(0xFF818CF8) else Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun OverlayPanel(
    state: EditState,
    onAddText: () -> Unit,
    onAddSticker: (String) -> Unit,
    onCloseFocus: () -> Unit
) {
    val itemsEmoji = listOf("❤️", "✨", "🔥", "📸", "⚡", "🌟", "🌈", "🌸", "🌻", "😎", "🍿", "🎈", "🎨", "🚀", "🗺️", "📌")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Overlays Studio", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onCloseFocus) {
                Text("Deselect overlays", fontSize = 11.sp, color = Color.Gray)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Button(
                onClick = onAddText,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF), contentColor = Color.White),
                border = BorderStroke(1.dp, Color(0x10FFFFFF)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.TextFields, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Title Text", fontSize = 11.sp)
            }
        }

        Column {
            Text("Quick Decals & Stickers:", fontSize = 11.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(itemsEmoji) { sticker ->
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x20FFFFFF))
                            .clickable { onAddSticker(sticker) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(sticker, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MyPresetsPanel(
    savedPresets: List<PresetEntity>,
    onApply: (PresetEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    Column {
        Text("My Presets (Offline Database)", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        if (savedPresets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No custom presets saved. Click the bookmark icon in the top header to save yours!",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(savedPresets) { p ->
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1AFFFFFF))
                            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = p.name,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDelete(p.name) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.Red.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = { onApply(p) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1), contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.fillMaxWidth().height(26.dp)
                            ) {
                                Text("Apply Filter", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryPanel(
    recentEdits: List<RecentEditEntity>,
    onRestore: (RecentEditEntity) -> Unit,
    onClearAll: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent Artworks History", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            if (recentEdits.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear log", color = Color.Red, fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (recentEdits.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Your completed edits and exports will list here automatically.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recentEdits) { recent ->
                    Box(
                        modifier = Modifier
                            .width(135.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x15FFFFFF))
                            .border(1.dp, Color(0x0EFFFFFF), RoundedCornerShape(12.dp))
                            .clickable { onRestore(recent) }
                            .padding(8.dp)
                    ) {
                        Column {
                            val displayPathName = when {
                                recent.imagePath.contains("sunset") -> "🌅 Sunset Bliss"
                                recent.imagePath.contains("aurora") -> "🌌 Cosmic Aurora"
                                recent.imagePath.contains("sand") -> "🏜️ Sand Dunes"
                                recent.imagePath.contains("forest") -> "🌲 Misty Pines"
                                else -> "📷 Imported Photo"
                            }
                            Text(
                                text = displayPathName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Saved Preset: " + if (recent.presetName == "Default") "Sliders" else recent.presetName,
                                color = Color.LightGray,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Restore Session",
                                color = Color(0xFF818CF8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }
}
