package com.example.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ProjectType
import com.example.data.model.StudioProject
import com.example.data.repository.StudioRepository
import com.example.export.GifEncoder
import com.example.export.Mp4Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    GIF("Animated GIF", "gif", "image/gif"),
    MP4("H.264 MP4 Video", "mp4", "video/mp4")
}

enum class ExportResolution(val label: String, val width: Int, val height: Int) {
    SAFE_480P("480p (4GB RAM Safe)", 480, 300),
    HD_720P("720p HD", 720, 450)
}

data class ExportUiState(
    val exportableProjects: List<StudioProject> = emptyList(),
    val selectedProjectId: String? = null,
    val selectedFormat: ExportFormat = ExportFormat.GIF,
    val selectedResolution: ExportResolution = ExportResolution.SAFE_480P,
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val exportedFiles: List<File> = emptyList(),
    val statusMessage: String? = null
)

class ExportViewModel(
    private val repository: StudioRepository,
    private val initialProjectId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allProjects.collect { projects ->
                val exportable = projects.filter {
                    (it.type == ProjectType.ANIMATION || it.type == ProjectType.DRAWING) && it.frames.isNotEmpty()
                }
                _uiState.update { state ->
                    val chosenId = state.selectedProjectId
                        ?: initialProjectId?.takeIf { id -> exportable.any { it.id == id } }
                        ?: exportable.firstOrNull()?.id
                    state.copy(
                        exportableProjects = exportable,
                        selectedProjectId = chosenId
                    )
                }
            }
        }
        refreshExportedFiles()
    }

    fun selectProject(id: String) = _uiState.update { it.copy(selectedProjectId = id) }
    fun selectFormat(format: ExportFormat) = _uiState.update { it.copy(selectedFormat = format) }
    fun selectResolution(res: ExportResolution) = _uiState.update { it.copy(selectedResolution = res) }
    fun clearStatusMessage() = _uiState.update { it.copy(statusMessage = null) }

    fun refreshExportedFiles() {
        val files = repository.getExportsDir()
            .listFiles()
            ?.filter { it.isFile && (it.extension == "gif" || it.extension == "mp4" || it.extension == "png") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        _uiState.update { it.copy(exportedFiles = files) }
    }

    fun deleteExportedFile(file: File) {
        runCatching { file.delete() }
        refreshExportedFiles()
    }

    fun startExport() {
        val state = _uiState.value
        val project = state.exportableProjects.firstOrNull { it.id == state.selectedProjectId } ?: return
        if (state.isExporting) return

        val safeTitle = project.title.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "ronin_cut" }
        val ext = state.selectedFormat.ext
        val outFile = File(repository.getExportsDir(), "${safeTitle}_${System.currentTimeMillis()}.$ext")

        _uiState.update { it.copy(isExporting = true, progress = 0f, statusMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (state.selectedFormat) {
                        ExportFormat.GIF -> GifEncoder.exportFramesToGif(
                            frames = project.frames,
                            fps = project.fps,
                            width = state.selectedResolution.width,
                            height = state.selectedResolution.height,
                            outFile = outFile
                        ) { p -> _uiState.update { it.copy(progress = p) } }

                        ExportFormat.MP4 -> Mp4Exporter.exportFramesToMp4(
                            frames = project.frames,
                            fps = project.fps,
                            width = state.selectedResolution.width,
                            height = state.selectedResolution.height,
                            outFile = outFile
                        ) { p -> _uiState.update { it.copy(progress = p) } }
                    }
                }
            }
            refreshExportedFiles()
            result.fold(
                onSuccess = { file ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            progress = 1f,
                            statusMessage = "Exported ${file.name} (${file.length() / 1024} KB)"
                        )
                    }
                },
                onFailure = { err ->
                    runCatching { outFile.delete() }
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            progress = 0f,
                            statusMessage = "Export failed (${err.localizedMessage ?: "encoder unavailable"}). Try GIF format."
                        )
                    }
                }
            )
        }
    }

    class Factory(
        private val repository: StudioRepository,
        private val projectId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExportViewModel(repository, projectId) as T
        }
    }
}
