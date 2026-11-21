package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.DeviceMemoryInfo
import com.example.data.model.ProjectType
import com.example.data.model.StudioProject
import com.example.data.repository.StudioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val projects: List<StudioProject> = emptyList(),
    val memoryInfo: DeviceMemoryInfo = DeviceMemoryInfo(0L, 0L, true),
    val isLoading: Boolean = true
)

class DashboardViewModel(
    private val repository: StudioRepository
) : ViewModel() {

    private val memoryFlow = MutableStateFlow(repository.getDeviceMemoryInfo())

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.allProjects,
        memoryFlow
    ) { projects, mem ->
        DashboardUiState(
            projects = projects,
            memoryInfo = mem,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureStarterData()
            memoryFlow.value = repository.getDeviceMemoryInfo()
        }
    }

    fun refreshMemoryStats() {
        memoryFlow.value = repository.getDeviceMemoryInfo()
    }

    fun createProject(
        title: String,
        type: ProjectType,
        fps: Int,
        onCreated: (StudioProject) -> Unit
    ) {
        viewModelScope.launch {
            val created = repository.createProject(title, type, fps)
            onCreated(created)
        }
    }

    class Factory(private val repository: StudioRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(repository) as T
        }
    }
}
