package com.example.data.local

import com.example.data.model.AnimationFrame
import com.example.data.model.DrawStroke
import com.example.data.model.DrawTool
import com.example.data.model.OnionSkinConfig
import com.example.data.model.ProjectType
import com.example.data.model.StrokePoint
import com.example.data.model.StudioLayer
import com.example.data.model.StudioProject
import java.util.UUID

object StarterProjectFactory {

    fun createNewBlankProject(title: String, type: ProjectType, fps: Int = 12): StudioProject {
        val defaultLayers = listOf(
            StudioLayer(
                id = UUID.randomUUID().toString(),
                name = "Rough Sketch",
                isVisible = true,
                opacity = 0.7f
            ),
            StudioLayer(
                id = UUID.randomUUID().toString(),
                name = "Line Art",
                isVisible = true,
                opacity = 1f
            )
        )
        val initialFrames = when (type) {
            ProjectType.ANIMATION -> listOf(
                AnimationFrame(
                    id = UUID.randomUUID().toString(),
                    frameNumber = 1,
                    layers = defaultLayers
                ),
                AnimationFrame(
                    id = UUID.randomUUID().toString(),
                    frameNumber = 2,
                    layers = defaultLayers.map { it.copy(id = UUID.randomUUID().toString()) }
                )
            )
            ProjectType.DRAWING -> listOf(
                AnimationFrame(
                    id = UUID.randomUUID().toString(),
                    frameNumber = 1,
                    layers = listOf(
                        StudioLayer(id = UUID.randomUUID().toString(), name = "Background", opacity = 1f),
                        StudioLayer(id = UUID.randomUUID().toString(), name = "Line Art", opacity = 1f),
                        StudioLayer(id = UUID.randomUUID().toString(), name = "Neon FX", opacity = 0.9f)
                    )
                ),
                AnimationFrame(
                    id = UUID.randomUUID().toString(),
                    frameNumber = 2,
                    layers = listOf(
                        StudioLayer(id = UUID.randomUUID().toString(), name = "Background", opacity = 1f),
                        StudioLayer(id = UUID.randomUUID().toString(), name = "Line Art", opacity = 1f),
                        StudioLayer(id = UUID.randomUUID().toString(), name = "Neon FX", opacity = 0.9f)
                    )
                )
            )
            else -> emptyList()
        }

        return StudioProject(
            id = UUID.randomUUID().toString(),
            title = title,
            type = type,
            fps = fps,
            aspectRatio = "16:9",
            onionSkin = OnionSkinConfig(),
            frames = initialFrames,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Generates a starter 5-frame katana slash anime sequence so creators can immediately
     * experience onion skinning, layers, timeline scrubbing, and GIF/MP4 export on launch.
     */
    fun createStarterKatanaProject(): StudioProject {
        val slashAngles = listOf(
            // Frame 1: Ready stance
            listOf(
                StrokePoint(0.26f, 0.62f),
                StrokePoint(0.32f, 0.44f),
                StrokePoint(0.30f, 0.25f)
            ),
            // Frame 2: Wind up
            listOf(
                StrokePoint(0.28f, 0.56f),
                StrokePoint(0.42f, 0.34f),
                StrokePoint(0.55f, 0.20f)
            ),
            // Frame 3: Peak slash arc
            listOf(
                StrokePoint(0.35f, 0.22f),
                StrokePoint(0.62f, 0.36f),
                StrokePoint(0.80f, 0.58f)
            ),
            // Frame 4: Follow-through impact
            listOf(
                StrokePoint(0.48f, 0.45f),
                StrokePoint(0.72f, 0.65f),
                StrokePoint(0.84f, 0.78f)
            ),
            // Frame 5: Recovery sheath
            listOf(
                StrokePoint(0.34f, 0.66f),
                StrokePoint(0.50f, 0.69f),
                StrokePoint(0.64f, 0.72f)
            )
        )

        val characterOffsetX = listOf(0.22f, 0.27f, 0.35f, 0.42f, 0.44f)

        val frames = slashAngles.mapIndexed { idx, bladePts ->
            val cx = characterOffsetX[idx]
            val cy = 0.58f

            val sketchLayer = StudioLayer(
                id = "starter_sketch_$idx",
                name = "Background",
                isVisible = true,
                opacity = 0.55f,
                strokes = listOf(
                    DrawStroke(
                        id = "guide_circle_$idx",
                        points = listOf(StrokePoint(cx - 0.05f, cy - 0.16f), StrokePoint(cx + 0.05f, cy - 0.06f)),
                        colorArgb = 0xFF6B6B8A,
                        strokeWidth = 0.005f,
                        tool = DrawTool.CIRCLE
                    )
                )
            )

            val inkLayer = StudioLayer(
                id = "starter_ink_$idx",
                name = "Line Art",
                isVisible = true,
                opacity = 1f,
                strokes = listOf(
                    // Ronin torso & head silhouette
                    DrawStroke(
                        id = "ronin_head_$idx",
                        points = listOf(StrokePoint(cx - 0.035f, cy - 0.15f), StrokePoint(cx + 0.035f, cy - 0.07f)),
                        colorArgb = 0xFFF8FAFC,
                        strokeWidth = 0.008f,
                        tool = DrawTool.CIRCLE
                    ),
                    DrawStroke(
                        id = "ronin_body_$idx",
                        points = listOf(
                            StrokePoint(cx, cy - 0.07f),
                            StrokePoint(cx - 0.04f, cy + 0.12f),
                            StrokePoint(cx + 0.05f, cy + 0.12f),
                            StrokePoint(cx, cy - 0.07f)
                        ),
                        colorArgb = 0xFFF8FAFC,
                        strokeWidth = 0.009f,
                        tool = DrawTool.INK_BRUSH
                    ),
                    // Katana blade
                    DrawStroke(
                        id = "blade_$idx",
                        points = bladePts,
                        colorArgb = 0xFFC084FC,
                        strokeWidth = 0.013f,
                        tool = DrawTool.KATANA_BLADE,
                        glowIntensity = 0.45f,
                        pointWeights = listOf(0.55f, 1.20f, 0.40f)
                    )
                )
            )

            val fxLayer = StudioLayer(
                id = "starter_fx_$idx",
                name = "Neon Slash FX",
                isVisible = true,
                opacity = 0.92f,
                strokes = if (idx in 1..3) {
                    listOf(
                        DrawStroke(
                            id = "fx_arc_$idx",
                            points = listOf(
                                StrokePoint(bladePts.first().x - 0.06f, bladePts.first().y + 0.04f),
                                StrokePoint(bladePts[1].x + 0.04f, bladePts[1].y - 0.04f),
                                StrokePoint(bladePts.last().x + 0.05f, bladePts.last().y + 0.02f)
                            ),
                            colorArgb = 0xFF22D3EE,
                            strokeWidth = 0.014f,
                            tool = DrawTool.NEON_AURA,
                            glowIntensity = 0.85f
                        )
                    )
                } else {
                    emptyList()
                }
            )

            AnimationFrame(
                id = "starter_frame_$idx",
                frameNumber = idx + 1,
                layers = listOf(sketchLayer, inkLayer, fxLayer)
            )
        }

        return StudioProject(
            id = "starter-katana-slash",
            title = "Katana Slash Cut",
            type = ProjectType.ANIMATION,
            fps = 8,
            aspectRatio = "16:9",
            onionSkin = OnionSkinConfig(
                enabled = true,
                beforeCount = 1,
                afterCount = 1,
                beforeOpacity = 0.40f,
                afterOpacity = 0.30f,
                tintEnabled = true
            ),
            frames = frames,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Generates a 3-frame anime pose sketchbook for Drawing Studio so creators can immediately
     * toggle Previous & Next frame onion skin overlays on the drawing canvas.
     */
    fun createStarterDrawingSketchbookProject(): StudioProject {
        val poses = listOf(
            Triple(0.32f, 0.25f, 0.28f), // Frame 1: Ready stance
            Triple(0.48f, 0.44f, 0.36f), // Frame 2: Mid-dash pose
            Triple(0.65f, 0.62f, 0.48f)  // Frame 3: Guard arc
        )

        val frames = poses.mapIndexed { idx, (headX, bodyX, swordTipY) ->
            val bgLayer = StudioLayer(
                id = "draw_bg_$idx",
                name = "Background",
                isVisible = true,
                opacity = 0.55f,
                strokes = listOf(
                    DrawStroke(
                        id = "draw_horizon_$idx",
                        points = listOf(StrokePoint(0.10f, 0.76f), StrokePoint(0.90f, 0.76f)),
                        colorArgb = 0xFF6B6B8A,
                        strokeWidth = 0.005f,
                        tool = DrawTool.LINE
                    )
                )
            )
            val lineLayer = StudioLayer(
                id = "draw_line_$idx",
                name = "Line Art",
                isVisible = true,
                opacity = 1f,
                strokes = listOf(
                    DrawStroke(
                        id = "draw_head_$idx",
                        points = listOf(StrokePoint(headX - 0.04f, 0.28f), StrokePoint(headX + 0.04f, 0.36f)),
                        colorArgb = 0xFFF8FAFC,
                        strokeWidth = 0.009f,
                        tool = DrawTool.CIRCLE
                    ),
                    DrawStroke(
                        id = "draw_torso_$idx",
                        points = listOf(
                            StrokePoint(headX, 0.36f),
                            StrokePoint(bodyX - 0.05f, 0.58f),
                            StrokePoint(bodyX + 0.06f, 0.58f),
                            StrokePoint(headX, 0.36f)
                        ),
                        colorArgb = 0xFFF8FAFC,
                        strokeWidth = 0.010f,
                        tool = DrawTool.INK_BRUSH
                    ),
                    DrawStroke(
                        id = "draw_blade_$idx",
                        points = listOf(
                            StrokePoint(bodyX + 0.03f, 0.47f),
                            StrokePoint(bodyX + 0.18f, swordTipY)
                        ),
                        colorArgb = 0xFFC084FC,
                        strokeWidth = 0.011f,
                        tool = DrawTool.PEN
                    )
                )
            )
            val fxLayer = StudioLayer(
                id = "draw_fx_$idx",
                name = "Neon FX",
                isVisible = true,
                opacity = 0.85f
            )
            AnimationFrame(
                id = "draw_frame_$idx",
                frameNumber = idx + 1,
                layers = listOf(bgLayer, lineLayer, fxLayer)
            )
        }

        return StudioProject(
            id = "starter-drawing-sketchbook",
            title = "Ronin Pose Sketchbook",
            type = ProjectType.DRAWING,
            fps = 8,
            aspectRatio = "4:3",
            onionSkin = OnionSkinConfig(
                enabled = true,
                showBefore = true,
                showAfter = true,
                beforeCount = 1,
                afterCount = 1,
                beforeOpacity = 0.40f,
                afterOpacity = 0.30f,
                tintEnabled = true
            ),
            frames = frames,
            updatedAt = System.currentTimeMillis() - 1000L
        )
    }
}
