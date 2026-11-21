package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ProjectType
import com.example.data.model.StudioProject

@Entity(tableName = "studio_projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val typeName: String,
    val fps: Int,
    val aspectRatio: String,
    val onionSkinJson: String,
    val framesJson: String,
    val videoClipsJson: String,
    val audioClipsJson: String,
    val frameCount: Int,
    val updatedAt: Long
) {
    fun toDomain(): StudioProject {
        val type = ProjectType.entries.firstOrNull { it.name == typeName } ?: ProjectType.ANIMATION
        return StudioProject(
            id = id,
            title = title,
            type = type,
            fps = fps,
            aspectRatio = aspectRatio,
            onionSkin = StudioJsonCodec.decodeOnionSkin(onionSkinJson),
            frames = StudioJsonCodec.decodeFrames(framesJson),
            videoClips = StudioJsonCodec.decodeVideoClips(videoClipsJson),
            audioClips = StudioJsonCodec.decodeAudioClips(audioClipsJson),
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(project: StudioProject): ProjectEntity {
            return ProjectEntity(
                id = project.id,
                title = project.title,
                typeName = project.type.name,
                fps = project.fps,
                aspectRatio = project.aspectRatio,
                onionSkinJson = StudioJsonCodec.encodeOnionSkin(project.onionSkin),
                framesJson = StudioJsonCodec.encodeFrames(project.frames),
                videoClipsJson = StudioJsonCodec.encodeVideoClips(project.videoClips),
                audioClipsJson = StudioJsonCodec.encodeAudioClips(project.audioClips),
                frameCount = project.frames.size.coerceAtLeast(project.videoClips.size),
                updatedAt = project.updatedAt
            )
        }
    }
}
