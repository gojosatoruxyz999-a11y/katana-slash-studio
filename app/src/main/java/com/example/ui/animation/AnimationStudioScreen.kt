package com.example.ui.animation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.NeonPalettePreset
import com.example.data.model.DrawTool
import com.example.data.model.OnionSkinConfig
import com.example.data.model.StudioLayer
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
fun AnimationStudioScreen(
    viewModel: AnimationViewModel,
    onBack: () -> Unit,
    onExportProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val project = state.project
    val snackbarHostState = remember { SnackbarHostState() }

    var showLayersSheet by remember { mutableStateOf(false) }
    var showLayersPopover by remember { mutableStateOf(false) }
    var showOnionSheet by remember { mutableStateOf(false) }
    var showFpsSheet by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    BackHandler(enabled = state.isFullScreenCanvas) {
        viewModel.toggleFullScreenCanvas()
    }

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
            // Top Studio Header (hidden in Full-Screen Canvas mode)
            if (!state.isFullScreenCanvas) {
                val frameTotal = project?.frames?.size ?: 0
                val curFrameNum = state.currentFrameIndex + 1
                StudioTopHeader(
                    title = project?.title ?: "Katana Slash Canvas",
                    subtitle = "F$curFrameNum/$frameTotal • ${project?.fps ?: 8} FPS • ${if (project?.onionSkin?.enabled == true) "ONION ON" else "ONION OFF"}",
                    onBack = {
                        viewModel.stopPlayback()
                        onBack()
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::undo,
                            enabled = state.undoStack.isNotEmpty(),
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("undo_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = stringResource(R.string.cd_undo),
                                tint = if (state.undoStack.isNotEmpty()) TextPrimary else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = viewModel::redo,
                            enabled = state.redoStack.isNotEmpty(),
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("redo_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Redo,
                                contentDescription = stringResource(R.string.cd_redo),
                                tint = if (state.redoStack.isNotEmpty()) TextPrimary else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = viewModel::toggleFullScreenCanvas,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("fullscreen_canvas_toggle")
                        ) {
                            Icon(
                                Icons.Rounded.Fullscreen,
                                contentDescription = "Toggle full-screen canvas",
                                tint = TextSecondary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        if (project != null) {
                            Surface(
                                onClick = viewModel::openExportSaveModal,
                                shape = RoundedCornerShape(10.dp),
                                color = NeonPurple.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, NeonPurpleBright.copy(alpha = 0.75f)),
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .testTag("export_save_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Outbox,
                                        contentDescription = "Export or Save",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Export / Save",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                )

                // Active Layer Popover Anchor + Quick 8/12/24 FPS + Onion Skin + Grid Strip
                if (project != null) {
                    val curFrame = project.frames.getOrNull(state.currentFrameIndex)
                    val activeLayer = curFrame?.layers?.getOrNull(state.activeLayerIndex)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Layers Popover Trigger
                        Box {
                            Surface(
                                onClick = { showLayersPopover = true },
                                shape = RoundedCornerShape(8.dp),
                                color = StudioSurfaceElevated,
                                border = BorderStroke(1.dp, StudioBorder),
                                modifier = Modifier.testTag("active_layer_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Layers,
                                        contentDescription = "Layers popover",
                                        tint = NeonPurpleBright,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = activeLayer?.name ?: "Layer",
                                        style = StudioMonoStyle,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            if (curFrame != null) {
                                LayersQuickPopover(
                                    expanded = showLayersPopover,
                                    layers = curFrame.layers,
                                    activeLayerIndex = state.activeLayerIndex,
                                    onDismiss = { showLayersPopover = false },
                                    onSelectLayer = { idx ->
                                        viewModel.selectLayer(idx)
                                        showLayersPopover = false
                                    },
                                    onToggleVisibility = viewModel::toggleLayerVisibility,
                                    onEnsureStandardLayers = viewModel::ensureStandardLayers,
                                    onOpenFullManager = {
                                        showLayersPopover = false
                                        showLayersSheet = true
                                    }
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // Quick 8 / 12 / 24 FPS Pills
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StudioSurfaceElevated,
                                border = BorderStroke(1.dp, StudioBorder)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    listOf(8, 12, 24).forEach { fpsOpt ->
                                        val active = project.fps == fpsOpt
                                        Surface(
                                            onClick = { viewModel.setFps(fpsOpt) },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (active) CyberCyan.copy(alpha = 0.22f) else Color.Transparent,
                                            modifier = Modifier.testTag("fps_quick_$fpsOpt")
                                        ) {
                                            Text(
                                                text = "$fpsOpt",
                                                style = StudioMonoStyle,
                                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                                color = if (active) CyberCyan else TextMuted,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (project.onionSkin.enabled) {
                                Surface(
                                    onClick = { showOnionSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = StudioSurfaceElevated,
                                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
                                    modifier = Modifier.testTag("onion_legend_pill")
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
                                                .background(if (project.onionSkin.showBefore) OnionBeforeRose else TextMuted)
                                        )
                                        Text(
                                            text = if (project.onionSkin.showBefore) "-${project.onionSkin.beforeCount}" else "-0",
                                            style = StudioMonoStyle,
                                            color = if (project.onionSkin.showBefore) OnionBeforeRose else TextMuted
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (project.onionSkin.showAfter) OnionAfterCyan else TextMuted)
                                        )
                                        Text(
                                            text = if (project.onionSkin.showAfter) "+${project.onionSkin.afterCount}" else "+0",
                                            style = StudioMonoStyle,
                                            color = if (project.onionSkin.showAfter) OnionAfterCyan else TextMuted
                                        )
                                    }
                                }
                            }

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
            }

            // Main Drawing Stage (16:10 anime cel viewport)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(VoidBlack)
                    .padding(horizontal = if (state.isFullScreenCanvas) 4.dp else 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (project != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (state.isPlaying) CyberCyan else NeonPurple.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                    ) {
                        RoninVectorCanvas(
                            frames = project.frames,
                            currentFrameIndex = state.currentFrameIndex,
                            activeLayerIndex = state.activeLayerIndex,
                            activeStroke = state.activeStroke,
                            onionSkin = project.onionSkin,
                            isPlaying = state.isPlaying,
                            showGrid = state.showGrid,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("animation_drawing_canvas")
                                .onSizeChanged { canvasSize = it }
                                .pointerInput(
                                    state.currentFrameIndex,
                                    state.activeLayerIndex,
                                    state.selectedTool,
                                    state.selectedColorArgb,
                                    state.strokeWidthNorm,
                                    state.brushOpacity,
                                    state.glowIntensity,
                                    state.isPlaying
                                ) {
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
                                .pointerInput(
                                    state.currentFrameIndex,
                                    state.activeLayerIndex,
                                    state.selectedTool,
                                    state.selectedColorArgb,
                                    state.strokeWidthNorm,
                                    state.brushOpacity,
                                    state.glowIntensity
                                ) {
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

            if (!state.isFullScreenCanvas) {
                // Studio Brush Dock + Neon Palette Preset Bar + Expandable Brush Settings Panel
                ToolAndBrushEngineDock(
                    selectedTool = state.selectedTool,
                    selectedColorArgb = state.selectedColorArgb,
                    favoriteColors = state.favoriteNeonColors,
                    palettePresets = state.palettePresets,
                    activePresetId = state.activePalettePresetId,
                    brushSizePx = state.brushSizePx,
                    brushOpacity = state.brushOpacity,
                    glowIntensity = state.glowIntensity,
                    isBrushSettingsExpanded = state.isBrushSettingsExpanded,
                    onSelectTool = viewModel::selectTool,
                    onSelectColor = viewModel::selectColor,
                    onSelectPreset = viewModel::selectPalettePreset,
                    onToggleSaveFavorite = viewModel::toggleSaveFavoriteColor,
                    onOpenColorPicker = viewModel::openColorPicker,
                    onToggleBrushSettings = viewModel::toggleBrushSettingsExpanded,
                    onBrushSizeChange = viewModel::setBrushSizePx,
                    onBrushOpacityChange = viewModel::setBrushOpacity,
                    onGlowIntensityChange = viewModel::setGlowIntensity
                )

                // Integrated Timeline & Onion Skin Controls Dock
                if (project != null) {
                    TimelineAndOnionDock(
                        frames = project.frames,
                        currentFrameIndex = state.currentFrameIndex,
                        fps = project.fps,
                        isPlaying = state.isPlaying,
                        onionSkin = project.onionSkin,
                        onSelectFrame = viewModel::selectFrame,
                        onStepFrame = viewModel::stepFrame,
                        onTogglePlay = viewModel::togglePlayback,
                        onToggleOnionSkin = viewModel::toggleOnionSkin,
                        onOpenOnionSettings = { showOnionSheet = true },
                        onOpenFpsSelector = { showFpsSheet = true },
                        onAddFrame = viewModel::addBlankFrameAfterCurrent,
                        onDuplicateFrame = viewModel::duplicateCurrentFrame,
                        onDeleteFrame = viewModel::deleteCurrentFrame,
                        onToggleHold = viewModel::toggleCurrentFrameHold
                    )
                }
            }
        }

        // Full-Screen Floating Overlay Controls
        if (state.isFullScreenCanvas) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = viewModel::undo,
                    enabled = state.undoStack.isNotEmpty(),
                    shape = CircleShape,
                    color = StudioSurface.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = stringResource(R.string.cd_undo),
                            tint = if (state.undoStack.isNotEmpty()) TextPrimary else TextMuted
                        )
                    }
                }
                Surface(
                    onClick = viewModel::redo,
                    enabled = state.redoStack.isNotEmpty(),
                    shape = CircleShape,
                    color = StudioSurface.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Redo,
                            contentDescription = stringResource(R.string.cd_redo),
                            tint = if (state.redoStack.isNotEmpty()) TextPrimary else TextMuted
                        )
                    }
                }
                Surface(
                    onClick = viewModel::toggleFullScreenCanvas,
                    shape = RoundedCornerShape(14.dp),
                    color = NeonPurple.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, NeonPurpleBright),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("fullscreen_exit_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit full screen", tint = VoidBlack, modifier = Modifier.size(18.dp))
                        Text("Exit Full Screen", style = MaterialTheme.typography.labelLarge, color = VoidBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    // Export / Save Studio Modal
    if (state.exportModal.isOpen && project != null) {
        StudioExportSaveModal(
            projectTitle = project.title,
            frameCount = project.frames.size,
            modalState = state.exportModal,
            onDismiss = viewModel::closeExportSaveModal,
            onBuildProjectJson = viewModel::buildProjectJsonBundle,
            onSaveProjectLocally = viewModel::saveProjectJsonLocally,
            onSavedToUri = viewModel::reportProjectJsonSavedToUri,
            onLoadProjectJson = viewModel::loadProjectFromJson,
            onToggleTransparentPng = viewModel::setTransparentPngBackground,
            onExportPng = viewModel::exportCurrentFramePng,
            onSelectFormat = viewModel::setExportFormat,
            onSelectExportFps = viewModel::setExportFps,
            onToggleOutro = viewModel::setIncludeOutro,
            onOutroTextChange = viewModel::setOutroCreatorText,
            onExportAnimation = viewModel::exportAnimationMedia,
            onOpenFullExportHub = { onExportProject(project.id) }
        )
    }

    // Full Layer Manager Sheet
    val currentFrame = project?.frames?.getOrNull(state.currentFrameIndex)
    if (showLayersSheet && currentFrame != null) {
        LayerManagerBottomSheet(
            layers = currentFrame.layers,
            activeLayerIndex = state.activeLayerIndex,
            showTimelineSyncAction = (project.frames.size > 1),
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
            onSyncLayerStructureAcrossFrames = viewModel::syncLayerStructureAcrossFrames,
            onDismiss = { showLayersSheet = false }
        )
    }

    // Onion Skin Settings Sheet
    if (showOnionSheet && project != null) {
        OnionSkinSettingsBottomSheet(
            config = project.onionSkin,
            onUpdate = viewModel::updateOnionSkin,
            onDismiss = { showOnionSheet = false }
        )
    }

    // FPS Selector Sheet
    if (showFpsSheet && project != null) {
        FpsSelectorSheet(
            currentFps = project.fps,
            onSelectFps = {
                viewModel.setFps(it)
                showFpsSheet = false
            },
            onDismiss = { showFpsSheet = false }
        )
    }

    // Customizable Neon Color Picker Dialog
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

@Composable
private fun LayersQuickPopover(
    expanded: Boolean,
    layers: List<StudioLayer>,
    activeLayerIndex: Int,
    onDismiss: () -> Unit,
    onSelectLayer: (Int) -> Unit,
    onToggleVisibility: (Int) -> Unit,
    onEnsureStandardLayers: () -> Unit,
    onOpenFullManager: () -> Unit
) {
    val hasStandardNames = layers.any { it.name.contains("Line", true) } &&
        layers.any { it.name.contains("FX", true) } &&
        layers.any { it.name.contains("Background", true) || it.name.contains("Guide", true) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(StudioSurfaceElevated)
            .testTag("layers_popover_menu")
    ) {
        Text(
            text = "FRAME LAYERS",
            style = StudioMonoStyle,
            color = CyberCyan,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
        layers.indices.reversed().forEach { idx ->
            val layer = layers[idx]
            val isActive = idx == activeLayerIndex
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Rounded.Check else Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = if (isActive) NeonPurpleBright else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = layer.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) TextPrimary else TextSecondary
                            )
                            Text(
                                text = "${(layer.opacity * 100).roundToInt()}% opacity • ${layer.strokes.size} strokes",
                                style = StudioMonoStyle,
                                color = TextMuted
                            )
                        }
                        IconButton(
                            onClick = { onToggleVisibility(idx) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (layer.isVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = "Toggle ${layer.name} visibility",
                                tint = if (layer.isVisible) CyberCyan else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                onClick = { onSelectLayer(idx) }
            )
        }
        if (!hasStandardNames) {
            HorizontalDivider(color = StudioBorder.copy(alpha = 0.6f))
            DropdownMenuItem(
                text = {
                    Text(
                        text = "+ Add Line Art / FX / Background",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyberCyan
                    )
                },
                onClick = {
                    onEnsureStandardLayers()
                    onDismiss()
                }
            )
        }
        HorizontalDivider(color = StudioBorder.copy(alpha = 0.6f))
        DropdownMenuItem(
            text = {
                Text(
                    text = "Full Layer Stack & Merge...",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonPurpleBright,
                    fontWeight = FontWeight.SemiBold
                )
            },
            onClick = onOpenFullManager,
            modifier = Modifier.testTag("open_layers_button")
        )
    }
}

@Composable
private fun ToolAndBrushEngineDock(
    selectedTool: DrawTool,
    selectedColorArgb: Long,
    favoriteColors: List<Long>,
    palettePresets: List<NeonPalettePreset>,
    activePresetId: String,
    brushSizePx: Float,
    brushOpacity: Float,
    glowIntensity: Float,
    isBrushSettingsExpanded: Boolean,
    onSelectTool: (DrawTool) -> Unit,
    onSelectColor: (Long) -> Unit,
    onSelectPreset: (String) -> Unit,
    onToggleSaveFavorite: (Long) -> Unit,
    onOpenColorPicker: () -> Unit,
    onToggleBrushSettings: () -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onBrushOpacityChange: (Float) -> Unit,
    onGlowIntensityChange: (Float) -> Unit
) {
    val toolIcons: List<Pair<DrawTool, ImageVector>> = listOf(
        DrawTool.KATANA_BLADE to Icons.Rounded.FlashOn,
        DrawTool.NEON_AURA to Icons.Rounded.AutoAwesome,
        DrawTool.SOFT_AIRBRUSH to Icons.Rounded.BlurOn,
        DrawTool.PEN to Icons.Rounded.Edit,
        DrawTool.INK_BRUSH to Icons.Rounded.Brush,
        DrawTool.MARKER to Icons.Rounded.AutoFixNormal,
        DrawTool.ERASER to Icons.Rounded.FilterNone,
        DrawTool.LINE to Icons.Rounded.HorizontalRule,
        DrawTool.RECT to Icons.Rounded.CropSquare,
        DrawTool.CIRCLE to Icons.Rounded.Circle
    )

    Surface(
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            // Brushes & Studio Tools Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(toolIcons) { _, (tool, icon) ->
                    val active = tool == selectedTool
                    Surface(
                        onClick = { onSelectTool(tool) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) NeonPurple.copy(alpha = 0.24f) else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (active) NeonPurple else StudioBorder),
                        modifier = Modifier.testTag("tool_${tool.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tool.label,
                                tint = if (active) NeonPurpleBright else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = tool.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Customizable Neon Palette Preset Bar + Brush Settings Toggle Pill
            NeonPalettePresetBar(
                selectedColorArgb = selectedColorArgb,
                favoriteColors = favoriteColors,
                presets = palettePresets,
                activePresetId = activePresetId,
                onSelectColor = onSelectColor,
                onSelectPreset = onSelectPreset,
                onToggleSaveFavorite = onToggleSaveFavorite,
                onOpenColorPicker = onOpenColorPicker,
                trailingContent = {
                    Surface(
                        onClick = onToggleBrushSettings,
                        shape = RoundedCornerShape(10.dp),
                        color = if (isBrushSettingsExpanded) CyberCyan.copy(alpha = 0.18f) else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (isBrushSettingsExpanded) CyberCyan else StudioBorder),
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .testTag("brush_settings_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            LiveBrushPreviewCircle(
                                brushSizePx = brushSizePx,
                                brushOpacity = brushOpacity,
                                glowIntensity = glowIntensity,
                                colorArgb = selectedColorArgb,
                                isEraser = (selectedTool == DrawTool.ERASER),
                                boxSizeDp = 20
                            )
                            Text(
                                text = "${brushSizePx.roundToInt()}px • ${(brushOpacity * 100).roundToInt()}%",
                                style = StudioMonoStyle,
                                color = if (isBrushSettingsExpanded) CyberCyan else TextPrimary
                            )
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Brush Settings",
                                tint = if (isBrushSettingsExpanded) CyberCyan else NeonPurpleBright,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            )

            // Expandable Studio Brush Settings Panel (Size, Opacity, Glow + Live Preview)
            AnimatedVisibility(
                visible = isBrushSettingsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianBg,
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("studio_brush_settings_panel")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Live Circular Brush Preview Indicator
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = VoidBlack,
                                border = BorderStroke(1.dp, StudioBorder),
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("brush_live_preview")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    LiveBrushPreviewCircle(
                                        brushSizePx = brushSizePx,
                                        brushOpacity = brushOpacity,
                                        glowIntensity = glowIntensity,
                                        colorArgb = selectedColorArgb,
                                        isEraser = (selectedTool == DrawTool.ERASER),
                                        boxSizeDp = 48
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${selectedTool.label} Studio Settings",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Size ${brushSizePx.roundToInt()}px • Opacity ${(brushOpacity * 100).roundToInt()}% • Glow ${(glowIntensity * 100).roundToInt()}%",
                                    style = StudioMonoStyle,
                                    color = CyberCyan
                                )
                            }
                        }

                        // Brush Size Slider (1px - 60px)
                        BrushSliderRow(
                            label = "Size",
                            valueText = "${brushSizePx.roundToInt()}px",
                            value = brushSizePx,
                            valueRange = 1f..60f,
                            accentColor = CyberCyan,
                            testTag = "brush_size_slider",
                            onValueChange = onBrushSizeChange
                        )

                        // Opacity Slider (10% - 100%)
                        BrushSliderRow(
                            label = "Opacity",
                            valueText = "${(brushOpacity * 100).roundToInt()}%",
                            value = brushOpacity,
                            valueRange = 0.10f..1.0f,
                            accentColor = NeonPurpleBright,
                            testTag = "brush_opacity_slider",
                            onValueChange = onBrushOpacityChange
                        )

                        // Glow Intensity Slider (0% - 100%)
                        BrushSliderRow(
                            label = "Glow",
                            valueText = "${(glowIntensity * 100).roundToInt()}%",
                            value = glowIntensity,
                            valueRange = 0f..1f,
                            accentColor = NeonPurple,
                            testTag = "brush_glow_slider",
                            onValueChange = onGlowIntensityChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrushSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = StudioMonoStyle,
            color = TextSecondary,
            modifier = Modifier.width(54.dp)
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = StudioSurfaceElevated
            ),
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .testTag(testTag)
        )
        Text(
            text = valueText,
            style = StudioMonoStyle,
            color = accentColor,
            modifier = Modifier.width(42.dp)
        )
    }
}

@Composable
private fun LiveBrushPreviewCircle(
    brushSizePx: Float,
    brushOpacity: Float,
    glowIntensity: Float,
    colorArgb: Long,
    isEraser: Boolean,
    boxSizeDp: Int
) {
    val baseColor = if (isEraser) Color(0xFF94A3B8) else Color(colorArgb.toInt())
    Canvas(modifier = Modifier.size(boxSizeDp.dp)) {
        val maxRadius = size.minDimension * 0.44f
        val norm = ((brushSizePx - 1f) / 59f).coerceIn(0f, 1f)
        val coreRadius = (2f + norm * (maxRadius - 2f)).coerceAtLeast(2f)

        if (!isEraser && glowIntensity > 0.02f) {
            val haloRadius = (coreRadius * (1.35f + glowIntensity * 0.9f)).coerceAtMost(size.minDimension * 0.49f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        baseColor.copy(alpha = (brushOpacity * glowIntensity * 0.65f).coerceIn(0f, 0.85f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = center
            )
        }
        drawCircle(
            color = baseColor.copy(alpha = brushOpacity.coerceIn(0.1f, 1f)),
            radius = coreRadius,
            center = center
        )
    }
}

@Composable
private fun TimelineAndOnionDock(
    frames: List<com.example.data.model.AnimationFrame>,
    currentFrameIndex: Int,
    fps: Int,
    isPlaying: Boolean,
    onionSkin: OnionSkinConfig,
    onSelectFrame: (Int) -> Unit,
    onStepFrame: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleOnionSkin: () -> Unit,
    onOpenOnionSettings: () -> Unit,
    onOpenFpsSelector: () -> Unit,
    onAddFrame: () -> Unit,
    onDuplicateFrame: () -> Unit,
    onDeleteFrame: () -> Unit,
    onToggleHold: () -> Unit
) {
    Surface(
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // Transport & Onion Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = { onStepFrame(-1) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous frame", tint = TextPrimary)
                    }
                    Surface(
                        onClick = onTogglePlay,
                        shape = CircleShape,
                        color = if (isPlaying) CyberCyan else NeonPurple,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause),
                                tint = VoidBlack
                            )
                        }
                    }
                    IconButton(onClick = { onStepFrame(1) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next frame", tint = TextPrimary)
                    }

                    Spacer(Modifier.width(4.dp))

                    Surface(
                        onClick = onOpenFpsSelector,
                        shape = RoundedCornerShape(8.dp),
                        color = StudioSurfaceElevated,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.testTag("fps_selector_button")
                    ) {
                        Text(
                            text = "${fps}FPS",
                            style = StudioMonoStyle,
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                // Onion Skin Quick Toggle + Settings + Frame Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = onToggleOnionSkin,
                        shape = RoundedCornerShape(8.dp),
                        color = if (onionSkin.enabled) NeonPurple.copy(alpha = 0.24f) else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (onionSkin.enabled) NeonPurple else StudioBorder),
                        modifier = Modifier.testTag("onion_skin_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FilterNone,
                                contentDescription = stringResource(R.string.cd_onion_skin),
                                tint = if (onionSkin.enabled) NeonPurpleBright else TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (onionSkin.enabled) "ONION" else "OFF",
                                style = StudioMonoStyle,
                                color = if (onionSkin.enabled) TextPrimary else TextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenOnionSettings,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("onion_skin_settings_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Onion skin settings", tint = NeonPurpleBright, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = onDuplicateFrame,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate frame", tint = TextSecondary, modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = onDeleteFrame,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete frame", tint = OnionBeforeRose, modifier = Modifier.size(18.dp))
                    }
                    Surface(
                        onClick = onAddFrame,
                        shape = RoundedCornerShape(8.dp),
                        color = NeonPurple,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("add_frame_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_add_frame), tint = VoidBlack, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Frame Thumbnail Timeline Strip with Onion Skin Window Indicators
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("animation_timeline_strip")
            ) {
                itemsIndexed(frames, key = { _, f -> f.id }) { idx, frame ->
                    val isCurrent = idx == currentFrameIndex
                    val delta = idx - currentFrameIndex
                    val isOnionBefore = onionSkin.enabled && onionSkin.showBefore && !isPlaying && delta in -onionSkin.beforeCount..-1
                    val isOnionAfter = onionSkin.enabled && onionSkin.showAfter && !isPlaying && delta in 1..onionSkin.afterCount

                    val borderColor = when {
                        isCurrent -> NeonPurple
                        isOnionBefore -> OnionBeforeRose
                        isOnionAfter -> OnionAfterCyan
                        else -> StudioBorder
                    }
                    val borderWidth = if (isCurrent || isOnionBefore || isOnionAfter) 2.dp else 1.dp

                    Surface(
                        onClick = { onSelectFrame(idx) },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(borderWidth, borderColor),
                        color = ObsidianBg,
                        modifier = Modifier
                            .width(86.dp)
                            .height(58.dp)
                            .testTag("timeline_frame_$idx")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            FrameThumbnailCanvas(
                                frame = frame,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Frame number badge
                            Surface(
                                shape = RoundedCornerShape(bottomEnd = 6.dp),
                                color = if (isCurrent) NeonPurple else VoidBlack.copy(alpha = 0.85f),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = StudioMonoStyle,
                                    color = if (isCurrent) VoidBlack else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }

                            // Onion Skin relative offset pill on adjacent timeline cells
                            if (isOnionBefore || isOnionAfter) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 6.dp),
                                    color = if (isOnionBefore) OnionBeforeRose else OnionAfterCyan,
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                ) {
                                    Text(
                                        text = if (delta > 0) "+$delta" else "$delta",
                                        style = StudioMonoStyle,
                                        color = VoidBlack,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            } else if (frame.holdFrames > 1 || isCurrent) {
                                Surface(
                                    onClick = onToggleHold,
                                    shape = RoundedCornerShape(topStart = 6.dp),
                                    color = StudioSurfaceElevated.copy(alpha = 0.9f),
                                    modifier = Modifier.align(Alignment.BottomEnd)
                                ) {
                                    Text(
                                        text = "${frame.holdFrames}x",
                                        style = StudioMonoStyle,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FpsSelectorSheet(
    currentFps: Int,
    onSelectFps: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Playback Frame Rate (FPS)", style = MaterialTheme.typography.titleLarge)
            Text("Katana Slash defaults to 8 FPS; switch to 12 FPS (twos) or 24 FPS (ones) anytime.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(6, 8, 12, 18, 24, 30).forEach { fps ->
                    val active = fps == currentFps
                    Surface(
                        onClick = { onSelectFps(fps) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) NeonPurple.copy(alpha = 0.25f) else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (active) NeonPurple else StudioBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "$fps",
                            style = StudioMonoStyle,
                            color = if (active) NeonPurpleBright else TextPrimary,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
