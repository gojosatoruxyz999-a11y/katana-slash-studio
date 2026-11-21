package com.example.data.model

enum class ProjectType(val label: String) {
    ANIMATION("Animation"),
    DRAWING("Drawing"),
    VIDEO("Video"),
    SOUND("Sound")
}

enum class DrawTool(val label: String) {
    PEN("Pen"),
    INK_BRUSH("Ink"),
    KATANA_BLADE("Katana Blade"),
    NEON_AURA("Neon Aura"),
    SOFT_AIRBRUSH("Soft Airbrush"),
    MARKER("Marker"),
    ERASER("Eraser"),
    LINE("Line"),
    RECT("Rect"),
    CIRCLE("Circle")
}

data class StrokePoint(
    val x: Float,
    val y: Float
)

data class DrawStroke(
    val id: String,
    val points: List<StrokePoint>,
    val colorArgb: Long,
    val strokeWidth: Float,
    val tool: DrawTool,
    val alpha: Float = 1f,
    val glowIntensity: Float = 0f,
    val pointWeights: List<Float> = emptyList()
)

data class StudioOutroConfig(
    val enabled: Boolean = false,
    val creatorText: String = "Made with Katana Slash Canvas"
)

data class StudioLayer(
    val id: String,
    val name: String,
    val isVisible: Boolean = true,
    val opacity: Float = 1f,
    val isLocked: Boolean = false,
    val strokes: List<DrawStroke> = emptyList()
)

data class AnimationFrame(
    val id: String,
    val frameNumber: Int,
    val layers: List<StudioLayer>,
    val holdFrames: Int = 1
)

data class OnionSkinConfig(
    val enabled: Boolean = true,
    val showBefore: Boolean = true,
    val showAfter: Boolean = true,
    val beforeCount: Int = 1,
    val afterCount: Int = 1,
    val beforeOpacity: Float = 0.35f,
    val afterOpacity: Float = 0.25f,
    val tintEnabled: Boolean = true
)

enum class VideoEffectType(val label: String) {
    NONE("Original"),
    NEON_PURPLE("Neon Cyber"),
    ANIME_CONTRAST("Anime Cel"),
    MANGA_BW("Manga Ink"),
    SAKURA_TINT("Sakura Glow")
}

enum class VideoTransitionType(val label: String) {
    NONE("Cut"),
    CROSSFADE("Crossfade"),
    KATANA_WIPE("Katana Wipe"),
    FLASH("Impact Flash")
}

enum class KeyframeEasing(val label: String) {
    LINEAR("Linear"),
    EASE_IN_OUT("Ease In/Out"),
    EASE_OUT("Ease Out"),
    EASE_IN("Ease In")
}

enum class OverlayTargetType(val label: String) {
    TEXT("Text Overlay"),
    IMAGE("Image Overlay")
}

data class OverlayKeyframe(
    val id: String,
    val timeMs: Long,
    val x: Float = 0.5f,
    val y: Float = 0.82f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val easing: KeyframeEasing = KeyframeEasing.EASE_IN_OUT
)

data class OverlayTransform(
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotationDeg: Float,
    val opacity: Float
)

data class VideoClipItem(
    val id: String,
    val uriString: String,
    val displayName: String,
    val durationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = durationMs,
    val effect: VideoEffectType = VideoEffectType.NONE,
    val transitionIn: VideoTransitionType = VideoTransitionType.NONE,
    val overlayText: String = "",
    val textKeyframes: List<OverlayKeyframe> = emptyList(),
    val imageOverlayUri: String? = null,
    val imageOverlayName: String = "",
    val imageKeyframes: List<OverlayKeyframe> = emptyList(),
    val motionBlurEnabled: Boolean = false,
    val motionBlurIntensity: Float = 0.65f
) {
    val effectiveDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(100L)
}

data class AudioClipItem(
    val id: String,
    val name: String,
    val trackType: String, // "VOICE", "SFX", "BGM"
    val filePath: String? = null,
    val sfxPresetId: String? = null,
    val durationMs: Long = 2000L,
    val volume: Float = 0.85f,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = durationMs
)

data class StudioProject(
    val id: String,
    val title: String,
    val type: ProjectType,
    val fps: Int = 12,
    val aspectRatio: String = "16:9",
    val onionSkin: OnionSkinConfig = OnionSkinConfig(),
    val frames: List<AnimationFrame> = emptyList(),
    val videoClips: List<VideoClipItem> = emptyList(),
    val audioClips: List<AudioClipItem> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class DeviceMemoryInfo(
    val availableMb: Long,
    val totalMb: Long,
    val isLowRamClass: Boolean
)
