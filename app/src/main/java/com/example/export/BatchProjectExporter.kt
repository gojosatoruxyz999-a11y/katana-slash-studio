package com.example.export

import com.example.data.local.StudioJsonCodec
import com.example.data.model.ProjectType
import com.example.data.model.StudioProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class BatchExportFormat(val label: String, val extension: String) {
    GIF("Animated GIF", "gif"),
    MP4("H.264 MP4", "mp4"),
    JSON_BUNDLE("Project JSON", "ronin.json")
}

data class BatchExportResult(
    val exportedFiles: List<File>,
    val skippedMessages: List<String>
)

object BatchProjectExporter {

    suspend fun exportProjects(
        projects: List<StudioProject>,
        format: BatchExportFormat,
        resolutionHeight: Int,
        exportsDir: File,
        onProgress: (currentIndex: Int, total: Int, projectTitle: String, overallFraction: Float) -> Unit
    ): BatchExportResult = withContext(Dispatchers.IO) {
        exportsDir.mkdirs()
        val exportedFiles = mutableListOf<File>()
        val skippedMessages = mutableListOf<String>()
        val total = projects.size
        if (total == 0) {
            return@withContext BatchExportResult(emptyList(), emptyList())
        }

        val safeHeight = resolutionHeight.coerceIn(360, 720)
        val safeWidth = ((safeHeight * 16) / 9).let { (it / 2) * 2 }
        val batchStamp = System.currentTimeMillis()

        projects.forEachIndexed { index, project ->
            val baseFraction = index.toFloat() / total.toFloat()
            onProgress(index + 1, total, project.title, baseFraction)

            val cleanName = project.title
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .trim('_')
                .ifBlank { "ronin_project" }
                .take(40)
            val fileName = "${cleanName}_${batchStamp}_${index + 1}.${format.extension}"
            val outFile = File(exportsDir, fileName)

            when (format) {
                BatchExportFormat.GIF, BatchExportFormat.MP4 -> {
                    val supportsFrameRender =
                        project.type == ProjectType.ANIMATION || project.type == ProjectType.DRAWING
                    if (!supportsFrameRender) {
                        skippedMessages.add(
                            "${project.title}: ${project.type.label} projects export via Project JSON format"
                        )
                    } else if (project.frames.isEmpty()) {
                        skippedMessages.add("${project.title}: No drawable frames to export")
                    } else {
                        runCatching {
                            if (format == BatchExportFormat.GIF) {
                                GifEncoder.exportFramesToGif(
                                    frames = project.frames,
                                    fps = project.fps,
                                    width = safeWidth,
                                    height = safeHeight,
                                    outFile = outFile
                                ) { itemFraction ->
                                    val combined = (index + itemFraction) / total.toFloat()
                                    onProgress(index + 1, total, project.title, combined)
                                }
                            } else {
                                Mp4Exporter.exportFramesToMp4(
                                    frames = project.frames,
                                    fps = project.fps,
                                    width = safeWidth,
                                    height = safeHeight,
                                    outFile = outFile
                                ) { itemFraction ->
                                    val combined = (index + itemFraction) / total.toFloat()
                                    onProgress(index + 1, total, project.title, combined)
                                }
                            }
                        }.onSuccess { generated ->
                            exportedFiles.add(generated)
                        }.onFailure { err ->
                            runCatching { if (outFile.exists()) outFile.delete() }
                            skippedMessages.add(
                                "${project.title}: Export failed (${err.localizedMessage ?: "unknown error"})"
                            )
                        }
                    }
                }

                BatchExportFormat.JSON_BUNDLE -> {
                    runCatching {
                        val rootJson = JSONObject().apply {
                            put("schemaVersion", 1)
                            put("exportedAtMs", batchStamp)
                            put("id", project.id)
                            put("title", project.title)
                            put("type", project.type.name)
                            put("fps", project.fps)
                            put("aspectRatio", project.aspectRatio)
                            put("updatedAt", project.updatedAt)
                            put("onionSkin", JSONObject(StudioJsonCodec.encodeOnionSkin(project.onionSkin)))
                            put("frames", org.json.JSONArray(StudioJsonCodec.encodeFrames(project.frames)))
                            put("videoClips", org.json.JSONArray(StudioJsonCodec.encodeVideoClips(project.videoClips)))
                            put("audioClips", org.json.JSONArray(StudioJsonCodec.encodeAudioClips(project.audioClips)))
                        }
                        outFile.writeText(rootJson.toString(2), Charsets.UTF_8)
                        outFile
                    }.onSuccess { generated ->
                        exportedFiles.add(generated)
                    }.onFailure { err ->
                        skippedMessages.add(
                            "${project.title}: JSON export failed (${err.localizedMessage ?: "unknown error"})"
                        )
                    }
                }
            }

            onProgress(index + 1, total, project.title, (index + 1).toFloat() / total.toFloat())
        }

        BatchExportResult(
            exportedFiles = exportedFiles,
            skippedMessages = skippedMessages
        )
    }
}
