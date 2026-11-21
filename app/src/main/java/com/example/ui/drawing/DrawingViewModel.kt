package com.example.ui.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DefaultNeonPalettes
import com.example.data.local.NeonPalettePreset
import com.example.data.local.StarterProjectFactory
import com.example.data.model.AnimationFrame
import com.example.data.model.DrawStroke
import com.example.data.model.DrawTool
import com.example.data.model.OnionSkinConfig
import com.example.data.model.ProjectType
import com.example.data.model.StrokePoint
import com.example.data.model.StudioLayer
import com.example.data.model.StudioProject
import com.example.data.repository.StudioRepository
import com.example.export.FrameRenderer
import com.example.ui.components.formatHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class DrawingUiState(
    val project: StudioProject? = null,
    val currentFrameIndex: Int = 0,
    val activeLayerIndex: Int = 1,
    val selectedTool: DrawTool = DrawTool.INK_BRUSH,
    val selectedColorArgb: Long = 0xFFC084FC,
    val favoriteNeonColors: List<Long> = DefaultNeonPalettes.defaultFavoriteColors,
    val palettePresets: List<NeonPalettePreset> = emptyList(),
    val activePalettePresetId: String = DefaultNeonPalettes.PRESET_FAVORITES,
    val isColorPickerOpen: Boolean = false,
    val strokeWidthNorm: Float = 0.010f,
    val activeStroke: DrawStroke? = null,
    val showGrid: Boolean = true,
    val undoStack: List<List<AnimationFrame>> = emptyList(),
    val redoStack: List<List<AnimationFrame>> = emptyList(),
    val statusMessage: String? = null
)

class DrawingViewModel(
    private val repository: StudioRepository,
    private val initialProjectId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrawingUiState())
    val uiState: StateFlow<DrawingUiState> = _uiState.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureStarterData()
            val loaded = if (initialProjectId != null) {
                repository.getProject(initialProjectId)
            } else {
                null
            } ?: repository.createProject("Anime Illustration", ProjectType.DRAWING, 1)

            val frames = if (loaded.frames.isNotEmpty()) {
                loaded.frames
            } else {
                StarterProjectFactory.createNewBlankProject(loaded.title, ProjectType.DRAWING).frames
            }
            val normalized = loaded.copy(frames = frames)
            // If opening a multi-frame sketchbook (>= 3 frames), start on frame 2 (index 1) so
            // both previous and next onion-skin overlays are immediately visible on the canvas.
            val startFrameIdx = if (frames.size >= 3) 1 else 0
            val layerCount = frames.getOrNull(startFrameIdx)?.layers?.size ?: 1
            _uiState.update {
                it.copy(
                    project = normalized,
                    currentFrameIndex = startFrameIdx,
                    activeLayerIndex = 1.coerceIn(0, (layerCount - 1).coerceAtLeast(0))
                )
            }
        }

        viewModelScope.launch {
            combine(
                repository.favoriteNeonColors,
                repository.palettePresets,
                repository.activePalettePresetId
            ) { favorites, presets, activePresetId ->
                Triple(favorites, presets, activePresetId)
            }.collect { (favorites, presets, activePresetId) ->
                _uiState.update {
                    it.copy(
                        favoriteNeonColors = favorites,
                        palettePresets = presets,
                        activePalettePresetId = activePresetId
                    )
                }
            }
        }
    }

    fun selectTool(tool: DrawTool) = _uiState.update { it.copy(selectedTool = tool) }
    fun selectColor(argb: Long) = _uiState.update { it.copy(selectedColorArgb = 0xFF000000L or (argb and 0x00FFFFFFL)) }
    fun openColorPicker() = _uiState.update { it.copy(isColorPickerOpen = true) }
    fun closeColorPicker() = _uiState.update { it.copy(isColorPickerOpen = false) }
    fun saveFavoriteNeonColor(colorArgb: Long) {
        val norm = 0xFF000000L or (colorArgb and 0x00FFFFFFL)
        viewModelScope.launch {
            repository.saveFavoriteNeonColor(norm)
            _uiState.update { it.copy(statusMessage = "Saved ${formatHexColor(norm)} to Neon Favorites") }
        }
    }
    fun removeFavoriteNeonColor(colorArgb: Long) {
        val norm = 0xFF000000L or (colorArgb and 0x00FFFFFFL)
        viewModelScope.launch { repository.removeFavoriteNeonColor(norm) }
    }
    fun toggleSaveFavoriteColor(colorArgb: Long) {
        val norm = 0xFF000000L or (colorArgb and 0x00FFFFFFL)
        val favorites = _uiState.value.favoriteNeonColors
        val exists = favorites.any { (0xFF000000L or (it and 0x00FFFFFFL)) == norm }
        viewModelScope.launch {
            if (exists) {
                if (favorites.size > 1) {
                    repository.removeFavoriteNeonColor(norm)
                    _uiState.update { it.copy(statusMessage = "Removed ${formatHexColor(norm)} from Favorites") }
                } else {
                    _uiState.update { it.copy(statusMessage = "Keep at least 1 favorite swatch") }
                }
            } else {
                repository.saveFavoriteNeonColor(norm)
                _uiState.update { it.copy(statusMessage = "Saved ${formatHexColor(norm)} to Neon Favorites") }
            }
        }
    }
    fun selectPalettePreset(presetId: String) {
        viewModelScope.launch { repository.selectPalettePreset(presetId) }
    }
    fun saveCustomPalettePreset(name: String, colors: List<Long>) {
        viewModelScope.launch {
            val saved = repository.saveCustomPalettePreset(name, colors)
            _uiState.update { it.copy(statusMessage = "Saved palette preset \"${saved.name}\"") }
        }
    }
    fun deleteCustomPalettePreset(presetId: String) {
        viewModelScope.launch { repository.deleteCustomPalettePreset(presetId) }
    }
    fun resetDefaultNeonColors() {
        viewModelScope.launch {
            repository.resetDefaultNeonColors()
            _uiState.update { it.copy(statusMessage = "Restored default neon palette") }
        }
    }
    fun setStrokeWidth(w: Float) = _uiState.update { it.copy(strokeWidthNorm = w.coerceIn(0.003f, 0.045f)) }
    fun toggleGrid() = _uiState.update { it.copy(showGrid = !it.showGrid) }
    fun clearStatusMessage() = _uiState.update { it.copy(statusMessage = null) }

    // --- Onion Skinning Controls ---

    fun toggleOnionSkin() {
        updateOnionSkin { it.copy(enabled = !it.enabled) }
    }

    fun toggleShowPreviousFrames() {
        updateOnionSkin { config ->
            val nextShow = !config.showBefore
            config.copy(
                enabled = if (nextShow) true else config.enabled,
                showBefore = nextShow,
                beforeCount = if (nextShow && config.beforeCount == 0) 1 else config.beforeCount
            )
        }
    }

    fun toggleShowNextFrames() {
        updateOnionSkin { config ->
            val nextShow = !config.showAfter
            config.copy(
                enabled = if (nextShow) true else config.enabled,
                showAfter = nextShow,
                afterCount = if (nextShow && config.afterCount == 0) 1 else config.afterCount
            )
        }
    }

    fun updateOnionSkin(transform: (OnionSkinConfig) -> OnionSkinConfig) {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val updatedProject = project.copy(onionSkin = transform(project.onionSkin))
            scheduleSave(updatedProject)
            state.copy(project = updatedProject)
        }
    }

    // --- Frame Navigation & Sheet Management ---

    fun selectFrame(index: Int) {
        _uiState.update { state ->
            val frames = state.project?.frames ?: return@update state
            val safeIdx = index.coerceIn(0, frames.lastIndex.coerceAtLeast(0))
            val maxLayerIdx = (frames[safeIdx].layers.lastIndex).coerceAtLeast(0)
            state.copy(
                currentFrameIndex = safeIdx,
                activeLayerIndex = state.activeLayerIndex.coerceIn(0, maxLayerIdx)
            )
        }
    }

    fun stepFrame(delta: Int) {
        val state = _uiState.value
        val count = state.project?.frames?.size ?: return
        if (count <= 1) return
        val next = (state.currentFrameIndex + delta + count) % count
        selectFrame(next)
    }

    fun addBlankFrameAfterCurrent() {
        mutateFrames { frames, curIdx ->
            val templateLayers = frames.getOrNull(curIdx)?.layers?.map { layer ->
                layer.copy(id = UUID.randomUUID().toString(), strokes = emptyList())
            } ?: listOf(StudioLayer(id = UUID.randomUUID().toString(), name = "Line Art"))
            val newFrame = AnimationFrame(
                id = UUID.randomUUID().toString(),
                frameNumber = curIdx + 2,
                layers = templateLayers
            )
            val mutable = frames.toMutableList()
            val insertAt = (curIdx + 1).coerceIn(0, mutable.size)
            mutable.add(insertAt, newFrame)
            reindexFrames(mutable) to insertAt
        }
    }

    fun duplicateCurrentFrame() {
        mutateFrames { frames, curIdx ->
            val source = frames.getOrNull(curIdx) ?: return@mutateFrames frames to curIdx
            val clonedLayers = source.layers.map { layer ->
                layer.copy(
                    id = UUID.randomUUID().toString(),
                    strokes = layer.strokes.map { it.copy(id = UUID.randomUUID().toString()) }
                )
            }
            val dup = AnimationFrame(
                id = UUID.randomUUID().toString(),
                frameNumber = curIdx + 2,
                layers = clonedLayers
            )
            val mutable = frames.toMutableList()
            val insertAt = curIdx + 1
            mutable.add(insertAt, dup)
            reindexFrames(mutable) to insertAt
        }
    }

    fun deleteCurrentFrame() {
        mutateFrames { frames, curIdx ->
            if (frames.size <= 1) {
                _uiState.update { it.copy(statusMessage = "At least 1 frame is required") }
                return@mutateFrames frames to curIdx
            }
            val mutable = frames.toMutableList().apply { removeAt(curIdx) }
            val nextIdx = curIdx.coerceIn(0, mutable.lastIndex)
            reindexFrames(mutable) to nextIdx
        }
    }

    // --- Drawing Gestures ---

    fun startStroke(normX: Float, normY: Float) {
        val state = _uiState.value
        val frame = state.project?.frames?.getOrNull(state.currentFrameIndex) ?: return
        val layer = frame.layers.getOrNull(state.activeLayerIndex) ?: return
        if (layer.isLocked || !layer.isVisible) {
            _uiState.update { it.copy(statusMessage = "Active layer is locked or hidden") }
            return
        }
        val stroke = DrawStroke(
            id = UUID.randomUUID().toString(),
            points = listOf(StrokePoint(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))),
            colorArgb = state.selectedColorArgb,
            strokeWidth = state.strokeWidthNorm,
            tool = state.selectedTool
        )
        _uiState.update { it.copy(activeStroke = stroke) }
    }

    fun appendStrokePoint(normX: Float, normY: Float) {
        _uiState.update { state ->
            val current = state.activeStroke ?: return@update state
            val clamped = StrokePoint(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))
            val pts = if (current.tool in listOf(DrawTool.LINE, DrawTool.RECT, DrawTool.CIRCLE)) {
                listOf(current.points.first(), clamped)
            } else {
                current.points + clamped
            }
            state.copy(activeStroke = current.copy(points = pts))
        }
    }

    fun finishStroke() {
        val state = _uiState.value
        val stroke = state.activeStroke ?: return
        mutateLayers(clearActiveStroke = true) { layers, activeIdx ->
            val target = layers.getOrNull(activeIdx) ?: return@mutateLayers layers to activeIdx
            val updated = layers.toMutableList().apply {
                set(activeIdx, target.copy(strokes = target.strokes + stroke))
            }
            updated to activeIdx
        }
    }

    fun undo() {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val prevFrames = state.undoStack.lastOrNull() ?: return@update state
            val nextIdx = state.currentFrameIndex.coerceIn(0, prevFrames.lastIndex.coerceAtLeast(0))
            val maxLayerIdx = (prevFrames.getOrNull(nextIdx)?.layers?.lastIndex ?: 0).coerceAtLeast(0)
            val updatedProj = project.copy(frames = prevFrames)
            scheduleSave(updatedProj)
            state.copy(
                project = updatedProj,
                currentFrameIndex = nextIdx,
                activeLayerIndex = state.activeLayerIndex.coerceIn(0, maxLayerIdx),
                undoStack = state.undoStack.dropLast(1),
                redoStack = (state.redoStack + listOf(project.frames)).takeLast(25)
            )
        }
    }

    fun redo() {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val nextFrames = state.redoStack.lastOrNull() ?: return@update state
            val nextIdx = state.currentFrameIndex.coerceIn(0, nextFrames.lastIndex.coerceAtLeast(0))
            val maxLayerIdx = (nextFrames.getOrNull(nextIdx)?.layers?.lastIndex ?: 0).coerceAtLeast(0)
            val updatedProj = project.copy(frames = nextFrames)
            scheduleSave(updatedProj)
            state.copy(
                project = updatedProj,
                currentFrameIndex = nextIdx,
                activeLayerIndex = state.activeLayerIndex.coerceIn(0, maxLayerIdx),
                redoStack = state.redoStack.dropLast(1),
                undoStack = (state.undoStack + listOf(project.frames)).takeLast(25)
            )
        }
    }

    // --- Layer Management Operations ---

    fun selectLayer(idx: Int) = _uiState.update { state ->
        val frame = state.project?.frames?.getOrNull(state.currentFrameIndex) ?: return@update state
        state.copy(activeLayerIndex = idx.coerceIn(0, frame.layers.lastIndex.coerceAtLeast(0)))
    }

    fun addLayer() = mutateLayers { layers, _ ->
        val updated = layers + StudioLayer(id = UUID.randomUUID().toString(), name = "Layer ${layers.size + 1}")
        updated to updated.lastIndex
    }

    fun deleteLayer(idx: Int) = mutateLayers { layers, activeIdx ->
        if (layers.size <= 1) return@mutateLayers layers to activeIdx
        val updated = layers.toMutableList().apply { removeAt(idx) }
        updated to activeIdx.coerceIn(0, updated.lastIndex)
    }

    fun renameLayer(idx: Int, name: String) = mutateLayers { layers, activeIdx ->
        val target = layers.getOrNull(idx) ?: return@mutateLayers layers to activeIdx
        layers.toMutableList().apply { set(idx, target.copy(name = name)) } to activeIdx
    }

    fun moveLayerUp(idx: Int) = mutateLayers { layers, _ ->
        if (idx >= layers.lastIndex) return@mutateLayers layers to idx
        val updated = layers.toMutableList()
        val item = updated.removeAt(idx)
        updated.add(idx + 1, item)
        updated to (idx + 1)
    }

    fun moveLayerDown(idx: Int) = mutateLayers { layers, _ ->
        if (idx <= 0) return@mutateLayers layers to idx
        val updated = layers.toMutableList()
        val item = updated.removeAt(idx)
        updated.add(idx - 1, item)
        updated to (idx - 1)
    }

    fun mergeLayerDown(idx: Int) = mutateLayers { layers, _ ->
        if (idx <= 0 || idx > layers.lastIndex) return@mutateLayers layers to idx
        val upper = layers[idx]
        val lower = layers[idx - 1]
        val merged = lower.copy(strokes = lower.strokes + upper.strokes)
        val updated = layers.toMutableList().apply {
            set(idx - 1, merged)
            removeAt(idx)
        }
        updated to (idx - 1)
    }

    fun toggleLayerVisibility(idx: Int) = mutateLayers { layers, activeIdx ->
        val target = layers.getOrNull(idx) ?: return@mutateLayers layers to activeIdx
        layers.toMutableList().apply { set(idx, target.copy(isVisible = !target.isVisible)) } to activeIdx
    }

    fun toggleLayerLock(idx: Int) = mutateLayers { layers, activeIdx ->
        val target = layers.getOrNull(idx) ?: return@mutateLayers layers to activeIdx
        layers.toMutableList().apply { set(idx, target.copy(isLocked = !target.isLocked)) } to activeIdx
    }

    fun changeLayerOpacity(idx: Int, opacity: Float) = mutateLayers(recordUndo = false) { layers, activeIdx ->
        val target = layers.getOrNull(idx) ?: return@mutateLayers layers to activeIdx
        layers.toMutableList().apply { set(idx, target.copy(opacity = opacity.coerceIn(0f, 1f))) } to activeIdx
    }

    fun exportPngSnapshot() {
        val state = _uiState.value
        val frame = state.project?.frames?.getOrNull(state.currentFrameIndex) ?: return
        val title = state.project?.title?.replace(Regex("[^A-Za-z0-9_-]"), "_") ?: "artwork"
        viewModelScope.launch {
            val outFile = withContext(Dispatchers.IO) {
                val bmp = Bitmap.createBitmap(960, 720, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bmp)
                    FrameRenderer.drawFrameToAndroidCanvas(canvas, frame, 960, 720)
                    val file = File(repository.getExportsDir(), "${title}_f${state.currentFrameIndex + 1}_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
                    file
                } finally {
                    bmp.recycle()
                }
            }
            _uiState.update { it.copy(statusMessage = "Saved PNG: ${outFile.name}") }
        }
    }

    private fun mutateLayers(
        recordUndo: Boolean = true,
        clearActiveStroke: Boolean = false,
        transform: (List<StudioLayer>, Int) -> Pair<List<StudioLayer>, Int>
    ) {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val curIdx = state.currentFrameIndex
            val frame = project.frames.getOrNull(curIdx) ?: return@update state
            val (newLayers, newActiveIdx) = transform(frame.layers, state.activeLayerIndex)
            val newFrames = project.frames.toMutableList().apply {
                set(curIdx, frame.copy(layers = newLayers))
            }
            val updatedProj = project.copy(frames = newFrames)
            scheduleSave(updatedProj)
            state.copy(
                project = updatedProj,
                activeLayerIndex = newActiveIdx.coerceIn(0, newLayers.lastIndex.coerceAtLeast(0)),
                activeStroke = if (clearActiveStroke) null else state.activeStroke,
                undoStack = if (recordUndo) (state.undoStack + listOf(project.frames)).takeLast(25) else state.undoStack,
                redoStack = if (recordUndo) emptyList() else state.redoStack
            )
        }
    }

    private fun mutateFrames(transform: (List<AnimationFrame>, Int) -> Pair<List<AnimationFrame>, Int>) {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val (newFrames, newIndex) = transform(project.frames, state.currentFrameIndex)
            if (newFrames == project.frames && newIndex == state.currentFrameIndex) return@update state
            val updatedProject = project.copy(frames = newFrames)
            scheduleSave(updatedProject)
            val maxLayerIdx = (newFrames.getOrNull(newIndex)?.layers?.lastIndex ?: 0).coerceAtLeast(0)
            state.copy(
                project = updatedProject,
                currentFrameIndex = newIndex.coerceIn(0, newFrames.lastIndex.coerceAtLeast(0)),
                activeLayerIndex = state.activeLayerIndex.coerceIn(0, maxLayerIdx),
                undoStack = (state.undoStack + listOf(project.frames)).takeLast(25),
                redoStack = emptyList()
            )
        }
    }

    private fun reindexFrames(frames: List<AnimationFrame>): List<AnimationFrame> {
        return frames.mapIndexed { idx, frame -> frame.copy(frameNumber = idx + 1) }
    }

    private fun scheduleSave(project: StudioProject) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(250)
            repository.saveProject(project)
        }
    }

    class Factory(
        private val repository: StudioRepository,
        private val projectId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DrawingViewModel(repository, projectId) as T
        }
    }
}
