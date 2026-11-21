package com.example.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.example.data.model.AnimationFrame
import com.example.data.model.DrawStroke
import com.example.data.model.DrawTool
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object FrameRenderer {

    fun drawFrameToAndroidCanvas(
        canvas: Canvas,
        frame: AnimationFrame,
        width: Int,
        height: Int,
        backgroundColor: Int = Color.rgb(10, 10, 18)
    ) {
        if (backgroundColor == Color.TRANSPARENT) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            canvas.drawColor(backgroundColor)
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val minDim = min(w, h)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }

        for (layer in frame.layers) {
            if (!layer.isVisible || layer.opacity <= 0.01f) continue

            val layerAlpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
            val saveCount = canvas.saveLayerAlpha(0f, 0f, w, h, layerAlpha)

            for (stroke in layer.strokes) {
                drawStroke(canvas, stroke, w, h, minDim, paint, clearPaint)
            }

            canvas.restoreToCount(saveCount)
        }
    }

    private fun drawStroke(
        canvas: Canvas,
        stroke: DrawStroke,
        w: Float,
        h: Float,
        minDim: Float,
        paint: Paint,
        clearPaint: Paint
    ) {
        if (stroke.points.isEmpty()) return
        val strokePx = max(1.5f, stroke.strokeWidth * minDim)

        if (stroke.tool == DrawTool.ERASER) {
            clearPaint.strokeWidth = strokePx * 1.6f
            val path = buildPath(stroke, w, h)
            canvas.drawPath(path, clearPaint)
            return
        }

        paint.style = Paint.Style.STROKE
        val widthPx = when (stroke.tool) {
            DrawTool.INK_BRUSH -> strokePx * 1.25f
            DrawTool.MARKER -> strokePx * 1.9f
            DrawTool.SOFT_AIRBRUSH -> strokePx * 2.2f
            else -> strokePx
        }
        val baseColor = stroke.colorArgb.toInt()
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)
        val alphaMultiplier = when (stroke.tool) {
            DrawTool.MARKER -> 0.55f * stroke.alpha
            DrawTool.SOFT_AIRBRUSH -> 0.85f * stroke.alpha
            else -> stroke.alpha
        }
        val finalAlpha = (Color.alpha(baseColor) * alphaMultiplier).toInt().coerceIn(0, 255)
        val effectiveGlow = when (stroke.tool) {
            DrawTool.NEON_AURA -> max(stroke.glowIntensity, 0.72f)
            else -> stroke.glowIntensity.coerceIn(0f, 1f)
        }

        when (stroke.tool) {
            DrawTool.LINE -> {
                val p0 = stroke.points.first()
                val p1 = stroke.points.last()
                if (effectiveGlow > 0.02f) {
                    paint.strokeWidth = widthPx * (2.2f + effectiveGlow * 1.8f)
                    paint.color = Color.argb((finalAlpha * 0.25f * effectiveGlow).toInt().coerceIn(0, 255), r, g, b)
                    canvas.drawLine(p0.x * w, p0.y * h, p1.x * w, p1.y * h, paint)
                }
                paint.strokeWidth = widthPx
                paint.color = Color.argb(finalAlpha, r, g, b)
                canvas.drawLine(p0.x * w, p0.y * h, p1.x * w, p1.y * h, paint)
            }
            DrawTool.RECT -> {
                val p0 = stroke.points.first()
                val p1 = stroke.points.last()
                val rect = RectF(
                    min(p0.x, p1.x) * w,
                    min(p0.y, p1.y) * h,
                    max(p0.x, p1.x) * w,
                    max(p0.y, p1.y) * h
                )
                if (effectiveGlow > 0.02f) {
                    paint.strokeWidth = widthPx * (2.2f + effectiveGlow * 1.6f)
                    paint.color = Color.argb((finalAlpha * 0.24f * effectiveGlow).toInt().coerceIn(0, 255), r, g, b)
                    canvas.drawRect(rect, paint)
                }
                paint.strokeWidth = widthPx
                paint.color = Color.argb(finalAlpha, r, g, b)
                canvas.drawRect(rect, paint)
            }
            DrawTool.CIRCLE -> {
                val p0 = stroke.points.first()
                val p1 = stroke.points.last()
                val cx = (p0.x + p1.x) * 0.5f * w
                val cy = (p0.y + p1.y) * 0.5f * h
                val rx = max(2f, hypot((p1.x - p0.x) * w, (p1.y - p0.y) * h) * 0.5f)
                if (effectiveGlow > 0.02f) {
                    paint.strokeWidth = widthPx * (2.2f + effectiveGlow * 1.6f)
                    paint.color = Color.argb((finalAlpha * 0.24f * effectiveGlow).toInt().coerceIn(0, 255), r, g, b)
                    canvas.drawCircle(cx, cy, rx, paint)
                }
                paint.strokeWidth = widthPx
                paint.color = Color.argb(finalAlpha, r, g, b)
                canvas.drawCircle(cx, cy, rx, paint)
            }
            DrawTool.KATANA_BLADE -> {
                val pts = stroke.points
                if (pts.size == 1) {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb(finalAlpha, r, g, b)
                    canvas.drawCircle(pts[0].x * w, pts[0].y * h, max(1.5f, strokePx * 0.35f), paint)
                    return
                }
                val lastIdx = (pts.size - 1).coerceAtLeast(1)
                for (i in 0 until pts.size - 1) {
                    val p0 = pts[i]
                    val p1 = pts[i + 1]
                    val t = (i + 0.5f) / lastIdx.toFloat()
                    val curveTaper = (0.14f + 1.25f * sin(PI * t).toFloat()).coerceIn(0.10f, 1.35f)
                    val velocityWeight = if (i < stroke.pointWeights.size) stroke.pointWeights[i] else 1f
                    val segWidth = max(1.2f, strokePx * curveTaper * velocityWeight)
                    if (effectiveGlow > 0.02f) {
                        paint.strokeWidth = segWidth * (2.1f + effectiveGlow * 1.4f)
                        paint.color = Color.argb((finalAlpha * 0.24f * effectiveGlow).toInt().coerceIn(0, 255), r, g, b)
                        canvas.drawLine(p0.x * w, p0.y * h, p1.x * w, p1.y * h, paint)
                    }
                    paint.strokeWidth = segWidth
                    paint.color = Color.argb(finalAlpha, r, g, b)
                    canvas.drawLine(p0.x * w, p0.y * h, p1.x * w, p1.y * h, paint)
                    if (effectiveGlow > 0.1f) {
                        paint.strokeWidth = max(0.8f, segWidth * 0.26f)
                        paint.color = Color.argb((finalAlpha * 0.45f).toInt().coerceIn(0, 255), 255, 255, 255)
                        canvas.drawLine(p0.x * w, p0.y * h, p1.x * w, p1.y * h, paint)
                    }
                }
            }
            DrawTool.NEON_AURA -> {
                val path = buildPath(stroke, w, h)
                paint.strokeWidth = widthPx * (3.2f + effectiveGlow * 2.2f)
                paint.color = Color.argb((finalAlpha * (0.14f + 0.16f * effectiveGlow)).toInt().coerceIn(0, 255), r, g, b)
                canvas.drawPath(path, paint)

                paint.strokeWidth = widthPx * (1.8f + effectiveGlow * 0.9f)
                paint.color = Color.argb((finalAlpha * (0.30f + 0.22f * effectiveGlow)).toInt().coerceIn(0, 255), r, g, b)
                canvas.drawPath(path, paint)

                val coreR = (r + (255 - r) * 0.55f).toInt().coerceIn(0, 255)
                val coreG = (g + (255 - g) * 0.55f).toInt().coerceIn(0, 255)
                val coreB = (b + (255 - b) * 0.55f).toInt().coerceIn(0, 255)
                paint.strokeWidth = max(1.5f, widthPx * 0.65f)
                paint.color = Color.argb(finalAlpha, coreR, coreG, coreB)
                canvas.drawPath(path, paint)
            }
            DrawTool.SOFT_AIRBRUSH -> {
                val path = buildPath(stroke, w, h)
                if (effectiveGlow > 0.05f) {
                    paint.strokeWidth = widthPx * 2.3f
                    paint.color = Color.argb((finalAlpha * 0.08f * effectiveGlow).toInt().coerceIn(0, 255), r, g, b)
                    canvas.drawPath(path, paint)
                }
                paint.strokeWidth = widthPx * 1.65f
                paint.color = Color.argb((finalAlpha * 0.12f).toInt().coerceIn(0, 255), r, g, b)
                canvas.drawPath(path, paint)

                paint.strokeWidth = widthPx * 1.05f
                paint.color = Color.argb((finalAlpha * 0.22f).toInt().coerceIn(0, 255), r, g, b)
                canvas.drawPath(path, paint)

                paint.strokeWidth = widthPx * 0.52f
                paint.color = Color.argb((finalAlpha * 0.35f).toInt().coerceIn(0, 255), r, g, b)
                canvas.drawPath(path, paint)
            }
            else -> {
                val path = buildPath(stroke, w, h)
                if (effectiveGlow > 0.02f) {
                    paint.strokeWidth = widthPx * (2.6f + effectiveGlow * 1.6f)
                    paint.color = Color.argb((finalAlpha * 0.16f * effectiveGlow).toInt().coerceIn(0, 255), r, g, b)
                    canvas.drawPath(path, paint)

                    paint.strokeWidth = widthPx * (1.65f + effectiveGlow * 0.8f)
                    paint.color = Color.argb((finalAlpha * 0.30f * effectiveGlow).toInt().coerceIn(0, 255), r, g, b)
                    canvas.drawPath(path, paint)
                }
                paint.strokeWidth = widthPx
                paint.color = Color.argb(finalAlpha, r, g, b)
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun buildPath(stroke: DrawStroke, w: Float, h: Float): Path {
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
            path.quadTo(prev.x * w, prev.y * h, midX, midY)
        }
        val last = pts.last()
        path.lineTo(last.x * w, last.y * h)
        return path
    }
}
