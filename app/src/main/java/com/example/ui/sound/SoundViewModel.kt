package com.example.ui.sound

import android.media.MediaMetadataRetriever
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEngine
import com.example.data.model.AudioClipItem
import com.example.data.model.ProjectType
import com.example.data.model.StudioProject
import com.example.data.repository.StudioRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class SoundUiState(
    val project: StudioProject? = null,
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val playingClipId: String? = null,
    val statusMessage: String? = null
)

class SoundViewModel(
    private val repository: StudioRepository,
    private val soundEngine: SoundEngine,
    private val initialProjectId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(SoundUiState())
    val uiState: StateFlow<SoundUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = if (initialProjectId != null) {
                repository.getProject(initialProjectId)
            } else {
                null
            } ?: repository.createProject("Anime Soundboard", ProjectType.SOUND, 12)
            _uiState.update { it.copy(project = loaded) }
        }
    }

    fun triggerSfx(presetId: String, label: String, addToTrack: Boolean) {
        viewModelScope.launch {
            soundEngine.playAnimeSfx(presetId)
        }
        if (addToTrack) {
            val clip = AudioClipItem(
                id = UUID.randomUUID().toString(),
                name = label,
                trackType = "SFX",
                sfxPresetId = presetId,
                durationMs = 800L,
                volume = 0.9f
            )
            addClip(clip)
        }
    }

    fun startVoiceRecording() {
        val outFile = File(repository.getAudioDir(), "voice_${System.currentTimeMillis()}.m4a")
        val started = soundEngine.startRecording(outFile)
        if (!started) {
            _uiState.update { it.copy(statusMessage = "Unable to start microphone recorder on this device") }
            return
        }
        _uiState.update { it.copy(isRecording = true, recordingSeconds = 0) }
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
            }
        }
    }

    fun stopVoiceRecording() {
        timerJob?.cancel()
        val recordedFile = soundEngine.stopRecording()
        _uiState.update { it.copy(isRecording = false, recordingSeconds = 0) }
        if (recordedFile == null) {
            _uiState.update { it.copy(statusMessage = "Recording discarded (no audio captured)") }
            return
        }
        val dur = extractDurationMs(recordedFile)
        val count = (_uiState.value.project?.audioClips?.count { it.trackType == "VOICE" } ?: 0) + 1
        val clip = AudioClipItem(
            id = UUID.randomUUID().toString(),
            name = "Voice Take #$count",
            trackType = "VOICE",
            filePath = recordedFile.absolutePath,
            durationMs = dur,
            trimStartMs = 0L,
            trimEndMs = dur
        )
        addClip(clip)
        _uiState.update { it.copy(statusMessage = "Saved ${clip.name}") }
    }

    private fun extractDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        val dur = runCatching {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }.getOrNull()
        runCatching { retriever.release() }
        return (dur ?: 1500L).coerceAtLeast(500L)
    }

    fun togglePlayClip(clip: AudioClipItem) {
        if (_uiState.value.playingClipId == clip.id) {
            soundEngine.stopPlayback()
            _uiState.update { it.copy(playingClipId = null) }
            return
        }
        _uiState.update { it.copy(playingClipId = clip.id) }
        if (clip.sfxPresetId != null) {
            viewModelScope.launch {
                soundEngine.playAnimeSfx(clip.sfxPresetId, clip.volume)
                _uiState.update { it.copy(playingClipId = null) }
            }
        } else if (clip.filePath != null) {
            soundEngine.playAudioFile(clip.filePath, clip.volume) {
                _uiState.update { it.copy(playingClipId = null) }
            }
        } else {
            _uiState.update { it.copy(playingClipId = null) }
        }
    }

    fun updateClipVolume(clipId: String, volume: Float) {
        mutateClips { clips ->
            clips.map { if (it.id == clipId) it.copy(volume = volume.coerceIn(0f, 1f)) else it }
        }
    }

    fun deleteClip(clipId: String) {
        if (_uiState.value.playingClipId == clipId) {
            soundEngine.stopPlayback()
            _uiState.update { it.copy(playingClipId = null) }
        }
        mutateClips { clips -> clips.filterNot { it.id == clipId } }
    }

    fun clearStatusMessage() = _uiState.update { it.copy(statusMessage = null) }

    private fun addClip(clip: AudioClipItem) {
        mutateClips { it + clip }
    }

    private fun mutateClips(transform: (List<AudioClipItem>) -> List<AudioClipItem>) {
        _uiState.update { state ->
            val project = state.project ?: return@update state
            val updated = project.copy(audioClips = transform(project.audioClips))
            scheduleSave(updated)
            state.copy(project = updated)
        }
    }

    private fun scheduleSave(project: StudioProject) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(200)
            repository.saveProject(project)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        soundEngine.release()
    }

    class Factory(
        private val repository: StudioRepository,
        private val soundEngine: SoundEngine,
        private val projectId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SoundViewModel(repository, soundEngine, projectId) as T
        }
    }
}
