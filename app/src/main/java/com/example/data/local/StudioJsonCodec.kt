package com.example.data.local

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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object StudioJsonCodec {

    fun encodeOnionSkin(config: OnionSkinConfig): String {
        return JSONObject().apply {
            put("enabled", config.enabled)
            put("showBefore", config.showBefore)
            put("showAfter", config.showAfter)
            put("beforeCount", config.beforeCount)
            put("afterCount", config.afterCount)
            put("beforeOpacity", config.beforeOpacity.toDouble())
            put("afterOpacity", config.afterOpacity.toDouble())
            put("tintEnabled", config.tintEnabled)
        }.toString()
    }

    fun decodeOnionSkin(json: String): OnionSkinConfig {
        if (json.isBlank()) return OnionSkinConfig()
        return runCatching {
            val obj = JSONObject(json)
            OnionSkinConfig(
                enabled = obj.optBoolean("enabled", true),
                showBefore = obj.optBoolean("showBefore", true),
                showAfter = obj.optBoolean("showAfter", true),
                beforeCount = obj.optInt("beforeCount", 1).coerceIn(0, 3),
                afterCount = obj.optInt("afterCount", 1).coerceIn(0, 3),
                beforeOpacity = obj.optDouble("beforeOpacity", 0.35).toFloat().coerceIn(0.05f, 0.85f),
                afterOpacity = obj.optDouble("afterOpacity", 0.25).toFloat().coerceIn(0.05f, 0.85f),
                tintEnabled = obj.optBoolean("tintEnabled", true)
            )
        }.getOrDefault(OnionSkinConfig())
    }

    fun encodeFrames(frames: List<AnimationFrame>): String {
        val arr = JSONArray()
        for (frame in frames) {
            val fObj = JSONObject()
            fObj.put("id", frame.id)
            fObj.put("frameNumber", frame.frameNumber)
            fObj.put("holdFrames", frame.holdFrames)
            val layersArr = JSONArray()
            for (layer in frame.layers) {
                val lObj = JSONObject()
                lObj.put("id", layer.id)
                lObj.put("name", layer.name)
                lObj.put("isVisible", layer.isVisible)
                lObj.put("opacity", layer.opacity.toDouble())
                lObj.put("isLocked", layer.isLocked)
                val strokesArr = JSONArray()
                for (stroke in layer.strokes) {
                    val sObj = JSONObject()
                    sObj.put("id", stroke.id)
                    sObj.put("colorArgb", stroke.colorArgb)
                    sObj.put("strokeWidth", stroke.strokeWidth.toDouble())
                    sObj.put("tool", stroke.tool.name)
                    sObj.put("alpha", stroke.alpha.toDouble())
                    sObj.put("glowIntensity", stroke.glowIntensity.toDouble())
                    if (stroke.pointWeights.isNotEmpty()) {
                        val weightsArr = JSONArray()
                        for (w in stroke.pointWeights) {
                            weightsArr.put(w.toDouble())
                        }
                        sObj.put("pointWeights", weightsArr)
                    }
                    val ptsArr = JSONArray()
                    for (pt in stroke.points) {
                        ptsArr.put(JSONArray().apply {
                            put(pt.x.toDouble())
                            put(pt.y.toDouble())
                        })
                    }
                    sObj.put("points", ptsArr)
                    strokesArr.put(sObj)
                }
                lObj.put("strokes", strokesArr)
                layersArr.put(lObj)
            }
            fObj.put("layers", layersArr)
            arr.put(fObj)
        }
        return arr.toString()
    }

    fun decodeFrames(json: String): List<AnimationFrame> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val fObj = arr.getJSONObject(i)
                    val layersArr = fObj.optJSONArray("layers") ?: JSONArray()
                    val layers = buildList {
                        for (j in 0 until layersArr.length()) {
                            val lObj = layersArr.getJSONObject(j)
                            val strokesArr = lObj.optJSONArray("strokes") ?: JSONArray()
                            val strokes = buildList {
                                for (k in 0 until strokesArr.length()) {
                                    val sObj = strokesArr.getJSONObject(k)
                                    val ptsArr = sObj.optJSONArray("points") ?: JSONArray()
                                    val points = buildList {
                                        for (p in 0 until ptsArr.length()) {
                                            val pair = ptsArr.getJSONArray(p)
                                            add(StrokePoint(pair.getDouble(0).toFloat(), pair.getDouble(1).toFloat()))
                                        }
                                    }
                                    val toolName = sObj.optString("tool", DrawTool.PEN.name)
                                    val tool = DrawTool.entries.firstOrNull { it.name == toolName } ?: DrawTool.PEN
                                    val weightsArr = sObj.optJSONArray("pointWeights")
                                    val pointWeights = if (weightsArr != null) {
                                        buildList {
                                            for (wIdx in 0 until weightsArr.length()) {
                                                add(weightsArr.optDouble(wIdx, 1.0).toFloat().coerceIn(0.1f, 2.0f))
                                            }
                                        }
                                    } else emptyList()
                                    add(
                                        DrawStroke(
                                            id = sObj.optString("id"),
                                            points = points,
                                            colorArgb = sObj.optLong("colorArgb", 0xFFF8FAFC),
                                            strokeWidth = sObj.optDouble("strokeWidth", 0.008).toFloat(),
                                            tool = tool,
                                            alpha = sObj.optDouble("alpha", 1.0).toFloat(),
                                            glowIntensity = sObj.optDouble("glowIntensity", 0.0).toFloat().coerceIn(0f, 1f),
                                            pointWeights = pointWeights
                                        )
                                    )
                                }
                            }
                            add(
                                StudioLayer(
                                    id = lObj.optString("id"),
                                    name = lObj.optString("name", "Layer ${j + 1}"),
                                    isVisible = lObj.optBoolean("isVisible", true),
                                    opacity = lObj.optDouble("opacity", 1.0).toFloat(),
                                    isLocked = lObj.optBoolean("isLocked", false),
                                    strokes = strokes
                                )
                            )
                        }
                    }
                    add(
                        AnimationFrame(
                            id = fObj.optString("id"),
                            frameNumber = fObj.optInt("frameNumber", i + 1),
                            layers = layers,
                            holdFrames = fObj.optInt("holdFrames", 1)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeVideoClips(clips: List<VideoClipItem>): String {
        val arr = JSONArray()
        for (clip in clips) {
            arr.put(JSONObject().apply {
                put("id", clip.id)
                put("uriString", clip.uriString)
                put("displayName", clip.displayName)
                put("durationMs", clip.durationMs)
                put("trimStartMs", clip.trimStartMs)
                put("trimEndMs", clip.trimEndMs)
                put("effect", clip.effect.name)
                put("transitionIn", clip.transitionIn.name)
                put("overlayText", clip.overlayText)
                put("textKeyframes", encodeKeyframes(clip.textKeyframes))
                put("imageOverlayUri", clip.imageOverlayUri ?: "")
                put("imageOverlayName", clip.imageOverlayName)
                put("imageKeyframes", encodeKeyframes(clip.imageKeyframes))
                put("motionBlurEnabled", clip.motionBlurEnabled)
                put("motionBlurIntensity", clip.motionBlurIntensity.toDouble())
            })
        }
        return arr.toString()
    }

    fun decodeVideoClips(json: String): List<VideoClipItem> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val effect = VideoEffectType.entries.firstOrNull {
                        it.name == obj.optString("effect")
                    } ?: VideoEffectType.NONE
                    val transition = VideoTransitionType.entries.firstOrNull {
                        it.name == obj.optString("transitionIn")
                    } ?: VideoTransitionType.NONE
                    val duration = obj.optLong("durationMs", 5000L)
                    add(
                        VideoClipItem(
                            id = obj.optString("id"),
                            uriString = obj.optString("uriString"),
                            displayName = obj.optString("displayName", "Clip ${i + 1}"),
                            durationMs = duration,
                            trimStartMs = obj.optLong("trimStartMs", 0L),
                            trimEndMs = obj.optLong("trimEndMs", duration),
                            effect = effect,
                            transitionIn = transition,
                            overlayText = obj.optString("overlayText", ""),
                            textKeyframes = decodeKeyframes(obj.optJSONArray("textKeyframes")),
                            imageOverlayUri = obj.optString("imageOverlayUri").ifBlank { null },
                            imageOverlayName = obj.optString("imageOverlayName", ""),
                            imageKeyframes = decodeKeyframes(obj.optJSONArray("imageKeyframes")),
                            motionBlurEnabled = obj.optBoolean("motionBlurEnabled", false),
                            motionBlurIntensity = obj.optDouble("motionBlurIntensity", 0.65).toFloat().coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeKeyframes(keyframes: List<OverlayKeyframe>): JSONArray {
        val arr = JSONArray()
        for (kf in keyframes) {
            arr.put(JSONObject().apply {
                put("id", kf.id)
                put("timeMs", kf.timeMs)
                put("x", kf.x.toDouble())
                put("y", kf.y.toDouble())
                put("scale", kf.scale.toDouble())
                put("rotationDeg", kf.rotationDeg.toDouble())
                put("opacity", kf.opacity.toDouble())
                put("easing", kf.easing.name)
            })
        }
        return arr
    }

    private fun decodeKeyframes(arr: JSONArray?): List<OverlayKeyframe> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val easingName = obj.optString("easing", KeyframeEasing.EASE_IN_OUT.name)
                val easing = KeyframeEasing.entries.firstOrNull { it.name == easingName }
                    ?: KeyframeEasing.EASE_IN_OUT
                add(
                    OverlayKeyframe(
                        id = obj.optString("id").ifBlank { "kf_$i" },
                        timeMs = obj.optLong("timeMs", 0L),
                        x = obj.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                        y = obj.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                        scale = obj.optDouble("scale", 1.0).toFloat().coerceIn(0.2f, 3.0f),
                        rotationDeg = obj.optDouble("rotationDeg", 0.0).toFloat().coerceIn(-180f, 180f),
                        opacity = obj.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                        easing = easing
                    )
                )
            }
        }.sortedBy { it.timeMs }
    }

    fun encodeAudioClips(clips: List<AudioClipItem>): String {
        val arr = JSONArray()
        for (clip in clips) {
            arr.put(JSONObject().apply {
                put("id", clip.id)
                put("name", clip.name)
                put("trackType", clip.trackType)
                put("filePath", clip.filePath ?: "")
                put("sfxPresetId", clip.sfxPresetId ?: "")
                put("durationMs", clip.durationMs)
                put("volume", clip.volume.toDouble())
                put("trimStartMs", clip.trimStartMs)
                put("trimEndMs", clip.trimEndMs)
            })
        }
        return arr.toString()
    }

    fun decodeAudioClips(json: String): List<AudioClipItem> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val duration = obj.optLong("durationMs", 2000L)
                    add(
                        AudioClipItem(
                            id = obj.optString("id"),
                            name = obj.optString("name", "Audio ${i + 1}"),
                            trackType = obj.optString("trackType", "VOICE"),
                            filePath = obj.optString("filePath").ifBlank { null },
                            sfxPresetId = obj.optString("sfxPresetId").ifBlank { null },
                            durationMs = duration,
                            volume = obj.optDouble("volume", 0.85).toFloat(),
                            trimStartMs = obj.optLong("trimStartMs", 0L),
                            trimEndMs = obj.optLong("trimEndMs", duration)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeProjectBundle(project: StudioProject): String {
        return JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAtMs", System.currentTimeMillis())
            put("id", project.id)
            put("title", project.title)
            put("type", project.type.name)
            put("fps", project.fps)
            put("aspectRatio", project.aspectRatio)
            put("updatedAt", project.updatedAt)
            put("onionSkin", JSONObject(encodeOnionSkin(project.onionSkin)))
            put("frames", JSONArray(encodeFrames(project.frames)))
            put("videoClips", JSONArray(encodeVideoClips(project.videoClips)))
            put("audioClips", JSONArray(encodeAudioClips(project.audioClips)))
        }.toString(2)
    }

    fun decodeProjectBundle(json: String): StudioProject? {
        if (json.isBlank()) return null
        return runCatching {
            val obj = JSONObject(json)
            val title = obj.optString("title", "Imported Katana Project").ifBlank { "Imported Katana Project" }
            val typeName = obj.optString("type", ProjectType.ANIMATION.name)
            val type = ProjectType.entries.firstOrNull { it.name == typeName } ?: ProjectType.ANIMATION
            val fps = obj.optInt("fps", 8).coerceIn(1, 30)
            val aspectRatio = obj.optString("aspectRatio", "16:9").ifBlank { "16:9" }
            val onionSkin = decodeOnionSkin(obj.optJSONObject("onionSkin")?.toString() ?: "")
            val frames = decodeFrames(obj.optJSONArray("frames")?.toString() ?: "")
            val videoClips = decodeVideoClips(obj.optJSONArray("videoClips")?.toString() ?: "")
            val audioClips = decodeAudioClips(obj.optJSONArray("audioClips")?.toString() ?: "")
            val rawId = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
            StudioProject(
                id = rawId,
                title = title,
                type = type,
                fps = fps,
                aspectRatio = aspectRatio,
                onionSkin = onionSkin,
                frames = frames,
                videoClips = videoClips,
                audioClips = audioClips,
                updatedAt = System.currentTimeMillis()
            )
        }.getOrNull()
    }
}
