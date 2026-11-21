package com.example.ui.drawing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.DrawTool
import com.example.ui.components.FrameThumbnailCanvas
import com.example.ui.components.LayerManagerBottomSheet
import com.example.ui.components.NeonColorPickerDialog
import com.example.ui.components.NeonPalettePresetBar
import com.example.ui.components.OnionSkinSettingsBottomSheet
import com.example.ui.components.RoninVectorCanvas
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnionAfterCyan
import com.example.ui.theme.OnionBeforeRose
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import kotlin.math.roundToInt

@Composable
fun DrawingStudioScreen(
    viewModel: DrawingViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val project = state.project
    val frames = project?.frames.orEmpty()
    val currentFrame = frames.getOrNull(state.currentFrameIndex)
    val layers = currentFrame?.layers.orEmpty()
    val activeLayer = layers.getOrNull(state.activeLayerIndex)

    val snackbarHostState = remember { SnackbarHostState() }
    var showLayersSheet by remember { mutableStateOf(false) }
    var showOnionSheet by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val onionEnabled = project?.onionSkin?.enabled == true
            StudioTopHeader(
                title = project?.title ?: "Drawing Studio",
                subtitle = "Sheet ${state.currentFrameIndex + 1}/${frames.size.coerceAtLeast(1)} • ${layers.size} Layers • ${if (onionEnabled) "ONION ON" else "ONION OFF"}",
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.undoStack.isNotEmpty()) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = stringResource(R.string.cd_undo),
                            tint = if (state.undoStack.isNotEmpty()) TextPrimary else TextMuted
                        )
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.redoStack.isNotEmpty()) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Redo,
                            contentDescription = stringResource(R.string.cd_redo),
                            tint = if (state.redoStack.isNotEmpty()) TextPrimary else TextMuted
                        )
                    }
                    IconButton(
                        onClick = viewModel::toggleOnionSkin,
                        modifier = Modifier.testTag("drawing_onion_topbar_toggle")
                    ) {
                        Icon(
                            Icons.Rounded.FilterNone,
                            contentDescription = stringResource(R.string.cd_onion_skin),
                            tint = if (onionEnabled) NeonPurpleBright else TextMuted
                        )
                    }
                    IconButton(onClick = { showLayersSheet = true }, modifier = Modifier.testTag("drawing_layers_button")) {
                        Icon(Icons.Rounded.Layers, contentDescription = "Layers", tint = NeonPurpleBright)
                    }
                    IconButton(onClick = viewModel::exportPngSnapshot, modifier = Modifier.testTag("drawing_export_png_button")) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = "Save PNG", tint = CyberCyan)
                    }
                }
            )

            // Active Layer + Independent Onion Skin Previous/Next Toggles + Grid Bar
            if (project != null) {
                val onion = project.onionSkin
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { showLayersSheet = true },
                        shape = RoundedCornerShape(8.dp),
                        color = StudioSurfaceElevated,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.testTag("drawing_active_layer_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Rounded.Layers, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(14.dp))
                            Text(
                                text = "${activeLayer?.name ?: "Layer"} (${((activeLayer?.opacity ?: 1f) * 100).roundToInt()}%)",
                                style = StudioMonoStyle,
                                color = TextPrimary
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Independent Previous Frames Overlay Toggle
                        val prevActive = onion.enabled && onion.showBefore
                        Surface(
                            onClick = viewModel::toggleShowPreviousFrames,
                            shape = RoundedCornerShape(8.dp),
                            color = if (prevActive) OnionBeforeRose.copy(alpha = 0.22f) else StudioSurfaceElevated,
                            border = BorderStroke(1.dp, if (prevActive) OnionBeforeRose else StudioBorder),
                            modifier = Modifier.testTag("drawing_onion_prev_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (prevActive) OnionBeforeRose else TextMuted)
                                )
                                Text(
                                    text = "Prev",
                                    style = StudioMonoStyle,
                                    color = if (prevActive) OnionBeforeRose else TextMuted
                                )
                            }
                        }

                        // Independent Next Frames Overlay Toggle
                        val nextActive = onion.enabled && onion.showAfter
                        Surface(
                            onClick = viewModel::toggleShowNextFrames,
                            shape = RoundedCornerShape(8.dp),
                            color = if (nextActive) OnionAfterCyan.copy(alpha = 0.22f) else StudioSurfaceElevated,
                            border = BorderStroke(1.dp, if (nextActive) OnionAfterCyan else StudioBorder),
                            modifier = Modifier.testTag("drawing_onion_next_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (nextActive) OnionAfterCyan else TextMuted)
                                )
                                Text(
                                    text = "Next",
                                    style = StudioMonoStyle,
                                    color = if (nextActive) OnionAfterCyan else TextMuted
                                )
                            }
                        }

                        // Onion Skin Settings Button
                        Surface(
                            onClick = { showOnionSheet = true },
                            shape = RoundedCornerShape(8.dp),
                            color = StudioSurfaceElevated,
                            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.55f)),
                            modifier = Modifier.testTag("drawing_onion_settings_button")
                        ) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = "Onion skin settings",
                                tint = NeonPurpleBright,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }

                        // Grid Toggle
                        Surface(
                            onClick = viewModel::toggleGrid,
                            shape = RoundedCornerShape(8.dp),
                            color = if (state.showGrid) CyberCyan.copy(alpha = 0.16f) else StudioSurfaceElevated,
                            border = BorderStroke(1.dp, if (state.showGrid) CyberCyan else StudioBorder)
                        ) {
                            Icon(
                                Icons.Rounded.GridOn,
                                contentDescription = "Toggle grid",
                                tint = if (state.showGrid) CyberCyan else TextMuted,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }
            }

            // Main Canvas with Onion Skinning Overlays
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (project != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, NeonPurple.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                    ) {
                        RoninVectorCanvas(
                            frames = project.frames,
                            currentFrameIndex = state.currentFrameIndex,
                            activeLayerIndex = state.activeLayerIndex,
                            activeStroke = state.activeStroke,
                            onionSkin = project.onionSkin,
                            isPlaying = false,
                            showGrid = state.showGrid,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("drawing_studio_canvas")
                                .onSizeChanged { canvasSize = it }
                                .pointerInput(state.currentFrameIndex, state.activeLayerIndex, state.selectedTool, state.selectedColorArgb, state.strokeWidthNorm) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                                            viewModel.startStroke(offset.x / w, offset.y / h)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                                            viewModel.appendStrokePoint(change.position.x / w, change.position.y / h)
                                        },
                                        onDragEnd = { viewModel.finishStroke() },
                                        onDragCancel = { viewModel.finishStroke() }
                                    )
                                }
                                .pointerInput(state.currentFrameIndex, state.activeLayerIndex, state.selectedTool) {
                                    detectTapGestures { offset ->
                                        val w = canvasSize.width.coerceAtLeast(1).toFloat()
                                        val h = canvasSize.height.coerceAtLeast(1).toFloat()
                                        viewModel.startStroke(offset.x / w, offset.y / h)
                                        viewModel.finishStroke()
                                    }
                                }
                        )
                    }
                }
            }

            // Sketchbook Multi-Frame Strip with Onion Skin Window Badges
            if (project != null) {
                Surface(
                    color = ObsidianBg,
                    border = BorderStroke(1.dp, StudioBorder.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.stepFrame(-1) },
                            enabled = frames.size > 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous frame",
                                tint = if (frames.size > 1) TextPrimary else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("drawing_frame_strip")
                        ) {
                            itemsIndexed(frames, key = { _, f -> f.id }) { idx, frame ->
                                val isCurrent = idx == state.currentFrameIndex
                                val delta = idx - state.currentFrameIndex
                                val onion = project.onionSkin
                                val isOnionBefore = onion.enabled && onion.showBefore && delta in -onion.beforeCount..-1
                                val isOnionAfter = onion.enabled && onion.showAfter && delta in 1..onion.afterCount

                                val borderColor = when {
                                    isCurrent -> NeonPurple
                                    isOnionBefore -> OnionBeforeRose
                                    isOnionAfter -> OnionAfterCyan
                                    else -> StudioBorder
                                }

                                Surface(
                                    onClick = { viewModel.selectFrame(idx) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(if (isCurrent || isOnionBefore || isOnionAfter) 1.5.dp else 1.dp, borderColor),
                                    color = StudioSurface,
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(44.dp)
                                        .testTag("drawing_frame_$idx")
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        FrameThumbnailCanvas(frame = frame, modifier = Modifier.fillMaxSize())
                                        Surface(
                                            shape = RoundedCornerShape(bottomEnd = 5.dp),
                                            color = if (isCurrent) NeonPurple else VoidBlack.copy(alpha = 0.85f),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                style = StudioMonoStyle,
                                                color = if (isCurrent) VoidBlack else TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                        }
                                        if (isOnionBefore || isOnionAfter) {
                                            Surface(
                                                shape = RoundedCornerShape(topStart = 5.dp),
                                                color = if (isOnionBefore) OnionBeforeRose else OnionAfterCyan,
                                                modifier = Modifier.align(Alignment.BottomEnd)
                                            ) {
                                                Text(
                                                    text = if (delta > 0) "+$delta" else "$delta",
                                                    style = StudioMonoStyle,
                                                    color = VoidBlack,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { viewModel.stepFrame(1) },
                            enabled = frames.size > 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                contentDescription = "Next frame",
                                tint = if (frames.size > 1) TextPrimary else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(onClick = viewModel::duplicateCurrentFrame, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate sheet", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = viewModel::deleteCurrentFrame,
                            enabled = frames.size > 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete sheet",
                                tint = if (frames.size > 1) OnionBeforeRose else TextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        Surface(
                            onClick = viewModel::addBlankFrameAfterCurrent,
                            shape = RoundedCornerShape(8.dp),
                            color = NeonPurple,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("drawing_add_frame_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_add_frame), tint = VoidBlack, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Brush, Shapes, Stroke Size & Color Dock
            Surface(
                color = StudioSurface,
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val toolItems = listOf(
                        DrawTool.PEN to Icons.Rounded.Edit,
                        DrawTool.INK_BRUSH to Icons.Rounded.Brush,
                        DrawTool.MARKER to Icons.Rounded.AutoFixNormal,
                        DrawTool.ERASER to Icons.Rounded.FilterNone,
                        DrawTool.LINE to Icons.Rounded.HorizontalRule,
                        DrawTool.RECT to Icons.Rounded.CropSquare,
                        DrawTool.CIRCLE to Icons.Rounded.Circle
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(toolItems) { (tool, icon) ->
                            val active = tool == state.selectedTool
                            Surface(
                                onClick = { viewModel.selectTool(tool) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) NeonPurple.copy(alpha = 0.24f) else StudioSurfaceElevated,
                                border = BorderStroke(1.dp, if (active) NeonPurple else StudioBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(icon, contentDescription = tool.label, tint = if (active) NeonPurpleBright else TextSecondary, modifier = Modifier.size(16.dp))
                                    Text(tool.label, style = MaterialTheme.typography.labelMedium, color = if (active) TextPrimary else TextSecondary)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Size", style = StudioMonoStyle, color = TextSecondary)
                        Slider(
                            value = state.strokeWidthNorm,
                            onValueChange = viewModel::setStrokeWidth,
                            valueRange = 0.003f..0.035f,
                            colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    NeonPalettePresetBar(
                        selectedColorArgb = state.selectedColorArgb,
                        favoriteColors = state.favoriteNeonColors,
                        presets = state.palettePresets,
                        activePresetId = state.activePalettePresetId,
                        onSelectColor = viewModel::selectColor,
                        onSelectPreset = viewModel::selectPalettePreset,
                        onToggleSaveFavorite = viewModel::toggleSaveFavoriteColor,
                        onOpenColorPicker = viewModel::openColorPicker
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showLayersSheet && layers.isNotEmpty()) {
        LayerManagerBottomSheet(
            layers = layers,
            activeLayerIndex = state.activeLayerIndex,
            showTimelineSyncAction = false,
            onSelectLayer = viewModel::selectLayer,
            onAddLayer = viewModel::addLayer,
            onDeleteLayer = viewModel::deleteLayer,
            onRenameLayer = viewModel::renameLayer,
            onMoveLayerUp = viewModel::moveLayerUp,
            onMoveLayerDown = viewModel::moveLayerDown,
            onMergeLayerDown = viewModel::mergeLayerDown,
            onToggleVisibility = viewModel::toggleLayerVisibility,
            onToggleLock = viewModel::toggleLayerLock,
            onChangeOpacity = viewModel::changeLayerOpacity,
            onDismiss = { showLayersSheet = false }
        )
    }

    if (showOnionSheet && project != null) {
        OnionSkinSettingsBottomSheet(
            config = project.onionSkin,
            onUpdate = viewModel::updateOnionSkin,
            onDismiss = { showOnionSheet = false }
        )
    }

    if (state.isColorPickerOpen) {
        NeonColorPickerDialog(
            initialColorArgb = state.selectedColorArgb,
            favoriteColors = state.favoriteNeonColors,
            presets = state.palettePresets,
            activePresetId = state.activePalettePresetId,
            onSelectPreset = viewModel::selectPalettePreset,
            onSaveFavoriteColor = viewModel::saveFavoriteNeonColor,
            onRemoveFavoriteColor = viewModel::removeFavoriteNeonColor,
            onSaveCustomPreset = viewModel::saveCustomPalettePreset,
            onDeleteCustomPreset = viewModel::deleteCustomPalettePreset,
            onResetDefaults = viewModel::resetDefaultNeonColors,
            onApplyColor = viewModel::selectColor,
            onDismiss = viewModel::closeColorPicker
        )
    }
}
