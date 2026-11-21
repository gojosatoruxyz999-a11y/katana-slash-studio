package com.example.ui.animation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DefaultNeonPalettes
import com.example.data.local.NeonPalettePreset
import com.example.data.local.StarterProjectFactory
import com.example.data.local.StudioJsonCodec
import com.example.data.model.AnimationFrame
import com.example.data.model.DrawStroke
import com.example.data.model.DrawTool
import com.example.data.model.OnionSkinConfig
import com.example.data.model.ProjectType
import com.example.data.model.StrokePoint
import com.example.data.model.StudioLayer
import com.example.data.model.StudioOutroConfig
import com.example.data.model.StudioProject
import com.example.data.repository.StudioRepository
import com.example.export.FrameRenderer
import com.example.export.GifEncoder
import com.example.export.Mp4Exporter
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
import kotlin.math.hypot

enum class AnimationExportFormat(val label: String, val extension: String, val mimeType: String) {
    MP4("MP4 Video", "mp4", "video/mp4"),
    GIF("Animated GIF", "gif", "image/gif")
}

data class StudioExportModalState(
    val isOpen: Boolean = false,
    val selectedFormat: AnimationExportFormat = AnimationExportFormat.MP4,
    val exportFps: Int = 8,
    val transparentPngBackground: Boolean = true,
    val includeOutro: Boolean = false,
    val outroCreatorText: String = "Made with Katana Slash Canvas",
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val lastExportedFile: File? = null,
    val modalStatusText: String? = null
)

data class AnimationUiState(
    val project: StudioProject? = null,
    val currentFrameIndex: Int = 0,
    val activeLayerIndex: Int = 1,
    val selectedTool: DrawTool = DrawTool.KATANA_BLADE,
    val selectedColorArgb: Long = 0xFFC084FC,
    val favoriteNeonColors: List<Long> = DefaultNeonPalettes.defaultFavoriteColors,
    val palettePresets: List<NeonPalettePreset> = emptyList(),
    val activePalettePresetId: String = DefaultNeonPalettes.PRESET_FAVORITES,
    val isColorPickerOpen: Boolean = false,
    val brushSizePx: Float = 12f,
    val brushOpacity: Float = 1f,
    val glowIntensity: Float = 0.45f,
    val strokeWidthNorm: Float = 12f / 900f,
    val isBrushSettingsExpanded: Boolean = false,
    val isFullScreenCanvas: Boolean = false,
    val activeStroke: DrawStroke? = null,
    val isPlaying: Boolean = false,
    val showGrid: Boolean = true,
    val undoStack: List<List<AnimationFrame>> = emptyList(),
    val redoStack: List<List<AnimationFrame>> = emptyList(),
    val statusMessage: String? = null,
    val exportModal: StudioExportModalState = StudioExportModalState()
)

class AnimationViewModel(
    private val repository: StudioRepository,
    private val initialProjectId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimationUiState())
    val uiState: StateFlow<AnimationUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var saveJob: Job? = null
    private var lastStrokePointTimeMs: Long = 0L

    init {
        viewModelScope.launch {
            repository.ensureStarterData()
            val loaded = if (initialProjectId != null) {
                repository.getProject(initialProjectId)
            } else {
                null
            } ?: StarterProjectFactory.createStarterKatanaProject().also {
                val existing = repository.getProject(it.id)
                if (existing == null) repository.saveProject(it)
            }

            val finalProj = if (loaded.frames.isEmpty()) {
                val blank = StarterProjectFactory.createNewBlankProject(loaded.title, ProjectType.ANIMATION, loaded.fps)
                loaded.copy(frames = blank.frames)
            } else {
                loaded
            }
            val defaultLayerIdx = (finalProj.frames.firstOrNull()?.layers?.indexOfFirst {
                it.name.contains("Line", ignoreCase = true)
            }?.takeIf { it >= 0 } ?: (finalProj.frames.firstOrNull()?.layers?.lastIndex ?: 0)).coerceAtLeast(0)

            _uiState.update {
                it.copy(
                    project = finalProj,
                    currentFrameIndex = 0,
                    activeLayerIndex = defaultLayerIdx,
                    exportModal = it.exportModal.copy(
                        exportFps = if (finalProj.fps in listOf(8, 12, 24)) finalProj.fps else 8
                    )
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

    fun selectTool(tool: DrawTool) {
        _uiState.update { state ->
            val suggestedGlow = when (tool) {
                DrawTool.NEON_AURA -> state.glowIntensity.coerceAtLeast(0.75f)
                DrawTool.ERASER -> 0f
                else -> state.glowIntensity
            }
            state.copy(selectedTool = tool, glowIntensity = suggestedGlow)
        }
    }

    fun selectColor(colorArgb: Long) {
        _uiState.update { it.copy(selectedColorArgb = 0xFF000000L or (colorArgb and 0x00FFFFFFL)) }
    }

    fun openColorPicker() {
        _uiState.update { it.copy(isColorPickerOpen = true) }
    }

    fun closeColorPicker() {
        _uiState.update { it.copy(isColorPickerOpen = false) }
    }

    fun saveFavoriteNeonColor(colorArgb: Long) {
        val norm = 0xFF000000L or (colorArgb and 0x00FFFFFFL)
        viewModelScope.launch {
            repository.saveFavoriteNeonColor(norm)
            _uiState.update { it.copy(statusMessage = "Saved ${formatHexColor(norm)} to Neon Favorites") }
        }
    }

    fun removeFavoriteNeonColor(colorArgb: Long) {
        val norm = 0xFF000000L or (colorArgb and 0x00FFFFFFL)
        viewModelScope.launch {
            repository.removeFavoriteNeonColor(norm)
        }
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
        viewModelScope.launch {
            repository.selectPalettePreset(presetId)
        }
    }

    fun saveCustomPalettePreset(name: String, colors: List<Long>) {
        viewModelScope.launch {
            val saved = repository.saveCustomPalettePreset(name, colors)
            _uiState.update { it.copy(statusMessage = "Saved palette preset \"${saved.name}\"") }
        }
    }

    fun deleteCustomPalettePreset(presetId: String) {
        viewModelScope.launch {
            repository.deleteCustomPalettePreset(presetId)
        }
    }

    fun resetDefaultNeonColors() {
        viewModelScope.launch {
            repository.resetDefaultNeonColors()
            _uiState.update { it.copy(statusMessage = "Restored default neon palette") }
        }
    }

    fun setBrushSizePx(sizePx: Float) {
        val clamped = sizePx.coerceIn(1f, 60f)
        _uiState.update {
            it.copy(
                brushSizePx = clamped,
                strokeWidthNorm = (clamped / 900f).coerceIn(0.0015f, 0.068f)
            )
        }
    }

    fun setStrokeWidth(widthNorm: Float) {
        val safeNorm = widthNorm.coerceIn(0.0015f, 0.068f)
        _uiState.update {
            it.copy(
                strokeWidthNorm = safeNorm,
                brushSizePx = (safeNorm * 900f).coerceIn(1f, 60f)
            )
        }
    }

    fun setBrushOpacity(opacity: Float) {
        _uiState.update { it.copy(brushOpacity = opacity.coerceIn(0.10f, 1.0f)) }
    }

    fun setGlowIntensity(glow: Float) {
        _uiState.update { it.copy(glowIntensity = glow.coerceIn(0f, 1f)) }
    }

    fun toggleBrushSettingsExpanded() {
        _uiState.update { it.copy(isBrushSettingsExpanded = !it.isBrushSettingsExpanded) }
    }

    fun toggleFullScreenCanvas() {
        _uiState.update { it.copy(isFullScreenCanvas = !it.isFullScreenCanvas) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // --- Drawing Gestures ---

    fun startStroke(normX: Float, normY: Float) {
        val state = _uiState.value
        if (state.isPlaying) return
        val frame = state.project?.frames?.getOrNull(state.currentFrameIndex) ?: return
        val layer = frame.layers.getOrNull(state.activeLayerIndex) ?: return
        if (layer.isLocked || !layer.isVisible) {
            _uiState.update { it.copy(statusMessage = "Active layer is locked or hidden") }
            return
        }
        lastStrokePointTimeMs = System.currentTimeMillis()
        val stroke = DrawStroke(
            id = UUID.randomUUID().toString(),
            points = listOf(StrokePoint(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))),
            colorArgb = state.selectedColorArgb,
            strokeWidth = state.strokeWidthNorm,
            tool = state.selectedTool,
            alpha = state.brushOpacity,
            glowIntensity = if (state.selectedTool == DrawTool.ERASER) 0f else state.glowIntensity,
            pointWeights = if (state.selectedTool == DrawTool.KATANA_BLADE) listOf(0.45f) else emptyList()
        )
        _uiState.update { it.copy(activeStroke = stroke) }
    }

    fun appendStrokePoint(normX: Float, normY: Float) {
        val now = System.currentTimeMillis()
        val dtMs = (now - lastStrokePointTimeMs).coerceAtLeast(8L)
        lastStrokePointTimeMs = now

        _uiState.update { state ->
            val current = state.activeStroke ?: return@update state
            val clamped = StrokePoint(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))
            val isShapeTool = current.tool in listOf(DrawTool.LINE, DrawTool.RECT, DrawTool.CIRCLE)
            val updatedPts = if (isShapeTool) {
                listOf(current.points.first(), clamped)
            } else {
                current.points + clamped
            }
            val updatedWeights = if (current.tool == DrawTool.KATANA_BLADE) {
                val prev = current.points.last()
                val dist = hypot(clamped.x - prev.x, clamped.y - prev.y)
                val speed = (dist * 1000f) / dtMs.toFloat() // normalized units per second
                val targetWeight = (1.35f / (1f + speed * 0.45f)).coerceIn(0.32f, 1.35f)
                val smoothed = if (current.pointWeights.isNotEmpty()) {
                    current.pointWeights.last() * 0.45f + targetWeight * 0.55f
                } else {
                    targetWeight
                }
                current.pointWeights + smoothed
            } else {
                current.pointWeights
            }
            state.copy(
                activeStroke = current.copy(
                    points = updatedPts,
                    pointWeights = updatedWeights
                )
            )
        }
    }

    fun finishStroke() {
        val state = _uiState.value
        val finished = state.activeStroke ?: return
        val project = state.project ?: return
        val frameIdx = state.currentFrameIndex
        val layerIdx = state.activeLayerIndex

        val previousFrames = project.frames
        val targetFrame = previousFrames.getOrNull(frameIdx) ?: return
        val targetLayer = targetFrame.layers.getOrNull(layerIdx) ?: return

        val updatedLayer = targetLayer.copy(strokes = targetLayer.strokes + finished)
        val updatedLayers = targetFrame.layers.toMutableList().apply { set(layerIdx, updatedLayer) }
        val updatedFrame = targetFrame.copy(layers = updatedLayers)
        val updatedFrames = previousFrames.toMutableList().apply { set(frameIdx, updatedFrame) }

        val updatedProject = project.copy(frames = updatedFrames)
        _uiState.update {
            it.copy(
                project = updatedProject,
                activeStroke = null,
                undoStack = (it.undoStack + listOf(previousFrames)).takeLast(25),
                redoStack = emptyList()
            )
        }
        scheduleSave(updatedProject)
    }

    fun undo() {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val prevFrames = state.undoStack.lastOrNull() ?: return@update state
            val nextIdx = state.currentFrameIndex.coerceIn(0, prevFrames.lastIndex.coerceAtLeast(0))
            val updated = project.copy(frames = prevFrames)
            scheduleSave(updated)
            state.copy(
                project = updated,
                currentFrameIndex = nextIdx,
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
            val updated = project.copy(frames = nextFrames)
            scheduleSave(updated)
            state.copy(
                project = updated,
                currentFrameIndex = nextIdx,
                redoStack = state.redoStack.dropLast(1),
                undoStack = (state.undoStack + listOf(project.frames)).takeLast(25)
            )
        }
    }

    // --- Onion Skinning Controls ---

    fun toggleOnionSkin() {
        updateOnionSkin { it.copy(enabled = !it.enabled) }
    }

    fun updateOnionSkin(transform: (OnionSkinConfig) -> OnionSkinConfig) {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val updatedProject = project.copy(onionSkin = transform(project.onionSkin))
            scheduleSave(updatedProject)
            state.copy(project = updatedProject)
        }
    }

    // --- Timeline & Frame Controls ---

    fun selectFrame(index: Int) {
        _uiState.update { state ->
            val frames = state.project?.frames ?: return@update state
            val safeFrameIdx = index.coerceIn(0, frames.lastIndex.coerceAtLeast(0))
            val maxLayerIdx = (frames[safeFrameIdx].layers.lastIndex).coerceAtLeast(0)
            state.copy(
                currentFrameIndex = safeFrameIdx,
                activeLayerIndex = state.activeLayerIndex.coerceIn(0, maxLayerIdx)
            )
        }
    }

    fun stepFrame(delta: Int) {
        val state = _uiState.value
        val count = state.project?.frames?.size ?: return
        if (count == 0) return
        val next = (state.currentFrameIndex + delta + count) % count
        selectFrame(next)
    }

    fun addBlankFrameAfterCurrent() {
        mutateFrames { frames, curIdx ->
            val templateLayers = frames.getOrNull(curIdx)?.layers?.map { layer ->
                layer.copy(id = UUID.randomUUID().toString(), strokes = emptyList())
            } ?: listOf(
                StudioLayer(id = UUID.randomUUID().toString(), name = "Background"),
                StudioLayer(id = UUID.randomUUID().toString(), name = "Line Art"),
                StudioLayer(id = UUID.randomUUID().toString(), name = "Neon Slash FX")
            )
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
                layers = clonedLayers,
                holdFrames = source.holdFrames
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

    fun toggleCurrentFrameHold() {
        mutateFrames { frames, curIdx ->
            val frame = frames.getOrNull(curIdx) ?: return@mutateFrames frames to curIdx
            val nextHold = if (frame.holdFrames >= 3) 1 else frame.holdFrames + 1
            val mutable = frames.toMutableList().apply {
                set(curIdx, frame.copy(holdFrames = nextHold))
            }
            mutable to curIdx
        }
    }

    fun setFps(fps: Int) {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val safeFps = fps.coerceIn(1, 30)
            val updated = project.copy(fps = safeFps)
            scheduleSave(updated)
            state.copy(
                project = updated,
                exportModal = state.exportModal.copy(
                    exportFps = if (safeFps in listOf(8, 12, 24)) safeFps else state.exportModal.exportFps
                )
            )
        }
    }

    fun togglePlayback() {
        if (_uiState.value.isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val project = _uiState.value.project ?: return
        if (project.frames.size <= 1) return
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = true) }
        playbackJob = viewModelScope.launch {
            while (true) {
                val curProj = _uiState.value.project ?: break
                val frames = curProj.frames
                if (frames.isEmpty()) break
                val curIdx = _uiState.value.currentFrameIndex.coerceIn(0, frames.lastIndex)
                val hold = frames[curIdx].holdFrames.coerceAtLeast(1)
                val frameMs = (1000L / curProj.fps.coerceIn(1, 30)) * hold
                delay(frameMs)
                val nextIdx = (curIdx + 1) % frames.size
                _uiState.update { it.copy(currentFrameIndex = nextIdx) }
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
    }

    // --- Export / Save / Load Modal ---

    fun openExportSaveModal() {
        stopPlayback()
        _uiState.update { state ->
            val curFps = state.project?.fps ?: 8
            state.copy(
                exportModal = state.exportModal.copy(
                    isOpen = true,
                    exportFps = if (curFps in listOf(8, 12, 24)) curFps else state.exportModal.exportFps,
                    modalStatusText = null
                )
            )
        }
    }

    fun closeExportSaveModal() {
        _uiState.update { it.copy(exportModal = it.exportModal.copy(isOpen = false)) }
    }

    fun setExportFormat(format: AnimationExportFormat) {
        _uiState.update { it.copy(exportModal = it.exportModal.copy(selectedFormat = format)) }
    }

    fun setExportFps(fps: Int) {
        _uiState.update { it.copy(exportModal = it.exportModal.copy(exportFps = fps.coerceIn(8, 24))) }
    }

    fun setTransparentPngBackground(enabled: Boolean) {
        _uiState.update { it.copy(exportModal = it.exportModal.copy(transparentPngBackground = enabled)) }
    }

    fun setIncludeOutro(enabled: Boolean) {
        _uiState.update { it.copy(exportModal = it.exportModal.copy(includeOutro = enabled)) }
    }

    fun setOutroCreatorText(text: String) {
        _uiState.update { it.copy(exportModal = it.exportModal.copy(outroCreatorText = text.take(60))) }
    }

    fun buildProjectJsonBundle(): String? {
        val project = _uiState.value.project ?: return null
        return StudioJsonCodec.encodeProjectBundle(project)
    }

    fun saveProjectJsonLocally() {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            val savedFile = withContext(Dispatchers.IO) {
                repository.saveProject(project)
                val cleanTitle = project.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "katana_cut" }
                val outFile = File(repository.getExportsDir(), "${cleanTitle}.ronin.json")
                outFile.writeText(StudioJsonCodec.encodeProjectBundle(project), Charsets.UTF_8)
                outFile
            }
            _uiState.update {
                it.copy(
                    exportModal = it.exportModal.copy(
                        lastExportedFile = savedFile,
                        modalStatusText = "Saved project JSON: ${savedFile.name}"
                    )
                )
            }
        }
    }

    fun reportProjectJsonSavedToUri(displayLabel: String) {
        _uiState.update {
            it.copy(
                exportModal = it.exportModal.copy(
                    modalStatusText = "Saved project bundle: $displayLabel"
                )
            )
        }
    }

    fun loadProjectFromJson(jsonText: String): Boolean {
        val decoded = StudioJsonCodec.decodeProjectBundle(jsonText) ?: return false
        val normalized = if (decoded.frames.isEmpty()) {
            val blank = StarterProjectFactory.createNewBlankProject(decoded.title, ProjectType.ANIMATION, decoded.fps)
            decoded.copy(frames = blank.frames)
        } else {
            decoded
        }
        viewModelScope.launch {
            repository.saveProject(normalized)
        }
        val defaultLayerIdx = (normalized.frames.firstOrNull()?.layers?.lastIndex ?: 0).coerceAtLeast(0)
        _uiState.update {
            it.copy(
                project = normalized,
                currentFrameIndex = 0,
                activeLayerIndex = defaultLayerIdx,
                undoStack = emptyList(),
                redoStack = emptyList(),
                statusMessage = "Loaded \"${normalized.title}\" (${normalized.frames.size} frames)",
                exportModal = it.exportModal.copy(
                    isOpen = false,
                    exportFps = if (normalized.fps in listOf(8, 12, 24)) normalized.fps else 8,
                    modalStatusText = "Loaded ${normalized.title}"
                )
            )
        }
        return true
    }

    fun exportCurrentFramePng() {
        val state = _uiState.value
        val project = state.project ?: return
        val frame = project.frames.getOrNull(state.currentFrameIndex) ?: return
        if (state.exportModal.isExporting) return

        val transparentBg = state.exportModal.transparentPngBackground
        val cleanTitle = project.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "katana_frame" }
        val outFile = File(
            repository.getExportsDir(),
            "${cleanTitle}_f${state.currentFrameIndex + 1}_${System.currentTimeMillis()}.png"
        )

        _uiState.update {
            it.copy(exportModal = it.exportModal.copy(isExporting = true, exportProgress = 0.25f, modalStatusText = null))
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = Bitmap.createBitmap(1920, 1200, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bmp)
                        FrameRenderer.drawFrameToAndroidCanvas(
                            canvas = canvas,
                            frame = frame,
                            width = 1920,
                            height = 1200,
                            backgroundColor = if (transparentBg) Color.TRANSPARENT else Color.rgb(10, 10, 18)
                        )
                        FileOutputStream(outFile).use { fos ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }
                    } finally {
                        bmp.recycle()
                    }
                    outFile
                }
            }
            result.fold(
                onSuccess = { file ->
                    _uiState.update {
                        it.copy(
                            exportModal = it.exportModal.copy(
                                isExporting = false,
                                exportProgress = 1f,
                                lastExportedFile = file,
                                modalStatusText = "Exported PNG (${file.length() / 1024} KB): ${file.name}"
                            )
                        )
                    }
                },
                onFailure = { err ->
                    runCatching { outFile.delete() }
                    _uiState.update {
                        it.copy(
                            exportModal = it.exportModal.copy(
                                isExporting = false,
                                exportProgress = 0f,
                                modalStatusText = "PNG export failed: ${err.localizedMessage ?: "unknown error"}"
                            )
                        )
                    }
                }
            )
        }
    }

    fun exportAnimationMedia() {
        val state = _uiState.value
        val project = state.project ?: return
        if (project.frames.isEmpty() || state.exportModal.isExporting) return

        val modal = state.exportModal
        val format = modal.selectedFormat
        val fps = modal.exportFps.coerceIn(8, 24)
        val outro = StudioOutroConfig(
            enabled = modal.includeOutro,
            creatorText = modal.outroCreatorText.ifBlank { "Made with Katana Slash Canvas" }
        )
        val cleanTitle = project.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "katana_cut" }
        val outFile = File(
            repository.getExportsDir(),
            "${cleanTitle}_${fps}fps_${System.currentTimeMillis()}.${format.extension}"
        )

        _uiState.update {
            it.copy(exportModal = it.exportModal.copy(isExporting = true, exportProgress = 0f, modalStatusText = null))
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (format) {
                        AnimationExportFormat.GIF -> GifEncoder.exportFramesToGif(
                            frames = project.frames,
                            fps = fps,
                            width = 480,
                            height = 300,
                            outFile = outFile,
                            outroConfig = outro
                        ) { p ->
                            _uiState.update { st -> st.copy(exportModal = st.exportModal.copy(exportProgress = p)) }
                        }

                        AnimationExportFormat.MP4 -> Mp4Exporter.exportFramesToMp4(
                            frames = project.frames,
                            fps = fps,
                            width = 720,
                            height = 450,
                            outFile = outFile,
                            outroConfig = outro
                        ) { p ->
                            _uiState.update { st -> st.copy(exportModal = st.exportModal.copy(exportProgress = p)) }
                        }
                    }
                }
            }

            result.fold(
                onSuccess = { file ->
                    val outroTag = if (outro.enabled) " + 1s Outro" else ""
                    _uiState.update {
                        it.copy(
                            exportModal = it.exportModal.copy(
                                isExporting = false,
                                exportProgress = 1f,
                                lastExportedFile = file,
                                modalStatusText = "Exported ${format.label} (${fps} FPS$outroTag): ${file.name}"
                            )
                        )
                    }
                },
                onFailure = { err ->
                    runCatching { outFile.delete() }
                    _uiState.update {
                        it.copy(
                            exportModal = it.exportModal.copy(
                                isExporting = false,
                                exportProgress = 0f,
                                modalStatusText = "${format.label} export failed (${err.localizedMessage ?: "codec unavailable"}). Try Animated GIF."
                            )
                        )
                    }
                }
            )
        }
    }

    // --- Comprehensive Layer Management ---

    fun selectLayer(index: Int) {
        _uiState.update { state ->
            val frame = state.project?.frames?.getOrNull(state.currentFrameIndex) ?: return@update state
            state.copy(activeLayerIndex = index.coerceIn(0, frame.layers.lastIndex.coerceAtLeast(0)))
        }
    }

    fun addLayer() {
        mutateCurrentFrameLayers { layers, _ ->
            val newLayer = StudioLayer(
                id = UUID.randomUUID().toString(),
                name = "Layer ${layers.size + 1}",
                isVisible = true,
                opacity = 1f
            )
            val updated = layers + newLayer
            updated to updated.lastIndex
        }
    }

    fun ensureStandardLayers() {
        mutateCurrentFrameLayers { layers, activeIdx ->
            val mutable = layers.toMutableList()
            val namesLower = mutable.map { it.name.lowercase() }
            if (namesLower.none { "background" in it || "guide" in it }) {
                mutable.add(0, StudioLayer(id = UUID.randomUUID().toString(), name = "Background", opacity = 0.6f))
            }
            if (namesLower.none { "line" in it || "ink" in it }) {
                mutable.add(StudioLayer(id = UUID.randomUUID().toString(), name = "Line Art", opacity = 1f))
            }
            if (namesLower.none { "fx" in it }) {
                mutable.add(StudioLayer(id = UUID.randomUUID().toString(), name = "Neon Slash FX", opacity = 0.92f))
            }
            mutable to activeIdx.coerceIn(0, mutable.lastIndex.coerceAtLeast(0))
        }
    }

    fun deleteLayer(layerIdx: Int) {
        mutateCurrentFrameLayers { layers, activeIdx ->
            if (layers.size <= 1) return@mutateCurrentFrameLayers layers to activeIdx
            val updated = layers.toMutableList().apply { removeAt(layerIdx) }
            val nextActive = activeIdx.coerceIn(0, updated.lastIndex)
            updated to nextActive
        }
    }

    fun renameLayer(layerIdx: Int, newName: String) {
        mutateCurrentFrameLayers { layers, activeIdx ->
            val target = layers.getOrNull(layerIdx) ?: return@mutateCurrentFrameLayers layers to activeIdx
            val updated = layers.toMutableList().apply { set(layerIdx, target.copy(name = newName)) }
            updated to activeIdx
        }
    }

    fun moveLayerUp(layerIdx: Int) {
        mutateCurrentFrameLayers { layers, _ ->
            if (layerIdx >= layers.lastIndex) return@mutateCurrentFrameLayers layers to layerIdx
            val updated = layers.toMutableList()
            val item = updated.removeAt(layerIdx)
            updated.add(layerIdx + 1, item)
            updated to (layerIdx + 1)
        }
    }

    fun moveLayerDown(layerIdx: Int) {
        mutateCurrentFrameLayers { layers, _ ->
            if (layerIdx <= 0) return@mutateCurrentFrameLayers layers to layerIdx
            val updated = layers.toMutableList()
            val item = updated.removeAt(layerIdx)
            updated.add(layerIdx - 1, item)
            updated to (layerIdx - 1)
        }
    }

    fun mergeLayerDown(layerIdx: Int) {
        mutateCurrentFrameLayers { layers, _ ->
            if (layerIdx <= 0 || layerIdx > layers.lastIndex) return@mutateCurrentFrameLayers layers to layerIdx
            val upper = layers[layerIdx]
            val lower = layers[layerIdx - 1]
            val merged = lower.copy(
                strokes = lower.strokes + upper.strokes
            )
            val updated = layers.toMutableList().apply {
                set(layerIdx - 1, merged)
                removeAt(layerIdx)
            }
            updated to (layerIdx - 1)
        }
    }

    fun toggleLayerVisibility(layerIdx: Int) {
        mutateCurrentFrameLayers { layers, activeIdx ->
            val target = layers.getOrNull(layerIdx) ?: return@mutateCurrentFrameLayers layers to activeIdx
            val updated = layers.toMutableList().apply {
                set(layerIdx, target.copy(isVisible = !target.isVisible))
            }
            updated to activeIdx
        }
    }

    fun toggleLayerLock(layerIdx: Int) {
        mutateCurrentFrameLayers { layers, activeIdx ->
            val target = layers.getOrNull(layerIdx) ?: return@mutateCurrentFrameLayers layers to activeIdx
            val updated = layers.toMutableList().apply {
                set(layerIdx, target.copy(isLocked = !target.isLocked))
            }
            updated to activeIdx
        }
    }

    fun changeLayerOpacity(layerIdx: Int, opacity: Float) {
        mutateCurrentFrameLayers(recordUndo = false) { layers, activeIdx ->
            val target = layers.getOrNull(layerIdx) ?: return@mutateCurrentFrameLayers layers to activeIdx
            val updated = layers.toMutableList().apply {
                set(layerIdx, target.copy(opacity = opacity.coerceIn(0f, 1f)))
            }
            updated to activeIdx
        }
    }

    fun syncLayerStructureAcrossFrames() {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val curFrame = project.frames.getOrNull(state.currentFrameIndex) ?: return@update state
            val templateNames = curFrame.layers.map { it.name }
            val updatedFrames = project.frames.mapIndexed { idx, frame ->
                if (idx == state.currentFrameIndex) frame
                else {
                    val syncedLayers = templateNames.mapIndexed { lIdx, name ->
                        val existing = frame.layers.firstOrNull { it.name == name }
                            ?: frame.layers.getOrNull(lIdx)
                        existing?.copy(name = name) ?: StudioLayer(
                            id = UUID.randomUUID().toString(),
                            name = name
                        )
                    }
                    frame.copy(layers = syncedLayers)
                }
            }
            val updatedProj = project.copy(frames = updatedFrames)
            scheduleSave(updatedProj)
            state.copy(
                project = updatedProj,
                statusMessage = "Layer names synced across ${updatedFrames.size} timeline frames"
            )
        }
    }

    private fun mutateCurrentFrameLayers(
        recordUndo: Boolean = true,
        transform: (List<StudioLayer>, Int) -> Pair<List<StudioLayer>, Int>
    ) {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val curIdx = state.currentFrameIndex
            val curFrame = project.frames.getOrNull(curIdx) ?: return@update state
            val (newLayers, newActiveLayerIdx) = transform(curFrame.layers, state.activeLayerIndex)
            if (newLayers == curFrame.layers && newActiveLayerIdx == state.activeLayerIndex) return@update state
            val newFrames = project.frames.toMutableList().apply {
                set(curIdx, curFrame.copy(layers = newLayers))
            }
            val updatedProject = project.copy(frames = newFrames)
            scheduleSave(updatedProject)
            state.copy(
                project = updatedProject,
                activeLayerIndex = newActiveLayerIdx.coerceIn(0, newLayers.lastIndex.coerceAtLeast(0)),
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

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        val proj = _uiState.value.project
        if (proj != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                repository.saveProject(proj)
            }
        }
    }

    class Factory(
        private val repository: StudioRepository,
        private val projectId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnimationViewModel(repository, projectId) as T
        }
    }
}
