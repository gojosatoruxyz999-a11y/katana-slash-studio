package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import com.example.data.model.AnimationFrame
import com.example.data.model.DrawStroke
import com.example.data.model.DrawTool
import com.example.data.model.OnionSkinConfig
import com.example.data.model.StudioLayer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnionAfterCyan
import com.example.ui.theme.OnionBeforeRose
import com.example.ui.theme.StudioBorder
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Shared high-performance Compose vector canvas renderer for Animation Studio & Drawing Studio.
 * Supports:
 * - Subtle anime light-table grid & Onion Skinning ghosts
 * - Layer isolation via CompositingStrategy.Offscreen for clean Eraser strokes
 * - Professional studio brushes: Katana Blade (tapered), Neon Aura, Soft Airbrush & Glow Intensity
 */
@Composable
fun RoninVectorCanvas(
    frames: List<AnimationFrame>,
    currentFrameIndex: Int,
    activeLayerIndex: Int,
    activeStroke: DrawStroke?,
    onionSkin: OnionSkinConfig,
    isPlaying: Boolean,
    showGrid: Boolean = true,
    modifier: Modifier = Modifier
) {
    val currentFrame = frames.getOrNull(currentFrameIndex)
    val showOnion = onionSkin.enabled && !isPlaying && frames.size > 1

    Box(
        modifier = modifier.background(Color(0xFF0C0C16))
    ) {
        // 1. Light-table background + grid + Onion Skin Ghosts
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (showGrid) {
                drawAnimeLightTableGrid()
            }

            if (showOnion) {
                // Draw PREVIOUS frames (oldest first so closest frame is strongest)
                if (onionSkin.showBefore && onionSkin.beforeCount > 0) {
                    for (dist in onionSkin.beforeCount downTo 1) {
                        val idx = currentFrameIndex - dist
                        if (idx in frames.indices) {
                            val decay = 1f / dist.toFloat()
                            val alpha = (onionSkin.beforeOpacity * decay).coerceIn(0.05f, 0.85f)
                            val tint = if (onionSkin.tintEnabled) OnionBeforeRose else null
                            drawFrameFlat(frames[idx], alphaOverride = alpha, tintColor = tint)
                        }
                    }
                }

                // Draw NEXT frames (furthest first)
                if (onionSkin.showAfter && onionSkin.afterCount > 0) {
                    for (dist in onionSkin.afterCount downTo 1) {
                        val idx = currentFrameIndex + dist
                        if (idx in frames.indices) {
                            val decay = 1f / dist.toFloat()
                            val alpha = (onionSkin.afterOpacity * decay).coerceIn(0.05f, 0.85f)
                            val tint = if (onionSkin.tintEnabled) OnionAfterCyan else null
                            drawFrameFlat(frames[idx], alphaOverride = alpha, tintColor = tint)
                        }
                    }
                }
            }
        }

        // 2. Active Frame Layers — each layer gets an Offscreen compositing buffer for clean Eraser support
        if (currentFrame != null) {
            currentFrame.layers.forEachIndexed { layerIdx, layer ->
                if (layer.isVisible && layer.opacity > 0.01f) {
                    val liveStrokeForThisLayer = if (layerIdx == activeLayerIndex) activeStroke else null
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                                alpha = layer.opacity
                            }
                    ) {
                        drawLayerStrokes(
                            layer = layer,
                            liveStroke = liveStrokeForThisLayer,
                            alphaMultiplier = 1f,
                            tintColor = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FrameThumbnailCanvas(
    frame: AnimationFrame,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.background(ObsidianBg)
    ) {
        drawFrameFlat(frame, alphaOverride = 1f, tintColor = null)
    }
}

private fun DrawScope.drawAnimeLightTableGrid() {
    val step = min(size.width, size.height) / 8f
    if (step <= 4f) return
    val gridColor = StudioBorder.copy(alpha = 0.25f)
    var x = step
    while (x < size.width) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
    // Center crosshair
    val centerColor = StudioBorder.copy(alpha = 0.45f)
    drawLine(centerColor, Offset(size.width * 0.5f, 0f), Offset(size.width * 0.5f, size.height), strokeWidth = 1.2f)
    drawLine(centerColor, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), strokeWidth = 1.2f)
}

private fun DrawScope.drawFrameFlat(
    frame: AnimationFrame,
    alphaOverride: Float,
    tintColor: Color?
) {
    for (layer in frame.layers) {
        if (!layer.isVisible || layer.opacity <= 0.01f) continue
        drawLayerStrokes(
            layer = layer,
            liveStroke = null,
            alphaMultiplier = layer.opacity * alphaOverride,
            tintColor = tintColor
        )
    }
}

private fun DrawScope.drawLayerStrokes(
    layer: StudioLayer,
    liveStroke: DrawStroke?,
    alphaMultiplier: Float,
    tintColor: Color?
) {
    val minDim = min(size.width, size.height)
    for (stroke in layer.strokes) {
        drawSingleStroke(stroke, minDim, alphaMultiplier, tintColor)
    }
    if (liveStroke != null) {
        drawSingleStroke(liveStroke, minDim, alphaMultiplier, tintColor)
    }
}

private fun DrawScope.drawSingleStroke(
    stroke: DrawStroke,
    minDim: Float,
    alphaMultiplier: Float,
    tintColor: Color?
) {
    if (stroke.points.isEmpty()) return
    val w = size.width
    val h = size.height
    val basePx = max(1.5f, stroke.strokeWidth * minDim)

    if (stroke.tool == DrawTool.ERASER) {
        if (tintColor != null) return
        val path = buildComposePath(stroke, w, h)
        drawPath(
            path = path,
            color = Color.Transparent,
            style = Stroke(width = basePx * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = BlendMode.Clear
        )
        return
    }

    val rawColor = tintColor ?: Color(stroke.colorArgb.toInt())
    val effectiveGlow = when (stroke.tool) {
        DrawTool.NEON_AURA -> max(stroke.glowIntensity, 0.72f)
        else -> stroke.glowIntensity.coerceIn(0f, 1f)
    }
    val toolAlpha = when (stroke.tool) {
        DrawTool.MARKER -> 0.55f
        DrawTool.SOFT_AIRBRUSH -> 0.85f
        else -> 1f
    }
    val finalAlpha = (rawColor.alpha * stroke.alpha * toolAlpha * alphaMultiplier).coerceIn(0f, 1f)
    val finalColor = rawColor.copy(alpha = finalAlpha)
    val widthPx = when (stroke.tool) {
        DrawTool.INK_BRUSH -> basePx * 1.25f
        DrawTool.MARKER -> basePx * 1.9f
        DrawTool.SOFT_AIRBRUSH -> basePx * 2.2f
        else -> basePx
    }

    when (stroke.tool) {
        DrawTool.LINE -> {
            val p0 = stroke.points.first()
            val p1 = stroke.points.last()
            val start = Offset(p0.x * w, p0.y * h)
            val end = Offset(p1.x * w, p1.y * h)
            if (effectiveGlow > 0.02f && tintColor == null) {
                drawLine(
                    color = finalColor.copy(alpha = (finalAlpha * 0.25f * effectiveGlow).coerceIn(0f, 1f)),
                    start = start,
                    end = end,
                    strokeWidth = widthPx * (2.2f + effectiveGlow * 1.8f),
                    cap = StrokeCap.Round
                )
            }
            drawLine(
                color = finalColor,
                start = start,
                end = end,
                strokeWidth = widthPx,
                cap = StrokeCap.Round
            )
        }
        DrawTool.RECT -> {
            val p0 = stroke.points.first()
            val p1 = stroke.points.last()
            val left = min(p0.x, p1.x) * w
            val top = min(p0.y, p1.y) * h
            val rw = kotlin.math.abs(p1.x - p0.x) * w
            val rh = kotlin.math.abs(p1.y - p0.y) * h
            if (effectiveGlow > 0.02f && tintColor == null) {
                drawRect(
                    color = finalColor.copy(alpha = (finalAlpha * 0.24f * effectiveGlow).coerceIn(0f, 1f)),
                    topLeft = Offset(left, top),
                    size = Size(rw, rh),
                    style = Stroke(width = widthPx * (2.2f + effectiveGlow * 1.6f), join = StrokeJoin.Round)
                )
            }
            drawRect(
                color = finalColor,
                topLeft = Offset(left, top),
                size = Size(rw, rh),
                style = Stroke(width = widthPx, join = StrokeJoin.Round)
            )
        }
        DrawTool.CIRCLE -> {
            val p0 = stroke.points.first()
            val p1 = stroke.points.last()
            val cx = (p0.x + p1.x) * 0.5f * w
            val cy = (p0.y + p1.y) * 0.5f * h
            val r = max(2f, hypot((p1.x - p0.x) * w, (p1.y - p0.y) * h) * 0.5f)
            if (effectiveGlow > 0.02f && tintColor == null) {
                drawCircle(
                    color = finalColor.copy(alpha = (finalAlpha * 0.24f * effectiveGlow).coerceIn(0f, 1f)),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = widthPx * (2.2f + effectiveGlow * 1.6f))
                )
            }
            drawCircle(
                color = finalColor,
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = widthPx)
            )
        }
        DrawTool.KATANA_BLADE -> {
            drawKatanaBladeStroke(
                stroke = stroke,
                w = w,
                h = h,
                basePx = basePx,
                color = finalColor,
                alphaMultiplier = finalAlpha,
                glowIntensity = if (tintColor == null) effectiveGlow else 0f
            )
        }
        DrawTool.NEON_AURA -> {
            val path = buildComposePath(stroke, w, h)
            val auraAlpha = (finalAlpha * (0.14f + 0.16f * effectiveGlow)).coerceIn(0f, 1f)
            val midAlpha = (finalAlpha * (0.30f + 0.22f * effectiveGlow)).coerceIn(0f, 1f)
            if (tintColor == null) {
                drawPath(
                    path = path,
                    color = finalColor.copy(alpha = auraAlpha),
                    style = Stroke(width = widthPx * (3.2f + effectiveGlow * 2.2f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = path,
                    color = finalColor.copy(alpha = midAlpha),
                    style = Stroke(width = widthPx * (1.8f + effectiveGlow * 0.9f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            val coreColor = if (tintColor == null) lerp(finalColor, Color.White, 0.55f).copy(alpha = finalAlpha) else finalColor
            drawPath(
                path = path,
                color = coreColor,
                style = Stroke(width = max(1.5f, widthPx * 0.65f), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        DrawTool.SOFT_AIRBRUSH -> {
            val path = buildComposePath(stroke, w, h)
            if (effectiveGlow > 0.05f && tintColor == null) {
                drawPath(
                    path = path,
                    color = finalColor.copy(alpha = (finalAlpha * 0.08f * effectiveGlow).coerceIn(0f, 1f)),
                    style = Stroke(width = widthPx * 2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            drawPath(
                path = path,
                color = finalColor.copy(alpha = (finalAlpha * 0.12f).coerceIn(0f, 1f)),
                style = Stroke(width = widthPx * 1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = path,
                color = finalColor.copy(alpha = (finalAlpha * 0.22f).coerceIn(0f, 1f)),
                style = Stroke(width = widthPx * 1.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = path,
                color = finalColor.copy(alpha = (finalAlpha * 0.35f).coerceIn(0f, 1f)),
                style = Stroke(width = widthPx * 0.52f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        else -> {
            val path = buildComposePath(stroke, w, h)
            if (effectiveGlow > 0.02f && tintColor == null) {
                drawPath(
                    path = path,
                    color = finalColor.copy(alpha = (finalAlpha * 0.16f * effectiveGlow).coerceIn(0f, 1f)),
                    style = Stroke(width = widthPx * (2.6f + effectiveGlow * 1.6f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = path,
                    color = finalColor.copy(alpha = (finalAlpha * 0.30f * effectiveGlow).coerceIn(0f, 1f)),
                    style = Stroke(width = widthPx * (1.65f + effectiveGlow * 0.8f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            drawPath(
                path = path,
                color = finalColor,
                style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

private fun DrawScope.drawKatanaBladeStroke(
    stroke: DrawStroke,
    w: Float,
    h: Float,
    basePx: Float,
    color: Color,
    alphaMultiplier: Float,
    glowIntensity: Float
) {
    val pts = stroke.points
    if (pts.size == 1) {
        val c = Offset(pts[0].x * w, pts[0].y * h)
        drawCircle(color = color, radius = max(1.5f, basePx * 0.35f), center = c)
        return
    }

    val lastIdx = (pts.size - 1).coerceAtLeast(1)
    for (i in 0 until pts.size - 1) {
        val p0 = pts[i]
        val p1 = pts[i + 1]
        val t = (i + 0.5f) / lastIdx.toFloat()
        // Sharp katana profile: narrow entry, keen belly, razor-sharp exit tip
        val curveTaper = (0.14f + 1.25f * sin(PI * t).toFloat()).coerceIn(0.10f, 1.35f)
        val velocityWeight = if (i < stroke.pointWeights.size) stroke.pointWeights[i] else 1f
        val segWidth = max(1.2f, basePx * curveTaper * velocityWeight)
        val start = Offset(p0.x * w, p0.y * h)
        val end = Offset(p1.x * w, p1.y * h)

        if (glowIntensity > 0.02f) {
            drawLine(
                color = color.copy(alpha = (alphaMultiplier * 0.24f * glowIntensity).coerceIn(0f, 1f)),
                start = start,
                end = end,
                strokeWidth = segWidth * (2.1f + glowIntensity * 1.4f),
                cap = StrokeCap.Round
            )
        }
        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = segWidth,
            cap = StrokeCap.Round
        )
        // Specular razor edge highlight
        if (glowIntensity > 0.1f) {
            drawLine(
                color = Color.White.copy(alpha = (alphaMultiplier * 0.45f).coerceIn(0f, 1f)),
                start = start,
                end = end,
                strokeWidth = max(0.8f, segWidth * 0.26f),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun buildComposePath(stroke: DrawStroke, w: Float, h: Float): Path {
    val path = Path()
    val pts = stroke.points
    if (pts.size == 1) {
        val x = pts[0].x * w
        val y = pts[0].y * h
        path.moveTo(x, y)
        path.lineTo(x + 0.5f, y + 0.5f)
        return path
    }
    path.moveTo(pts[0].x * w, pts[0].y * h)
    for (i in 1 until pts.size) {
        val prev = pts[i - 1]
        val curr = pts[i]
        val midX = ((prev.x + curr.x) * 0.5f) * w
        val midY = ((prev.y + curr.y) * 0.5f) * h
        path.quadraticTo(prev.x * w, prev.y * h, midX, midY)
    }
    val last = pts.last()
    path.lineTo(last.x * w, last.y * h)
    return path
}
