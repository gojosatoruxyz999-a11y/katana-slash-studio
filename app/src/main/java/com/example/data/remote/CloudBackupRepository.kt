package com.example.data.remote

import androidx.annotation.Keep
import com.example.data.model.AnimationFrame
import com.example.data.model.AudioClipItem
import com.example.data.model.DrawStroke
import com.example.data.model.DrawTool
import com.example.data.model.KeyframeEasing
import com.example.data.model.OnionSkinConfig
import com.example.data.model.OverlayKeyframe
import com.example.data.model.ProjectType
import com.example.data.model.StrokePoint
import com.example.data.model.StudioLayer
import com.example.data.model.StudioProject
import com.example.data.model.VideoClipItem
import com.example.data.model.VideoEffectType
import com.example.data.model.VideoTransitionType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

@Keep
@IgnoreExtraProperties
data class CloudBackupItem(
    val projectId: String = "",
    val userId: String = "",
    val title: String = "",
    val projectType: ProjectType = ProjectType.ANIMATION,
    val fps: Int = 12,
    val framesCount: Int = 0,
    val videoClipsCount: Int = 0,
    val audioClipsCount: Int = 0,
    val updatedAtMs: Long = 0L,
    val restoredProject: StudioProject? = null
)

object CloudBackupMapper {
    private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
    const val MAX_FRAMES = 120
    const val MAX_CLIPS = 50

    fun isValidId(id: String): Boolean {
        return id.length in 1..128 && VALID_ID_REGEX.matches(id)
    }

    fun sanitizeProjectId(rawId: String): String {
        val cleaned = rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        return cleaned.ifBlank { UUID.randomUUID().toString() }
    }

    fun toFirestoreData(
        project: StudioProject,
        userId: String,
        isCreate: Boolean,
        useServerTimestamps: Boolean = true
    ): Map<String, Any> {
        require(isValidId(userId)) { "Invalid userId for Firestore backup" }

        val boundedFrames = project.frames.take(MAX_FRAMES)
        val boundedVideoClips = project.videoClips.take(MAX_CLIPS)
        val boundedAudioClips = project.audioClips.take(MAX_CLIPS)

        val safeTitle = project.title.trim().ifBlank { "Untitled ${project.type.label}" }.take(100)
        val safeAspect = project.aspectRatio.trim().ifBlank { "16:9" }.take(20)
        val safeFps = project.fps.coerceIn(1, 60)
        val safeSourceUpdated = project.updatedAt.coerceAtLeast(0L)

        val payload = linkedMapOf<String, Any>(
            "title" to safeTitle,
            "projectType" to project.type.name,
            "fps" to safeFps,
            "aspectRatio" to safeAspect,
            "sourceUpdatedAtMs" to safeSourceUpdated,
            "onionSkin" to encodeOnionSkinMap(project.onionSkin),
            "frames" to boundedFrames.map { encodeFrameMap(it) },
            "videoClips" to boundedVideoClips.map { encodeVideoClipMap(it) },
            "audioClips" to boundedAudioClips.map { encodeAudioClipMap(it) },
            "framesCount" to boundedFrames.size,
            "videoClipsCount" to boundedVideoClips.size,
            "audioClipsCount" to boundedAudioClips.size
        )

        if (isCreate) {
            payload["userId"] = userId
            if (useServerTimestamps) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
        }
        if (useServerTimestamps) {
            payload["updatedAt"] = FieldValue.serverTimestamp()
        }
        return payload
    }

    fun fromFirestoreMap(projectId: String, data: Map<String, Any?>, estimatedUpdatedAtMs: Long = 0L): CloudBackupItem {
        val userId = data["userId"] as? String ?: ""
        val title = (data["title"] as? String)?.ifBlank { "Untitled" } ?: "Untitled"
        val typeName = data["projectType"] as? String ?: ProjectType.ANIMATION.name
        val projectType = ProjectType.entries.firstOrNull { it.name == typeName } ?: ProjectType.ANIMATION
        val fps = ((data["fps"] as? Number)?.toInt() ?: 12).coerceIn(1, 60)
        val aspectRatio = (data["aspectRatio"] as? String)?.ifBlank { "16:9" } ?: "16:9"
        val sourceUpdatedAtMs = ((data["sourceUpdatedAtMs"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L)

        val onionSkin = decodeOnionSkinMap(data["onionSkin"] as? Map<*, *>)
        val frames = (data["frames"] as? List<*>)?.mapIndexedNotNull { index, entry ->
            (entry as? Map<*, *>)?.let { decodeFrameMap(it, index) }
        } ?: emptyList()
        val videoClips = (data["videoClips"] as? List<*>)?.mapIndexedNotNull { index, entry ->
            (entry as? Map<*, *>)?.let { decodeVideoClipMap(it, index) }
        } ?: emptyList()
        val audioClips = (data["audioClips"] as? List<*>)?.mapIndexedNotNull { index, entry ->
            (entry as? Map<*, *>)?.let { decodeAudioClipMap(it, index) }
        } ?: emptyList()

        val framesCount = (data["framesCount"] as? Number)?.toInt() ?: frames.size
        val videoClipsCount = (data["videoClipsCount"] as? Number)?.toInt() ?: videoClips.size
        val audioClipsCount = (data["audioClipsCount"] as? Number)?.toInt() ?: audioClips.size
        val effectiveUpdatedAt = if (estimatedUpdatedAtMs > 0L) estimatedUpdatedAtMs else sourceUpdatedAtMs

        val restored = StudioProject(
            id = projectId,
            title = title,
            type = projectType,
            fps = fps,
            aspectRatio = aspectRatio,
            onionSkin = onionSkin,
            frames = frames,
            videoClips = videoClips,
            audioClips = audioClips,
            updatedAt = if (sourceUpdatedAtMs > 0L) sourceUpdatedAtMs else effectiveUpdatedAt
        )

        return CloudBackupItem(
            projectId = projectId,
            userId = userId,
            title = title,
            projectType = projectType,
            fps = fps,
            framesCount = framesCount,
            videoClipsCount = videoClipsCount,
            audioClipsCount = audioClipsCount,
            updatedAtMs = effectiveUpdatedAt,
            restoredProject = restored
        )
    }

    private fun encodeOnionSkinMap(config: OnionSkinConfig): Map<String, Any> = mapOf(
        "enabled" to config.enabled,
        "showBefore" to config.showBefore,
        "showAfter" to config.showAfter,
        "beforeCount" to config.beforeCount,
        "afterCount" to config.afterCount,
        "beforeOpacity" to config.beforeOpacity.toDouble(),
        "afterOpacity" to config.afterOpacity.toDouble(),
        "tintEnabled" to config.tintEnabled
    )

    private fun decodeOnionSkinMap(raw: Map<*, *>?): OnionSkinConfig {
        if (raw == null) return OnionSkinConfig()
        return OnionSkinConfig(
            enabled = (raw["enabled"] as? Boolean) ?: true,
            showBefore = (raw["showBefore"] as? Boolean) ?: true,
            showAfter = (raw["showAfter"] as? Boolean) ?: true,
            beforeCount = ((raw["beforeCount"] as? Number)?.toInt() ?: 1).coerceIn(0, 3),
            afterCount = ((raw["afterCount"] as? Number)?.toInt() ?: 1).coerceIn(0, 3),
            beforeOpacity = ((raw["beforeOpacity"] as? Number)?.toFloat() ?: 0.35f).coerceIn(0.05f, 0.85f),
            afterOpacity = ((raw["afterOpacity"] as? Number)?.toFloat() ?: 0.25f).coerceIn(0.05f, 0.85f),
            tintEnabled = (raw["tintEnabled"] as? Boolean) ?: true
        )
    }

    private fun encodeFrameMap(frame: AnimationFrame): Map<String, Any> = mapOf(
        "id" to frame.id,
        "frameNumber" to frame.frameNumber,
        "holdFrames" to frame.holdFrames,
        "layers" to frame.layers.map { encodeLayerMap(it) }
    )

    private fun decodeFrameMap(raw: Map<*, *>, index: Int): AnimationFrame {
        val layers = (raw["layers"] as? List<*>)?.mapIndexedNotNull { lIdx, entry ->
            (entry as? Map<*, *>)?.let { decodeLayerMap(it, lIdx) }
        } ?: emptyList()
        return AnimationFrame(
            id = (raw["id"] as? String)?.ifBlank { "frame_${index + 1}" } ?: "frame_${index + 1}",
            frameNumber = (raw["frameNumber"] as? Number)?.toInt() ?: (index + 1),
            layers = layers,
            holdFrames = ((raw["holdFrames"] as? Number)?.toInt() ?: 1).coerceAtLeast(1)
        )
    }

    private fun encodeLayerMap(layer: StudioLayer): Map<String, Any> = mapOf(
        "id" to layer.id,
        "name" to layer.name,
        "visible" to layer.isVisible,
        "opacity" to layer.opacity.toDouble(),
        "locked" to layer.isLocked,
        "strokes" to layer.strokes.map { encodeStrokeMap(it) }
    )

    private fun decodeLayerMap(raw: Map<*, *>, index: Int): StudioLayer {
        val strokes = (raw["strokes"] as? List<*>)?.mapIndexedNotNull { sIdx, entry ->
            (entry as? Map<*, *>)?.let { decodeStrokeMap(it, sIdx) }
        } ?: emptyList()
        val visible = (raw["visible"] as? Boolean) ?: (raw["isVisible"] as? Boolean) ?: true
        val locked = (raw["locked"] as? Boolean) ?: (raw["isLocked"] as? Boolean) ?: false
        return StudioLayer(
            id = (raw["id"] as? String)?.ifBlank { "layer_${index + 1}" } ?: "layer_${index + 1}",
            name = (raw["name"] as? String)?.ifBlank { "Layer ${index + 1}" } ?: "Layer ${index + 1}",
            isVisible = visible,
            opacity = ((raw["opacity"] as? Number)?.toFloat() ?: 1f).coerceIn(0f, 1f),
            isLocked = locked,
            strokes = strokes
        )
    }

    private fun encodeStrokeMap(stroke: DrawStroke): Map<String, Any> {
        val map = linkedMapOf<String, Any>(
            "id" to stroke.id,
            "colorArgb" to stroke.colorArgb,
            "strokeWidth" to stroke.strokeWidth.toDouble(),
            "tool" to stroke.tool.name,
            "alpha" to stroke.alpha.toDouble(),
            "glowIntensity" to stroke.glowIntensity.toDouble(),
            // Firestore forbids nested arrays-of-arrays, so store each point as an {x, y} map
            "points" to stroke.points.map { pt -> mapOf("x" to pt.x.toDouble(), "y" to pt.y.toDouble()) }
        )
        if (stroke.pointWeights.isNotEmpty()) {
            map["pointWeights"] = stroke.pointWeights.map { it.toDouble() }
        }
        return map
    }

    private fun decodeStrokeMap(raw: Map<*, *>, index: Int): DrawStroke {
        val points = (raw["points"] as? List<*>)?.mapNotNull { entry ->
            when (entry) {
                is Map<*, *> -> {
                    val x = (entry["x"] as? Number)?.toFloat()
                    val y = (entry["y"] as? Number)?.toFloat()
                    if (x != null && y != null) StrokePoint(x, y) else null
                }
                else -> null
            }
        } ?: emptyList()
        val toolName = raw["tool"] as? String ?: DrawTool.PEN.name
        val tool = DrawTool.entries.firstOrNull { it.name == toolName } ?: DrawTool.PEN
        val weights = (raw["pointWeights"] as? List<*>)?.mapNotNull {
            (it as? Number)?.toFloat()?.coerceIn(0.1f, 2.0f)
        } ?: emptyList()
        return DrawStroke(
            id = (raw["id"] as? String)?.ifBlank { "stroke_$index" } ?: "stroke_$index",
            points = points,
            colorArgb = (raw["colorArgb"] as? Number)?.toLong() ?: 0xFFF8FAFC,
            strokeWidth = (raw["strokeWidth"] as? Number)?.toFloat() ?: 0.008f,
            tool = tool,
            alpha = ((raw["alpha"] as? Number)?.toFloat() ?: 1f).coerceIn(0f, 1f),
            glowIntensity = ((raw["glowIntensity"] as? Number)?.toFloat() ?: 0f).coerceIn(0f, 1f),
            pointWeights = weights
        )
    }

    private fun encodeVideoClipMap(clip: VideoClipItem): Map<String, Any> {
        val map = linkedMapOf<String, Any?>(
            "id" to clip.id,
            "uriString" to clip.uriString,
            "displayName" to clip.displayName,
            "durationMs" to clip.durationMs,
            "trimStartMs" to clip.trimStartMs,
            "trimEndMs" to clip.trimEndMs,
            "effect" to clip.effect.name,
            "transitionIn" to clip.transitionIn.name,
            "overlayText" to clip.overlayText,
            "textKeyframes" to clip.textKeyframes.map { encodeKeyframeMap(it) },
            "imageOverlayUri" to clip.imageOverlayUri,
            "imageOverlayName" to clip.imageOverlayName,
            "imageKeyframes" to clip.imageKeyframes.map { encodeKeyframeMap(it) },
            "motionBlurEnabled" to clip.motionBlurEnabled,
            "motionBlurIntensity" to clip.motionBlurIntensity.toDouble()
        )
        return map.filterValues { it != null }.mapValues { it.value!! }
    }

    private fun decodeVideoClipMap(raw: Map<*, *>, index: Int): VideoClipItem {
        val duration = ((raw["durationMs"] as? Number)?.toLong() ?: 5000L).coerceAtLeast(100L)
        val effectName = raw["effect"] as? String ?: VideoEffectType.NONE.name
        val effect = VideoEffectType.entries.firstOrNull { it.name == effectName } ?: VideoEffectType.NONE
        val transitionName = raw["transitionIn"] as? String ?: VideoTransitionType.NONE.name
        val transition = VideoTransitionType.entries.firstOrNull { it.name == transitionName } ?: VideoTransitionType.NONE

        val textKeyframes = (raw["textKeyframes"] as? List<*>)?.mapIndexedNotNull { kfIdx, entry ->
            (entry as? Map<*, *>)?.let { decodeKeyframeMap(it, kfIdx) }
        }?.sortedBy { it.timeMs } ?: emptyList()

        val imageKeyframes = (raw["imageKeyframes"] as? List<*>)?.mapIndexedNotNull { kfIdx, entry ->
            (entry as? Map<*, *>)?.let { decodeKeyframeMap(it, kfIdx) }
        }?.sortedBy { it.timeMs } ?: emptyList()

        return VideoClipItem(
            id = (raw["id"] as? String)?.ifBlank { "clip_$index" } ?: "clip_$index",
            uriString = raw["uriString"] as? String ?: "",
            displayName = (raw["displayName"] as? String)?.ifBlank { "Clip ${index + 1}" } ?: "Clip ${index + 1}",
            durationMs = duration,
            trimStartMs = (raw["trimStartMs"] as? Number)?.toLong() ?: 0L,
            trimEndMs = (raw["trimEndMs"] as? Number)?.toLong() ?: duration,
            effect = effect,
            transitionIn = transition,
            overlayText = raw["overlayText"] as? String ?: "",
            textKeyframes = textKeyframes,
            imageOverlayUri = (raw["imageOverlayUri"] as? String)?.ifBlank { null },
            imageOverlayName = raw["imageOverlayName"] as? String ?: "",
            imageKeyframes = imageKeyframes,
            motionBlurEnabled = (raw["motionBlurEnabled"] as? Boolean) ?: false,
            motionBlurIntensity = ((raw["motionBlurIntensity"] as? Number)?.toFloat() ?: 0.65f).coerceIn(0f, 1f)
        )
    }

    private fun encodeKeyframeMap(kf: OverlayKeyframe): Map<String, Any> = mapOf(
        "id" to kf.id,
        "timeMs" to kf.timeMs,
        "x" to kf.x.toDouble(),
        "y" to kf.y.toDouble(),
        "scale" to kf.scale.toDouble(),
        "rotationDeg" to kf.rotationDeg.toDouble(),
        "opacity" to kf.opacity.toDouble(),
        "easing" to kf.easing.name
    )

    private fun decodeKeyframeMap(raw: Map<*, *>, index: Int): OverlayKeyframe {
        val easingName = raw["easing"] as? String ?: KeyframeEasing.EASE_IN_OUT.name
        val easing = KeyframeEasing.entries.firstOrNull { it.name == easingName } ?: KeyframeEasing.EASE_IN_OUT
        return OverlayKeyframe(
            id = (raw["id"] as? String)?.ifBlank { "kf_$index" } ?: "kf_$index",
            timeMs = (raw["timeMs"] as? Number)?.toLong() ?: 0L,
            x = ((raw["x"] as? Number)?.toFloat() ?: 0.5f).coerceIn(0f, 1f),
            y = ((raw["y"] as? Number)?.toFloat() ?: 0.5f).coerceIn(0f, 1f),
            scale = ((raw["scale"] as? Number)?.toFloat() ?: 1f).coerceIn(0.2f, 3.0f),
            rotationDeg = ((raw["rotationDeg"] as? Number)?.toFloat() ?: 0f).coerceIn(-180f, 180f),
            opacity = ((raw["opacity"] as? Number)?.toFloat() ?: 1f).coerceIn(0f, 1f),
            easing = easing
        )
    }

    private fun encodeAudioClipMap(clip: AudioClipItem): Map<String, Any> {
        val map = linkedMapOf<String, Any?>(
            "id" to clip.id,
            "name" to clip.name,
            "trackType" to clip.trackType,
            "filePath" to clip.filePath,
            "sfxPresetId" to clip.sfxPresetId,
            "durationMs" to clip.durationMs,
            "volume" to clip.volume.toDouble(),
            "trimStartMs" to clip.trimStartMs,
            "trimEndMs" to clip.trimEndMs
        )
        return map.filterValues { it != null }.mapValues { it.value!! }
    }

    private fun decodeAudioClipMap(raw: Map<*, *>, index: Int): AudioClipItem {
        val duration = ((raw["durationMs"] as? Number)?.toLong() ?: 2000L).coerceAtLeast(100L)
        return AudioClipItem(
            id = (raw["id"] as? String)?.ifBlank { "audio_$index" } ?: "audio_$index",
            name = (raw["name"] as? String)?.ifBlank { "Audio ${index + 1}" } ?: "Audio ${index + 1}",
            trackType = (raw["trackType"] as? String)?.ifBlank { "VOICE" } ?: "VOICE",
            filePath = (raw["filePath"] as? String)?.ifBlank { null },
            sfxPresetId = (raw["sfxPresetId"] as? String)?.ifBlank { null },
            durationMs = duration,
            volume = ((raw["volume"] as? Number)?.toFloat() ?: 0.85f).coerceIn(0f, 1f),
            trimStartMs = (raw["trimStartMs"] as? Number)?.toLong() ?: 0L,
            trimEndMs = (raw["trimEndMs"] as? Number)?.toLong() ?: duration
        )
    }
}

class CloudBackupRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        require(!uid.isNullOrBlank()) { "User must be signed in with Google before accessing cloud backups." }
        require(CloudBackupMapper.isValidId(uid)) { "Invalid user UID format." }
        return uid
    }

    fun observeCloudBackups(userId: String): Flow<List<CloudBackupItem>> = flow {
        require(userId.isNotBlank() && CloudBackupMapper.isValidId(userId)) {
            "Valid userId is required to observe cloud backups."
        }
        val collectionPath = "users/$userId/project_backups"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("project_backups")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val estimatedMs = doc.getTimestamp(
                            "updatedAt",
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.toDate()?.time ?: 0L
                        CloudBackupMapper.fromFirestoreMap(
                            projectId = doc.id,
                            data = data,
                            estimatedUpdatedAtMs = estimatedMs
                        )
                    }.sortedByDescending { it.updatedAtMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, collectionPath)
                    }
                    throw error
                }
        )
    }

    suspend fun backupProjects(
        projects: List<StudioProject>,
        onProgress: (current: Int, total: Int, title: String) -> Unit = { _, _, _ -> }
    ): Result<Int> {
        if (projects.isEmpty()) return Result.success(0)
        var currentPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/project_backups"
        var lastOp = OperationType.WRITE
        return try {
            val uid = requireUserId()
            var backedUpCount = 0
            projects.forEachIndexed { index, project ->
                onProgress(index + 1, projects.size, project.title)
                val docId = CloudBackupMapper.sanitizeProjectId(project.id)
                val docRef = db.collection("users")
                    .document(uid)
                    .collection("project_backups")
                    .document(docId)
                currentPath = docRef.path

                lastOp = OperationType.GET
                val existingSnapshot = docRef.get().await()
                if (existingSnapshot.exists()) {
                    lastOp = OperationType.UPDATE
                    val updatePayload = CloudBackupMapper.toFirestoreData(
                        project = project,
                        userId = uid,
                        isCreate = false,
                        useServerTimestamps = true
                    )
                    docRef.update(updatePayload).await()
                } else {
                    lastOp = OperationType.CREATE
                    val createPayload = CloudBackupMapper.toFirestoreData(
                        project = project,
                        userId = uid,
                        isCreate = true,
                        useServerTimestamps = true
                    )
                    docRef.set(createPayload).await()
                }
                backedUpCount++
            }
            Result.success(backedUpCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, lastOp, currentPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudBackup(projectId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/project_backups/$projectId"
        return try {
            val uid = requireUserId()
            val safeId = CloudBackupMapper.sanitizeProjectId(projectId)
            val docRef = db.collection("users")
                .document(uid)
                .collection("project_backups")
                .document(safeId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
