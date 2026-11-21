package com.example.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.min

/**
 * Renders a sleek 1-second dark neon outro card directly onto Android Canvas
 * for exported GIF and MP4 animations.
 */
object OutroCardRenderer {

    fun drawOutroFrame(
        canvas: Canvas,
        creatorText: String,
        width: Int,
        height: Int,
        progress: Float
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = progress.coerceIn(0f, 1f)
        val minDim = min(w, h)

        // 1. Dark studio backdrop with radial neon purple aura
        canvas.drawColor(Color.rgb(6, 6, 12))
        val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.5f,
                h * 0.45f,
                w * 0.65f,
                intArrayOf(
                    Color.argb(95, 124, 58, 237),
                    Color.argb(40, 34, 211, 238),
                    Color.argb(0, 6, 6, 12)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, auraPaint)

        // 2. Animated diagonal katana slash arc
        val slashProgress = (t / 0.65f).coerceIn(0f, 1f)
        val sx = w * 0.14f
        val sy = h * 0.68f
        val cx = w * 0.50f
        val cy = h * 0.28f
        val ex = w * 0.86f
        val ey = h * 0.34f

        val curEx = sx + (ex - sx) * slashProgress
        val curEy = sy + (ey - sy) * slashProgress
        val curCx = sx + (cx - sx) * slashProgress
        val curCy = sy + (cy - sy) * slashProgress

        val slashPath = Path().apply {
            moveTo(sx, sy)
            quadTo(curCx, curCy, curEx, curEy)
        }

        val slashFade = if (t > 0.7f) (1f - ((t - 0.7f) / 0.3f) * 0.25f) else 1f
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = minDim * 0.045f
            color = Color.argb((110 * slashFade).toInt().coerceIn(0, 255), 168, 85, 247)
        }
        canvas.drawPath(slashPath, glowPaint)

        val bladePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = minDim * 0.015f
            shader = LinearGradient(
                sx, sy, curEx.coerceAtLeast(sx + 1f), curEy,
                Color.rgb(192, 132, 252),
                Color.rgb(34, 211, 238),
                Shader.TileMode.CLAMP
            )
            alpha = (255 * slashFade).toInt().coerceIn(0, 255)
        }
        canvas.drawPath(slashPath, bladePaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = minDim * 0.005f
            color = Color.argb((240 * slashFade).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        canvas.drawPath(slashPath, corePaint)

        // 3. Creator Title & Ronin Motion Subtitle
        val textAlpha = ((t - 0.18f) / 0.32f).coerceIn(0f, 1f)
        val displayTitle = creatorText.trim().ifBlank { "Made with Katana Slash Canvas" }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = (minDim * 0.068f).coerceIn(14f, 54f)
            color = Color.argb((255 * textAlpha).toInt().coerceIn(0, 255), 248, 250, 252)
        }
        canvas.drawText(displayTitle, w * 0.5f, h * 0.56f, titlePaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
            textSize = (minDim * 0.036f).coerceIn(10f, 28f)
            color = Color.argb((220 * textAlpha).toInt().coerceIn(0, 255), 34, 211, 238)
        }
        canvas.drawText("RONIN MOTION • STUDIO EDITION", w * 0.5f, h * 0.65f, subtitlePaint)
    }
}
