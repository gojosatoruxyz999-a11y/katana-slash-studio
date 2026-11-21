package com.example.data.repository

import android.app.ActivityManager
import android.content.Context
import com.example.data.local.ColorPaletteStore
import com.example.data.local.DefaultNeonPalettes
import com.example.data.local.NeonPalettePreset
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.local.StarterProjectFactory
import com.example.data.model.DeviceMemoryInfo
import com.example.data.model.ProjectType
import com.example.data.model.StudioProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class StudioRepository(
    private val context: Context,
    private val projectDao: ProjectDao
) {
    private val paletteStore by lazy { ColorPaletteStore(context) }

    val favoriteNeonColors: Flow<List<Long>> = paletteStore.favoriteColors
    val activePalettePresetId: Flow<String> = paletteStore.activePresetId
    val palettePresets: Flow<List<NeonPalettePreset>> = combine(
        paletteStore.favoriteColors,
        paletteStore.customPresets
    ) { favorites, custom ->
        listOf(
            NeonPalettePreset(
                id = DefaultNeonPalettes.PRESET_FAVORITES,
                name = "Saved Neon",
                colors = favorites,
                isBuiltIn = true
            )
        ) + custom + DefaultNeonPalettes.builtInPresets
    }

    val allProjects: Flow<List<StudioProject>> = projectDao.observeAllProjects()
        .map { entities -> entities.map { it.toDomain() } }

    fun observeProject(projectId: String): Flow<StudioProject?> =
        projectDao.observeProjectById(projectId).map { it?.toDomain() }

    suspend fun ensureStarterData() = withContext(Dispatchers.IO) {
        if (projectDao.getProjectCount() == 0) {
            val animStarter = StarterProjectFactory.createStarterKatanaProject()
            val drawStarter = StarterProjectFactory.createStarterDrawingSketchbookProject()
            projectDao.upsertProject(ProjectEntity.fromDomain(drawStarter))
            projectDao.upsertProject(ProjectEntity.fromDomain(animStarter))
        }
    }

    suspend fun getProject(projectId: String): StudioProject? = withContext(Dispatchers.IO) {
        projectDao.getProjectById(projectId)?.toDomain()
    }

    suspend fun saveProject(project: StudioProject) = withContext(Dispatchers.IO) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        projectDao.upsertProject(ProjectEntity.fromDomain(updated))
    }

    suspend fun createProject(title: String, type: ProjectType, fps: Int = 12): StudioProject =
        withContext(Dispatchers.IO) {
            val project = StarterProjectFactory.createNewBlankProject(
                title = title.ifBlank { "Untitled ${type.label}" },
                type = type,
                fps = fps
            )
            projectDao.upsertProject(ProjectEntity.fromDomain(project))
            project
        }

    suspend fun duplicateProject(projectId: String): StudioProject? = withContext(Dispatchers.IO) {
        val source = projectDao.getProjectById(projectId)?.toDomain() ?: return@withContext null
        val copy = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            title = "${source.title} (Copy)",
            updatedAt = System.currentTimeMillis()
        )
        projectDao.upsertProject(ProjectEntity.fromDomain(copy))
        copy
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        projectDao.deleteProjectById(projectId)
    }

    fun getExportsDir(): File {
        return File(context.filesDir, "exports").apply { mkdirs() }
    }

    fun getAudioDir(): File {
        return File(context.filesDir, "audio").apply { mkdirs() }
    }

    suspend fun saveFavoriteNeonColor(colorArgb: Long) = paletteStore.saveFavoriteColor(colorArgb)

    suspend fun removeFavoriteNeonColor(colorArgb: Long) = paletteStore.removeFavoriteColor(colorArgb)

    suspend fun selectPalettePreset(presetId: String) = paletteStore.selectPreset(presetId)

    suspend fun saveCustomPalettePreset(name: String, colors: List<Long>): NeonPalettePreset =
        paletteStore.saveCustomPreset(name, colors)

    suspend fun deleteCustomPalettePreset(presetId: String) = paletteStore.deleteCustomPreset(presetId)

    suspend fun resetDefaultNeonColors() = paletteStore.resetDefaultFavorites()

    fun getDeviceMemoryInfo(): DeviceMemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val totalMb = memInfo.totalMem / (1024L * 1024L)
        return DeviceMemoryInfo(
            availableMb = availMb,
            totalMb = totalMb,
            isLowRamClass = totalMb <= 4600L || memInfo.lowMemory
        )
    }
}
