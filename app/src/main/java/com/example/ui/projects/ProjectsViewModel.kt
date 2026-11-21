package com.example.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ProjectType
import com.example.data.model.StudioProject
import com.example.data.remote.CloudBackupItem
import com.example.data.remote.CloudBackupRepository
import com.example.data.repository.StudioRepository
import com.example.export.BatchExportFormat
import com.example.export.BatchProjectExporter
import com.example.ui.auth.authStateFlow
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudUserSummary(
    val uid: String,
    val displayName: String,
    val email: String
)

private data class CloudSyncSlice(
    val user: CloudUserSummary? = null,
    val backups: List<CloudBackupItem> = emptyList(),
    val observeError: String? = null
)

data class BatchOperationsState(
    val selectedProjectIds: Set<String> = emptySet(),
    val isDialogOpen: Boolean = false,
    val activeTab: Int = 0, // 0 = Batch Export, 1 = Cloud Backup
    val exportFormat: BatchExportFormat = BatchExportFormat.GIF,
    val resolutionHeight: Int = 480,
    val isBatchExporting: Boolean = false,
    val batchProgress: Float = 0f,
    val batchStatusText: String = "",
    val lastExportedFileNames: List<String> = emptyList(),
    val lastSkippedNotes: List<String> = emptyList(),
    val isCloudBackingUp: Boolean = false,
    val cloudBackupProgressText: String = "",
    val cloudStatusMessage: String? = null,
    val cloudErrorMessage: String? = null,
    val isCloudConfigured: Boolean = false
)

data class ProjectsUiState(
    val projects: List<StudioProject> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: ProjectType? = null,
    val batch: BatchOperationsState = BatchOperationsState(),
    val currentUser: CloudUserSummary? = null,
    val cloudBackups: List<CloudBackupItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModel(
    private val repository: StudioRepository,
    private val cloudBackupRepository: CloudBackupRepository? = null
) : ViewModel() {

    private val searchFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow<ProjectType?>(null)
    private val batchStateFlow = MutableStateFlow(
        BatchOperationsState(isCloudConfigured = cloudBackupRepository != null)
    )

    private val authUserFlow: Flow<CloudUserSummary?> = if (cloudBackupRepository == null) {
        flowOf(null)
    } else {
        runCatching {
            authStateFlow(Firebase.auth).map { firebaseUser ->
                firebaseUser?.let {
                    CloudUserSummary(
                        uid = it.uid,
                        displayName = it.displayName?.ifBlank { null }
                            ?: it.email?.substringBefore("@")
                            ?: "Ronin Creator",
                        email = it.email ?: ""
                    )
                }
            }
        }.getOrElse { flowOf(null) }
    }

    private val cloudSyncFlow: Flow<CloudSyncSlice> = authUserFlow.flatMapLatest { user ->
        val repo = cloudBackupRepository
        if (repo == null || user == null || user.uid.isBlank()) {
            flowOf(CloudSyncSlice(user = user, backups = emptyList(), observeError = null))
        } else {
            repo.observeCloudBackups(user.uid)
                .map { backups ->
                    CloudSyncSlice(user = user, backups = backups, observeError = null)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    emit(
                        CloudSyncSlice(
                            user = user,
                            backups = emptyList(),
                            observeError = rootError.localizedMessage ?: "Failed to sync cloud backups"
                        )
                    )
                }
        }
    }

    val uiState: StateFlow<ProjectsUiState> = combine(
        repository.allProjects,
        searchFlow,
        filterFlow,
        batchStateFlow,
        cloudSyncFlow
    ) { all, query, filter, batch, cloudSlice ->
        val filtered = all.filter { proj ->
            val matchesFilter = filter == null || proj.type == filter
            val matchesQuery = query.isBlank() || proj.title.contains(query.trim(), ignoreCase = true)
            matchesFilter && matchesQuery
        }
        val mergedError = batch.cloudErrorMessage ?: cloudSlice.observeError
        ProjectsUiState(
            projects = filtered,
            searchQuery = query,
            selectedFilter = filter,
            batch = batch.copy(cloudErrorMessage = mergedError),
            currentUser = cloudSlice.user,
            cloudBackups = cloudSlice.backups
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ProjectsUiState(
            batch = BatchOperationsState(isCloudConfigured = cloudBackupRepository != null)
        )
    )

    fun setSearchQuery(q: String) {
        searchFlow.value = q
    }

    fun setFilter(type: ProjectType?) {
        filterFlow.value = type
    }

    fun createProject(title: String, type: ProjectType, fps: Int, onCreated: (StudioProject) -> Unit) {
        viewModelScope.launch {
            val created = repository.createProject(title, type, fps)
            onCreated(created)
        }
    }

    fun renameProject(project: StudioProject, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.saveProject(project.copy(title = newTitle.trim()))
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            repository.duplicateProject(projectId)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            batchStateFlow.update {
                it.copy(selectedProjectIds = it.selectedProjectIds - projectId)
            }
        }
    }

    fun openBatchDialog(initialTab: Int = 0, preselectProjectIds: Set<String>? = null) {
        batchStateFlow.update { current ->
            val currentVisibleIds = uiState.value.projects.map { it.id }.toSet()
            val sanitizedExisting = current.selectedProjectIds.intersect(currentVisibleIds)
            val resolvedSelection = preselectProjectIds ?: if (sanitizedExisting.isNotEmpty()) {
                sanitizedExisting
            } else {
                currentVisibleIds
            }
            current.copy(
                isDialogOpen = true,
                activeTab = initialTab.coerceIn(0, 1),
                selectedProjectIds = resolvedSelection,
                cloudErrorMessage = null,
                cloudStatusMessage = null
            )
        }
    }

    fun closeBatchDialog() {
        batchStateFlow.update {
            it.copy(
                isDialogOpen = false,
                cloudErrorMessage = null,
                cloudStatusMessage = null
            )
        }
    }

    fun setBatchDialogTab(tab: Int) {
        batchStateFlow.update {
            it.copy(
                activeTab = tab.coerceIn(0, 1),
                cloudErrorMessage = null,
                cloudStatusMessage = null
            )
        }
    }

    fun toggleProjectSelection(projectId: String) {
        batchStateFlow.update { current ->
            val updated = if (projectId in current.selectedProjectIds) {
                current.selectedProjectIds - projectId
            } else {
                current.selectedProjectIds + projectId
            }
            current.copy(selectedProjectIds = updated)
        }
    }

    fun selectAllVisibleProjects() {
        val visibleIds = uiState.value.projects.map { it.id }.toSet()
        batchStateFlow.update { it.copy(selectedProjectIds = visibleIds) }
    }

    fun clearProjectSelection() {
        batchStateFlow.update { it.copy(selectedProjectIds = emptySet()) }
    }

    fun setBatchExportFormat(format: BatchExportFormat) {
        batchStateFlow.update { it.copy(exportFormat = format) }
    }

    fun setBatchResolutionHeight(height: Int) {
        batchStateFlow.update { it.copy(resolutionHeight = height.coerceIn(360, 720)) }
    }

    fun clearBatchMessages() {
        batchStateFlow.update {
            it.copy(
                cloudStatusMessage = null,
                cloudErrorMessage = null,
                lastExportedFileNames = emptyList(),
                lastSkippedNotes = emptyList(),
                batchStatusText = ""
            )
        }
    }

    fun runBatchExport() {
        val currentBatch = batchStateFlow.value
        val currentProjects = uiState.value.projects
        if (currentBatch.isBatchExporting) return
        val selectedProjects = currentProjects.filter { it.id in currentBatch.selectedProjectIds }
        if (selectedProjects.isEmpty()) {
            batchStateFlow.update { it.copy(batchStatusText = "Select at least one project to export.") }
            return
        }

        viewModelScope.launch {
            batchStateFlow.update {
                it.copy(
                    isBatchExporting = true,
                    batchProgress = 0f,
                    batchStatusText = "Preparing batch export...",
                    lastExportedFileNames = emptyList(),
                    lastSkippedNotes = emptyList()
                )
            }
            val result = BatchProjectExporter.exportProjects(
                projects = selectedProjects,
                format = currentBatch.exportFormat,
                resolutionHeight = currentBatch.resolutionHeight,
                exportsDir = repository.getExportsDir()
            ) { current, total, title, fraction ->
                batchStateFlow.update {
                    it.copy(
                        batchProgress = fraction,
                        batchStatusText = "Exporting $current/$total: $title"
                    )
                }
            }
            val summary = when {
                result.exportedFiles.isNotEmpty() && result.skippedMessages.isEmpty() ->
                    "Exported ${result.exportedFiles.size} file(s) to studio exports."
                result.exportedFiles.isNotEmpty() ->
                    "Exported ${result.exportedFiles.size} file(s); skipped ${result.skippedMessages.size}."
                else -> "No files exported. Check format compatibility below."
            }
            batchStateFlow.update {
                it.copy(
                    isBatchExporting = false,
                    batchProgress = 1f,
                    batchStatusText = summary,
                    lastExportedFileNames = result.exportedFiles.map { file -> file.name },
                    lastSkippedNotes = result.skippedMessages
                )
            }
        }
    }

    fun backupSelectedToCloud() {
        val repo = cloudBackupRepository
        if (repo == null) {
            batchStateFlow.update { it.copy(cloudErrorMessage = "Cloud backup service is unavailable.") }
            return
        }
        val currentBatch = batchStateFlow.value
        val currentProjects = uiState.value.projects
        if (currentBatch.isCloudBackingUp) return
        val selectedProjects = currentProjects.filter { it.id in currentBatch.selectedProjectIds }
        if (selectedProjects.isEmpty()) {
            batchStateFlow.update { it.copy(cloudErrorMessage = "Select at least one project to back up.") }
            return
        }

        viewModelScope.launch {
            batchStateFlow.update {
                it.copy(
                    isCloudBackingUp = true,
                    cloudBackupProgressText = "Uploading ${selectedProjects.size} project(s)...",
                    cloudStatusMessage = null,
                    cloudErrorMessage = null
                )
            }
            val result = repo.backupProjects(selectedProjects) { current, total, title ->
                batchStateFlow.update {
                    it.copy(cloudBackupProgressText = "Backing up $current/$total: $title")
                }
            }
            result.onSuccess { count ->
                batchStateFlow.update {
                    it.copy(
                        isCloudBackingUp = false,
                        cloudBackupProgressText = "",
                        cloudStatusMessage = "Backed up $count project(s) to Firestore cloud.",
                        cloudErrorMessage = null
                    )
                }
            }.onFailure { err ->
                batchStateFlow.update {
                    it.copy(
                        isCloudBackingUp = false,
                        cloudBackupProgressText = "",
                        cloudErrorMessage = err.localizedMessage ?: "Cloud backup failed. Check connection or file size."
                    )
                }
            }
        }
    }

    fun restoreCloudBackup(item: CloudBackupItem) {
        val restored = item.restoredProject ?: return
        viewModelScope.launch {
            repository.saveProject(restored.copy(updatedAt = System.currentTimeMillis()))
            batchStateFlow.update {
                it.copy(
                    cloudStatusMessage = "Restored \"${item.title}\" to local Room library.",
                    cloudErrorMessage = null
                )
            }
        }
    }

    fun deleteCloudBackup(projectId: String) {
        val repo = cloudBackupRepository ?: return
        viewModelScope.launch {
            val res = repo.deleteCloudBackup(projectId)
            res.onSuccess {
                batchStateFlow.update {
                    it.copy(
                        cloudStatusMessage = "Removed backup from cloud.",
                        cloudErrorMessage = null
                    )
                }
            }.onFailure { err ->
                batchStateFlow.update {
                    it.copy(cloudErrorMessage = err.localizedMessage ?: "Failed to remove cloud backup.")
                }
            }
        }
    }

    fun reportCloudError(message: String) {
        batchStateFlow.update { it.copy(cloudErrorMessage = message, cloudStatusMessage = null) }
    }

    fun notifyAuthStateChanged() {
        batchStateFlow.update {
            it.copy(
                cloudErrorMessage = null,
                cloudStatusMessage = null
            )
        }
    }

    class Factory(
        private val repository: StudioRepository,
        private val cloudBackupRepository: CloudBackupRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProjectsViewModel(repository, cloudBackupRepository) as T
        }
    }
}
