package com.example.ui.video

import com.example.data.model.KeyframeEasing
import com.example.data.model.OverlayKeyframe
import com.example.data.model.OverlayTargetType
import com.example.data.model.OverlayTransform
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

data class MotionBlurSample(
    val transform: OverlayTransform,
    val alphaFactor: Float
)

data class MotionBlurState(
    val speedNormalized: Float = 0f,
    val blurRadiusXRatio: Float = 0f,
    val blurRadiusYRatio: Float = 0f,
    val trailSamples: List<MotionBlurSample> = emptyList()
)

object KeyframeInterpolator {

    const val KEYFRAME_SNAP_MS = 100L

    fun evaluateMotionBlur(
        keyframes: List<OverlayKeyframe>,
        timeMs: Long,
        target: OverlayTargetType,
        enabled: Boolean,
        intensity: Float,
        sampleCount: Int = 5
    ): MotionBlurState {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        if (!enabled || clampedIntensity <= 0.01f || keyframes.size < 2 || sampleCount <= 0) {
            return MotionBlurState()
        }

        val sorted = keyframes.sortedBy { it.timeMs }
        val minTime = sorted.first().timeMs
        val maxTime = sorted.last().timeMs
        if (timeMs <= minTime || timeMs >= maxTime) {
            return MotionBlurState()
        }

        val windowMs = 45L
        val beforeTf = evaluate(sorted, (timeMs - windowMs).coerceAtLeast(minTime), target)
        val afterTf = evaluate(sorted, (timeMs + windowMs).coerceAtMost(maxTime), target)

        val vx = abs(afterTf.x - beforeTf.x)
        val vy = abs(afterTf.y - beforeTf.y)
        val vScale = abs(afterTf.scale - beforeTf.scale) * 0.35f
        val vRot = abs(afterTf.rotationDeg - beforeTf.rotationDeg) / 90f
        val speed = sqrt(vx * vx + vy * vy + vScale * vScale + vRot * vRot)

        if (speed < 0.002f) {
            return MotionBlurState()
        }

        val speedNormalized = (speed / 0.12f).coerceIn(0.15f, 1f)
        val shutterSpanMs = (150f * clampedIntensity).toLong().coerceAtLeast(24L)
        val samples = buildList {
            for (i in 1..sampleCount) {
                // i = 1 is closest to current timeMs (strongest), i = sampleCount is oldest trail
                val fractionBack = i.toFloat() / (sampleCount + 0.5f)
                val sampleTime = (timeMs - (shutterSpanMs * fractionBack).toLong()).coerceAtLeast(minTime)
                val tf = evaluate(sorted, sampleTime, target)
                val falloff = 1f - ((i - 1).toFloat() / sampleCount.toFloat())
                val alphaFactor = (0.32f * clampedIntensity * speedNormalized * (0.35f + 0.65f * falloff))
                    .coerceIn(0.03f, 0.45f)
                add(MotionBlurSample(transform = tf, alphaFactor = alphaFactor))
            }
        }

        return MotionBlurState(
            speedNormalized = speedNormalized,
            blurRadiusXRatio = ((vx / 0.08f).coerceIn(0f, 1f) * clampedIntensity),
            blurRadiusYRatio = ((vy / 0.08f).coerceIn(0f, 1f) * clampedIntensity),
            trailSamples = samples
        )
    }

    fun defaultTransform(target: OverlayTargetType): OverlayTransform {
        return when (target) {
            OverlayTargetType.TEXT -> OverlayTransform(
                x = 0.5f,
                y = 0.82f,
                scale = 1f,
                rotationDeg = 0f,
                opacity = 1f
            )
            OverlayTargetType.IMAGE -> OverlayTransform(
                x = 0.5f,
                y = 0.45f,
                scale = 1f,
                rotationDeg = 0f,
                opacity = 1f
            )
        }
    }

    fun evaluate(
        keyframes: List<OverlayKeyframe>,
        timeMs: Long,
        target: OverlayTargetType
    ): OverlayTransform {
        if (keyframes.isEmpty()) return defaultTransform(target)
        val sorted = keyframes.sortedBy { it.timeMs }
        val first = sorted.first()
        if (sorted.size == 1 || timeMs <= first.timeMs) {
            return first.toTransform()
        }
        val last = sorted.last()
        if (timeMs >= last.timeMs) {
            return last.toTransform()
        }

        var prev = first
        var next = last
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (timeMs >= a.timeMs && timeMs <= b.timeMs) {
                prev = a
                next = b
                break
            }
        }

        val span = (next.timeMs - prev.timeMs).toFloat()
        if (span <= 0f) return next.toTransform()

        val rawT = ((timeMs - prev.timeMs).toFloat() / span).coerceIn(0f, 1f)
        val easedT = applyEasing(rawT, prev.easing)

        return OverlayTransform(
            x = lerp(prev.x, next.x, easedT),
            y = lerp(prev.y, next.y, easedT),
            scale = lerp(prev.scale, next.scale, easedT),
            rotationDeg = lerp(prev.rotationDeg, next.rotationDeg, easedT),
            opacity = lerp(prev.opacity, next.opacity, easedT).coerceIn(0f, 1f)
        )
    }

    fun applyEasing(t: Float, easing: KeyframeEasing): Float {
        val clamped = t.coerceIn(0f, 1f)
        return when (easing) {
            KeyframeEasing.LINEAR -> clamped
            KeyframeEasing.EASE_IN -> clamped * clamped
            KeyframeEasing.EASE_OUT -> {
                val inv = 1f - clamped
                1f - inv * inv
            }
            KeyframeEasing.EASE_IN_OUT -> {
                if (clamped < 0.5f) {
                    2f * clamped * clamped
                } else {
                    val v = -2f * clamped + 2f
                    1f - (v * v) / 2f
                }
            }
        }
    }

    fun findKeyframeIndexAt(
        keyframes: List<OverlayKeyframe>,
        timeMs: Long,
        toleranceMs: Long = KEYFRAME_SNAP_MS
    ): Int {
        var bestIdx = -1
        var bestDist = Long.MAX_VALUE
        keyframes.forEachIndexed { idx, kf ->
            val dist = kotlin.math.abs(kf.timeMs - timeMs)
            if (dist <= toleranceMs && dist < bestDist) {
                bestDist = dist
                bestIdx = idx
            }
        }
        return bestIdx
    }

    /**
     * Preserves motion continuity when splitting or cutting a clip into [startMs..endMs].
     * Keeps all keyframes inside the range and bakes boundary keyframes if motion crosses a cut point.
     */
    fun sliceKeyframesForRange(
        keyframes: List<OverlayKeyframe>,
        startMs: Long,
        endMs: Long,
        target: OverlayTargetType
    ): List<OverlayKeyframe> {
        if (keyframes.isEmpty() || endMs <= startMs) return emptyList()
        val sorted = keyframes.sortedBy { it.timeMs }
        val inside = sorted.filter { it.timeMs in startMs..endMs }.toMutableList()

        val hasBefore = sorted.any { it.timeMs < startMs }
        val hasAfter = sorted.any { it.timeMs > endMs }

        if (hasBefore && inside.none { kotlin.math.abs(it.timeMs - startMs) <= 40L }) {
            val tf = evaluate(sorted, startMs, target)
            val prevEasing = sorted.lastOrNull { it.timeMs < startMs }?.easing ?: KeyframeEasing.EASE_IN_OUT
            inside.add(
                OverlayKeyframe(
                    id = UUID.randomUUID().toString(),
                    timeMs = startMs,
                    x = tf.x,
                    y = tf.y,
                    scale = tf.scale,
                    rotationDeg = tf.rotationDeg,
                    opacity = tf.opacity,
                    easing = prevEasing
                )
            )
        }

        if (hasAfter && inside.none { kotlin.math.abs(it.timeMs - endMs) <= 40L }) {
            val tf = evaluate(sorted, endMs, target)
            inside.add(
                OverlayKeyframe(
                    id = UUID.randomUUID().toString(),
                    timeMs = endMs,
                    x = tf.x,
                    y = tf.y,
                    scale = tf.scale,
                    rotationDeg = tf.rotationDeg,
                    opacity = tf.opacity,
                    easing = KeyframeEasing.EASE_IN_OUT
                )
            )
        }

        return inside.sortedBy { it.timeMs }
    }

    private fun OverlayKeyframe.toTransform(): OverlayTransform {
        return OverlayTransform(
            x = x,
            y = y,
            scale = scale,
            rotationDeg = rotationDeg,
            opacity = opacity
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
