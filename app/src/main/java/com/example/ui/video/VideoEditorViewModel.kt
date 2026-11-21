package com.example.ui.video

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.KeyframeEasing
import com.example.data.model.OverlayKeyframe
import com.example.data.model.OverlayTargetType
import com.example.data.model.ProjectType
import com.example.data.model.StudioProject
import com.example.data.model.VideoClipItem
import com.example.data.model.VideoEffectType
import com.example.data.model.VideoTransitionType
import com.example.data.repository.StudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class VideoEditorUiState(
    val project: StudioProject? = null,
    val selectedClipIndex: Int = 0,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val selectedOverlayTarget: OverlayTargetType = OverlayTargetType.TEXT,
    val thumbnailsByClipId: Map<String, List<Bitmap>> = emptyMap(),
    val statusMessage: String? = null
)

class VideoEditorViewModel(
    private val context: Context,
    private val repository: StudioRepository,
    private val initialProjectId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoEditorUiState())
    val uiState: StateFlow<VideoEditorUiState> = _uiState.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = if (initialProjectId != null) {
                repository.getProject(initialProjectId)
            } else {
                null
            } ?: repository.createProject("Anime AMV Cut", ProjectType.VIDEO, 24)

            val firstClip = loaded.videoClips.firstOrNull()
            _uiState.update {
                it.copy(
                    project = loaded,
                    selectedClipIndex = 0,
                    playheadMs = firstClip?.trimStartMs ?: 0L
                )
            }

            // Lazily load lightweight thumbnails for existing clips
            loaded.videoClips.forEach { clip ->
                loadThumbnailsForClip(clip)
            }
        }
    }

    fun importVideoUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val meta = withContext(Dispatchers.IO) { extractVideoMetadata(uri) }
            val clip = VideoClipItem(
                id = UUID.randomUUID().toString(),
                uriString = uri.toString(),
                displayName = meta.first,
                durationMs = meta.second,
                trimStartMs = 0L,
                trimEndMs = meta.second
            )
            _uiState.update { state ->
                val project = state.project ?: return@update state
                val updatedClips = project.videoClips + clip
                val updatedProject = project.copy(videoClips = updatedClips)
                scheduleSave(updatedProject)
                state.copy(
                    project = updatedProject,
                    selectedClipIndex = updatedClips.lastIndex,
                    playheadMs = 0L,
                    statusMessage = "Imported \"${clip.displayName}\" (${formatMs(clip.durationMs)})"
                )
            }
            loadThumbnailsForClip(clip)
        }
    }

    private fun loadThumbnailsForClip(clip: VideoClipItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val thumbs = mutableListOf<Bitmap>()
            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, Uri.parse(clip.uriString))
                val count = 5
                val step = (clip.durationMs / count).coerceAtLeast(200L)
                for (i in 0 until count) {
                    val timeUs = (i * step) * 1000L
                    val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (raw != null) {
                        val scaled = Bitmap.createScaledBitmap(raw, 128, 72, true)
                        if (scaled !== raw) raw.recycle()
                        thumbs.add(scaled)
                    }
                }
            }
            runCatching { retriever.release() }
            if (thumbs.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(thumbnailsByClipId = state.thumbnailsByClipId + (clip.id to thumbs))
                }
            }
        }
    }

    private fun extractVideoMetadata(uri: Uri): Pair<String, Long> {
        var name = "Imported Clip"
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx) ?: name
                    }
                }
            }
        }
        var durationMs = 6000L
        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull()?.coerceAtLeast(500L) ?: 6000L
        }
        runCatching { retriever.release() }
        return name to durationMs
    }

    fun selectClip(index: Int) {
        _uiState.update { state ->
            val clips = state.project?.videoClips ?: return@update state
            val safeIdx = index.coerceIn(0, clips.lastIndex.coerceAtLeast(0))
            val clip = clips.getOrNull(safeIdx)
            state.copy(
                selectedClipIndex = safeIdx,
                playheadMs = clip?.trimStartMs ?: 0L,
                isPlaying = false
            )
        }
    }

    fun seekPlayhead(ms: Long) {
        _uiState.update { state ->
            val clip = state.project?.videoClips?.getOrNull(state.selectedClipIndex) ?: return@update state
            state.copy(playheadMs = ms.coerceIn(clip.trimStartMs, clip.trimEndMs))
        }
    }

    fun togglePlay() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }

    fun updateTrimRange(trimStartMs: Long, trimEndMs: Long) {
        mutateSelectedClip { clip ->
            val minGap = 250L
            val safeStart = trimStartMs.coerceIn(0L, (clip.durationMs - minGap).coerceAtLeast(0L))
            val safeEnd = trimEndMs.coerceIn((safeStart + minGap).coerceAtMost(clip.durationMs), clip.durationMs)
            clip.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
        }
        val clip = _uiState.value.project?.videoClips?.getOrNull(_uiState.value.selectedClipIndex)
        if (clip != null) {
            _uiState.update {
                it.copy(playheadMs = it.playheadMs.coerceIn(clip.trimStartMs, clip.trimEndMs))
            }
        }
    }

    /**
     * Splits the selected clip into two independent clips at the current playhead timestamp.
     */
    fun splitSelectedClipAtPlayhead() {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val idx = state.selectedClipIndex
            val clip = project.videoClips.getOrNull(idx) ?: return@update state
            val splitPt = state.playheadMs
            if (splitPt <= clip.trimStartMs + 150L || splitPt >= clip.trimEndMs - 150L) {
                return@update state.copy(statusMessage = "Move playhead inside trim bounds to split")
            }

            val firstHalf = clip.copy(
                id = UUID.randomUUID().toString(),
                displayName = "${clip.displayName} (1)",
                trimEndMs = splitPt,
                textKeyframes = KeyframeInterpolator.sliceKeyframesForRange(
                    clip.textKeyframes, clip.trimStartMs, splitPt, OverlayTargetType.TEXT
                ),
                imageKeyframes = KeyframeInterpolator.sliceKeyframesForRange(
                    clip.imageKeyframes, clip.trimStartMs, splitPt, OverlayTargetType.IMAGE
                )
            )
            val secondHalf = clip.copy(
                id = UUID.randomUUID().toString(),
                displayName = "${clip.displayName} (2)",
                trimStartMs = splitPt,
                textKeyframes = KeyframeInterpolator.sliceKeyframesForRange(
                    clip.textKeyframes, splitPt, clip.trimEndMs, OverlayTargetType.TEXT
                ),
                imageKeyframes = KeyframeInterpolator.sliceKeyframesForRange(
                    clip.imageKeyframes, splitPt, clip.trimEndMs, OverlayTargetType.IMAGE
                )
            )

            val updatedClips = project.videoClips.toMutableList().apply {
                set(idx, firstHalf)
                add(idx + 1, secondHalf)
            }
            val thumbs = state.thumbnailsByClipId[clip.id]
            val updatedThumbMap = if (thumbs != null) {
                state.thumbnailsByClipId + (firstHalf.id to thumbs) + (secondHalf.id to thumbs)
            } else {
                state.thumbnailsByClipId
            }

            val updatedProj = project.copy(videoClips = updatedClips)
            scheduleSave(updatedProj)
            state.copy(
                project = updatedProj,
                thumbnailsByClipId = updatedThumbMap,
                selectedClipIndex = idx + 1,
                playheadMs = secondHalf.trimStartMs,
                statusMessage = "Split clip at ${formatMs(splitPt)} into 2 clips"
            )
        }
    }

    /**
     * Cuts (removes) the portion of the clip after or before the playhead at the exact cut point.
     */
    fun cutClipAtPlayhead(keepBeforePlayhead: Boolean) {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val idx = state.selectedClipIndex
            val clip = project.videoClips.getOrNull(idx) ?: return@update state
            val cutPt = state.playheadMs
            if (cutPt <= clip.trimStartMs + 150L || cutPt >= clip.trimEndMs - 150L) {
                return@update state.copy(statusMessage = "Move playhead inside clip bounds to cut")
            }
            val newStart = if (keepBeforePlayhead) clip.trimStartMs else cutPt
            val newEnd = if (keepBeforePlayhead) cutPt else clip.trimEndMs
            val updatedClip = clip.copy(
                trimStartMs = newStart,
                trimEndMs = newEnd,
                textKeyframes = KeyframeInterpolator.sliceKeyframesForRange(
                    clip.textKeyframes, newStart, newEnd, OverlayTargetType.TEXT
                ),
                imageKeyframes = KeyframeInterpolator.sliceKeyframesForRange(
                    clip.imageKeyframes, newStart, newEnd, OverlayTargetType.IMAGE
                )
            )
            val updatedClips = project.videoClips.toMutableList().apply { set(idx, updatedClip) }
            val updatedProj = project.copy(videoClips = updatedClips)
            scheduleSave(updatedProj)
            state.copy(
                project = updatedProj,
                playheadMs = updatedClip.trimStartMs,
                statusMessage = if (keepBeforePlayhead) "Cut tail after ${formatMs(cutPt)}" else "Cut head before ${formatMs(cutPt)}"
            )
        }
    }

    fun deleteSelectedClip() {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val idx = state.selectedClipIndex
            if (idx !in project.videoClips.indices) return@update state
            val updatedClips = project.videoClips.toMutableList().apply { removeAt(idx) }
            val updatedProj = project.copy(videoClips = updatedClips)
            scheduleSave(updatedProj)
            val nextIdx = idx.coerceIn(0, updatedClips.lastIndex.coerceAtLeast(0))
            state.copy(
                project = updatedProj,
                selectedClipIndex = nextIdx,
                playheadMs = updatedClips.getOrNull(nextIdx)?.trimStartMs ?: 0L,
                isPlaying = false,
                statusMessage = "Removed clip from timeline"
            )
        }
    }

    fun setClipEffect(effect: VideoEffectType) {
        mutateSelectedClip { it.copy(effect = effect) }
    }

    fun setClipTransition(transition: VideoTransitionType) {
        mutateSelectedClip { it.copy(transitionIn = transition) }
    }

    fun setClipOverlayText(text: String) {
        mutateSelectedClip { it.copy(overlayText = text) }
        if (text.isNotBlank()) {
            _uiState.update { it.copy(selectedOverlayTarget = OverlayTargetType.TEXT) }
        }
    }

    fun selectOverlayTarget(target: OverlayTargetType) {
        _uiState.update { it.copy(selectedOverlayTarget = target) }
    }

    fun setMotionBlurEnabled(enabled: Boolean) {
        mutateSelectedClip { it.copy(motionBlurEnabled = enabled) }
        _uiState.update {
            it.copy(
                statusMessage = if (enabled) {
                    "Motion blur enabled for keyframe transitions"
                } else {
                    "Motion blur turned off"
                }
            )
        }
    }

    fun setMotionBlurIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.05f, 1f)
        mutateSelectedClip {
            it.copy(
                motionBlurEnabled = true,
                motionBlurIntensity = clamped
            )
        }
    }

    fun importImageOverlayUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val displayName = withContext(Dispatchers.IO) { queryDisplayName(uri) ?: "Image Sticker" }
            _uiState.update { state ->
                val project = state.project ?: return@update state
                val idx = state.selectedClipIndex
                val clip = project.videoClips.getOrNull(idx) ?: return@update state
                val updatedClip = clip.copy(
                    imageOverlayUri = uri.toString(),
                    imageOverlayName = displayName
                )
                val updatedClips = project.videoClips.toMutableList().apply { set(idx, updatedClip) }
                val updatedProj = project.copy(videoClips = updatedClips)
                scheduleSave(updatedProj)
                state.copy(
                    project = updatedProj,
                    selectedOverlayTarget = OverlayTargetType.IMAGE,
                    statusMessage = "Added image overlay \"$displayName\" — tap ♦ to keyframe"
                )
            }
        }
    }

    fun clearImageOverlay() {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val idx = state.selectedClipIndex
            val clip = project.videoClips.getOrNull(idx) ?: return@update state
            val updatedClip = clip.copy(
                imageOverlayUri = null,
                imageOverlayName = "",
                imageKeyframes = emptyList()
            )
            val updatedClips = project.videoClips.toMutableList().apply { set(idx, updatedClip) }
            val updatedProj = project.copy(videoClips = updatedClips)
            scheduleSave(updatedProj)
            state.copy(
                project = updatedProj,
                statusMessage = "Removed image overlay"
            )
        }
    }

    fun addOrUpdateKeyframeAtPlayhead() {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val idx = state.selectedClipIndex
            val clip = project.videoClips.getOrNull(idx) ?: return@update state
            val target = state.selectedOverlayTarget
            val t = state.playheadMs.coerceIn(clip.trimStartMs, clip.trimEndMs)
            val currentList = clip.keyframesFor(target)
            val matchIdx = KeyframeInterpolator.findKeyframeIndexAt(currentList, t)

            val updatedList = if (matchIdx >= 0) {
                currentList.toMutableList().apply {
                    val existing = this[matchIdx]
                    set(matchIdx, existing.copy(timeMs = t))
                }.sortedBy { it.timeMs }
            } else {
                val tf = KeyframeInterpolator.evaluate(currentList, t, target)
                (currentList + OverlayKeyframe(
                    id = UUID.randomUUID().toString(),
                    timeMs = t,
                    x = tf.x,
                    y = tf.y,
                    scale = tf.scale,
                    rotationDeg = tf.rotationDeg,
                    opacity = tf.opacity
                )).sortedBy { it.timeMs }
            }

            val updatedClip = clip.withKeyframesFor(target, updatedList)
            val updatedClips = project.videoClips.toMutableList().apply { set(idx, updatedClip) }
            val updatedProj = project.copy(videoClips = updatedClips)
            scheduleSave(updatedProj)
            state.copy(
                project = updatedProj,
                statusMessage = if (matchIdx >= 0) {
                    "Updated ${target.label} keyframe at ${formatMs(t)}"
                } else {
                    "Added ${target.label} keyframe at ${formatMs(t)}"
                }
            )
        }
    }

    fun updateTransformAtPlayhead(
        x: Float? = null,
        y: Float? = null,
        scale: Float? = null,
        rotationDeg: Float? = null,
        opacity: Float? = null,
        easing: KeyframeEasing? = null
    ) {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val idx = state.selectedClipIndex
            val clip = project.videoClips.getOrNull(idx) ?: return@update state
            val target = state.selectedOverlayTarget
            val t = state.playheadMs.coerceIn(clip.trimStartMs, clip.trimEndMs)
            val currentList = clip.keyframesFor(target)
            val matchIdx = KeyframeInterpolator.findKeyframeIndexAt(currentList, t)

            val updatedList = if (matchIdx >= 0) {
                val existing = currentList[matchIdx]
                val patched = existing.copy(
                    x = (x ?: existing.x).coerceIn(0f, 1f),
                    y = (y ?: existing.y).coerceIn(0f, 1f),
                    scale = (scale ?: existing.scale).coerceIn(0.25f, 2.5f),
                    rotationDeg = (rotationDeg ?: existing.rotationDeg).coerceIn(-180f, 180f),
                    opacity = (opacity ?: existing.opacity).coerceIn(0f, 1f),
                    easing = easing ?: existing.easing
                )
                currentList.toMutableList().apply { set(matchIdx, patched) }.sortedBy { it.timeMs }
            } else {
                val tf = KeyframeInterpolator.evaluate(currentList, t, target)
                val created = OverlayKeyframe(
                    id = UUID.randomUUID().toString(),
                    timeMs = t,
                    x = (x ?: tf.x).coerceIn(0f, 1f),
                    y = (y ?: tf.y).coerceIn(0f, 1f),
                    scale = (scale ?: tf.scale).coerceIn(0.25f, 2.5f),
                    rotationDeg = (rotationDeg ?: tf.rotationDeg).coerceIn(-180f, 180f),
                    opacity = (opacity ?: tf.opacity).coerceIn(0f, 1f),
                    easing = easing ?: KeyframeEasing.EASE_IN_OUT
                )
                (currentList + created).sortedBy { it.timeMs }
            }

            val updatedClip = clip.withKeyframesFor(target, updatedList)
            val updatedClips = project.videoClips.toMutableList().apply { set(idx, updatedClip) }
            val updatedProj = project.copy(videoClips = updatedClips)
            scheduleSave(updatedProj)
            state.copy(project = updatedProj)
        }
    }

    fun deleteKeyframeAtPlayhead() {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val idx = state.selectedClipIndex
            val clip = project.videoClips.getOrNull(idx) ?: return@update state
            val target = state.selectedOverlayTarget
            val currentList = clip.keyframesFor(target)
            val matchIdx = KeyframeInterpolator.findKeyframeIndexAt(currentList, state.playheadMs)
            if (matchIdx < 0) return@update state
            val updatedList = currentList.toMutableList().apply { removeAt(matchIdx) }
            val updatedClip = clip.withKeyframesFor(target, updatedList)
            val updatedClips = project.videoClips.toMutableList().apply { set(idx, updatedClip) }
            val updatedProj = project.copy(videoClips = updatedClips)
            scheduleSave(updatedProj)
            state.copy(
                project = updatedProj,
                statusMessage = "Deleted ${target.label} keyframe"
            )
        }
    }

    fun deleteKeyframeById(keyframeId: String) {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val idx = state.selectedClipIndex
            val clip = project.videoClips.getOrNull(idx) ?: return@update state
            val target = state.selectedOverlayTarget
            val updatedList = clip.keyframesFor(target).filterNot { it.id == keyframeId }
            val updatedClip = clip.withKeyframesFor(target, updatedList)
            val updatedClips = project.videoClips.toMutableList().apply { set(idx, updatedClip) }
            val updatedProj = project.copy(videoClips = updatedClips)
            scheduleSave(updatedProj)
            state.copy(
                project = updatedProj,
                statusMessage = "Removed keyframe"
            )
        }
    }

    fun clearKeyframesForActiveTarget() {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val idx = state.selectedClipIndex
            val clip = project.videoClips.getOrNull(idx) ?: return@update state
            val target = state.selectedOverlayTarget
            val updatedClip = clip.withKeyframesFor(target, emptyList())
            val updatedClips = project.videoClips.toMutableList().apply { set(idx, updatedClip) }
            val updatedProj = project.copy(videoClips = updatedClips)
            scheduleSave(updatedProj)
            state.copy(
                project = updatedProj,
                statusMessage = "Cleared all ${target.label} keyframes"
            )
        }
    }

    fun jumpToKeyframe(keyframe: OverlayKeyframe) {
        _uiState.update { state ->
            val clip = state.project?.videoClips?.getOrNull(state.selectedClipIndex) ?: return@update state
            state.copy(
                playheadMs = keyframe.timeMs.coerceIn(clip.trimStartMs, clip.trimEndMs),
                isPlaying = false
            )
        }
    }

    fun jumpToAdjacentKeyframe(direction: Int) {
        _uiState.update { state ->
            val clip = state.project?.videoClips?.getOrNull(state.selectedClipIndex) ?: return@update state
            val sorted = clip.keyframesFor(state.selectedOverlayTarget).sortedBy { it.timeMs }
            if (sorted.isEmpty()) return@update state
            val targetKf = if (direction < 0) {
                sorted.lastOrNull { it.timeMs < state.playheadMs - KeyframeInterpolator.KEYFRAME_SNAP_MS }
            } else {
                sorted.firstOrNull { it.timeMs > state.playheadMs + KeyframeInterpolator.KEYFRAME_SNAP_MS }
            } ?: return@update state
            state.copy(
                playheadMs = targetKf.timeMs.coerceIn(clip.trimStartMs, clip.trimEndMs),
                isPlaying = false
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx)
                    }
                }
            }
        }
        return name
    }

    private fun VideoClipItem.keyframesFor(target: OverlayTargetType): List<OverlayKeyframe> {
        return when (target) {
            OverlayTargetType.TEXT -> textKeyframes
            OverlayTargetType.IMAGE -> imageKeyframes
        }
    }

    private fun VideoClipItem.withKeyframesFor(
        target: OverlayTargetType,
        keyframes: List<OverlayKeyframe>
    ): VideoClipItem {
        return when (target) {
            OverlayTargetType.TEXT -> copy(textKeyframes = keyframes)
            OverlayTargetType.IMAGE -> copy(imageKeyframes = keyframes)
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun mutateSelectedClip(transform: (VideoClipItem) -> VideoClipItem) {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val idx = state.selectedClipIndex
            val clip = project.videoClips.getOrNull(idx) ?: return@update state
            val updatedClips = project.videoClips.toMutableList().apply { set(idx, transform(clip)) }
            val updatedProj = project.copy(videoClips = updatedClips)
            scheduleSave(updatedProj)
            state.copy(project = updatedProj)
        }
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
        _uiState.value.thumbnailsByClipId.values.flatten().forEach {
            runCatching { if (!it.isRecycled) it.recycle() }
        }
    }

    class Factory(
        private val context: Context,
        private val repository: StudioRepository,
        private val projectId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VideoEditorViewModel(context.applicationContext, repository, projectId) as T
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val tenths = ((ms % 1000L) / 100L).coerceIn(0L, 9L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d.%d".format(min, sec, tenths)
}
