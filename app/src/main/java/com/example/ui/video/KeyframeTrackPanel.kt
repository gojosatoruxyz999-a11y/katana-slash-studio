package com.example.ui.video

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.KeyframeEasing
import com.example.data.model.OverlayKeyframe
import com.example.data.model.OverlayTargetType
import com.example.data.model.VideoClipItem
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnionBeforeRose
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import kotlin.math.roundToInt

@Composable
fun KeyframeTrackPanel(
    clip: VideoClipItem,
    playheadMs: Long,
    selectedTarget: OverlayTargetType,
    onSelectTarget: (OverlayTargetType) -> Unit,
    onEditTextClick: () -> Unit,
    onImportImageClick: () -> Unit,
    onClearImageClick: () -> Unit,
    onSeekPlayhead: (Long) -> Unit,
    onAddOrUpdateKeyframe: () -> Unit,
    onDeleteKeyframeAtPlayhead: () -> Unit,
    onJumpToKeyframe: (OverlayKeyframe) -> Unit,
    onJumpAdjacentKeyframe: (Int) -> Unit,
    onClearAllKeyframes: () -> Unit,
    onUpdateTransform: (
        x: Float?,
        y: Float?,
        scale: Float?,
        rotationDeg: Float?,
        opacity: Float?,
        easing: KeyframeEasing?
    ) -> Unit,
    onToggleMotionBlur: (Boolean) -> Unit,
    onMotionBlurIntensityChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyframes = when (selectedTarget) {
        OverlayTargetType.TEXT -> clip.textKeyframes
        OverlayTargetType.IMAGE -> clip.imageKeyframes
    }.sortedBy { it.timeMs }

    val activeIdx = KeyframeInterpolator.findKeyframeIndexAt(keyframes, playheadMs)
    val isAtKeyframe = activeIdx >= 0
    val activeKeyframe = keyframes.getOrNull(activeIdx)
    val currentTransform = KeyframeInterpolator.evaluate(keyframes, playheadMs, selectedTarget)
    val activeEasing = activeKeyframe?.easing ?: KeyframeEasing.EASE_IN_OUT

    val hasContentForTarget = when (selectedTarget) {
        OverlayTargetType.TEXT -> clip.overlayText.isNotBlank()
        OverlayTargetType.IMAGE -> !clip.imageOverlayUri.isNullOrBlank()
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianBg,
        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header + Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiamondGlyph(
                        color = if (isAtKeyframe) CyberCyan else NeonPurpleBright,
                        filled = isAtKeyframe,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Overlay Keyframes",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "${keyframes.size} KF",
                    style = StudioMonoStyle,
                    color = if (keyframes.isNotEmpty()) CyberCyan else TextMuted
                )
            }

            // Target Switcher: Text Overlay vs Image Overlay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayTargetType.entries.forEach { target ->
                    val selected = target == selectedTarget
                    val count = when (target) {
                        OverlayTargetType.TEXT -> clip.textKeyframes.size
                        OverlayTargetType.IMAGE -> clip.imageKeyframes.size
                    }
                    Surface(
                        onClick = { onSelectTarget(target) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) NeonPurple.copy(alpha = 0.24f) else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (selected) NeonPurple else StudioBorder),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("overlay_target_${target.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (target == OverlayTargetType.TEXT) Icons.Rounded.TextFields else Icons.Rounded.Image,
                                contentDescription = null,
                                tint = if (selected) NeonPurpleBright else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${target.label.removeSuffix(" Overlay")} ($count)",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            // Quick Action Row for the active overlay content (Text prompt or Image Photo Picker)
            if (selectedTarget == OverlayTargetType.TEXT) {
                OutlinedButton(
                    onClick = onEditTextClick,
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("edit_text_overlay_button")
                ) {
                    Icon(Icons.Rounded.TextFields, contentDescription = null, tint = NeonPurpleBright, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (clip.overlayText.isBlank()) "Add Title / Subtitle Text" else "Edit Text: \"${clip.overlayText}\"",
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onImportImageClick,
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("import_image_overlay_button")
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (clip.imageOverlayUri.isNullOrBlank()) {
                                "Import Image Overlay"
                            } else {
                                clip.imageOverlayName.ifBlank { "Change Image Overlay" }
                            },
                            color = CyberCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!clip.imageOverlayUri.isNullOrBlank()) {
                        IconButton(
                            onClick = onClearImageClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("clear_image_overlay_button")
                        ) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = "Remove image overlay",
                                tint = OnionBeforeRose
                            )
                        }
                    }
                }
            }

            if (!hasContentForTarget) {
                Text(
                    text = if (selectedTarget == OverlayTargetType.TEXT) {
                        "Tip: Add overlay text above, then set keyframes to animate position, scale, rotation & fade."
                    } else {
                        "Tip: Import a PNG/JPEG sticker or watermark via Photo Picker, then animate it with keyframes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            // Keyframe Diamond Timeline Track
            KeyframeTimelineStrip(
                trimStartMs = clip.trimStartMs,
                trimEndMs = clip.trimEndMs,
                playheadMs = playheadMs,
                keyframes = keyframes,
                activeKeyframeId = activeKeyframe?.id,
                onSeekMs = onSeekPlayhead
            )

            // Keyframe Transport Controls: Prev | +/Update KF | Delete KF | Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { onJumpAdjacentKeyframe(-1) },
                    shape = RoundedCornerShape(10.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("prev_keyframe_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = "Previous keyframe",
                            tint = TextPrimary
                        )
                    }
                }

                Button(
                    onClick = onAddOrUpdateKeyframe,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAtKeyframe) CyberCyan else NeonPurple
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("add_keyframe_button")
                ) {
                    DiamondGlyph(
                        color = if (isAtKeyframe) VoidBlack else TextPrimary,
                        filled = true,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isAtKeyframe) "Keyframe @ ${formatMs(playheadMs)}" else "Add Keyframe",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isAtKeyframe) VoidBlack else TextPrimary,
                        maxLines = 1
                    )
                }

                Surface(
                    onClick = onDeleteKeyframeAtPlayhead,
                    enabled = isAtKeyframe,
                    shape = RoundedCornerShape(10.dp),
                    color = if (isAtKeyframe) OnionBeforeRose.copy(alpha = 0.16f) else StudioSurfaceElevated,
                    border = BorderStroke(1.dp, if (isAtKeyframe) OnionBeforeRose else StudioBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_keyframe_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete keyframe at playhead",
                            tint = if (isAtKeyframe) OnionBeforeRose else TextMuted
                        )
                    }
                }

                Surface(
                    onClick = { onJumpAdjacentKeyframe(1) },
                    shape = RoundedCornerShape(10.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("next_keyframe_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = "Next keyframe",
                            tint = TextPrimary
                        )
                    }
                }
            }

            // Keyframe Quick-Jump Chips
            if (keyframes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(keyframes, key = { it.id }) { kf ->
                            val selected = kf.id == activeKeyframe?.id
                            Surface(
                                onClick = { onJumpToKeyframe(kf) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) CyberCyan.copy(alpha = 0.2f) else StudioSurfaceElevated,
                                border = BorderStroke(1.dp, if (selected) CyberCyan else StudioBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    DiamondGlyph(
                                        color = if (selected) CyberCyan else NeonPurpleBright,
                                        filled = true,
                                        modifier = Modifier.size(9.dp)
                                    )
                                    Text(
                                        text = formatMs(kf.timeMs),
                                        style = StudioMonoStyle,
                                        color = if (selected) CyberCyan else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        onClick = onClearAllKeyframes,
                        shape = RoundedCornerShape(8.dp),
                        color = StudioSurfaceElevated,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.testTag("clear_all_keyframes_button")
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnionBeforeRose,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Live Transform Property Sliders (auto-keyframe at current playhead on drag)
            TransformSliderRow(
                label = "X Pos",
                valueText = "${(currentTransform.x * 100f).roundToInt()}%",
                value = currentTransform.x,
                valueRange = 0f..1f,
                accentColor = CyberCyan,
                testTag = "keyframe_x_slider",
                onValueChange = { newX ->
                    onUpdateTransform(newX, null, null, null, null, null)
                }
            )
            TransformSliderRow(
                label = "Y Pos",
                valueText = "${(currentTransform.y * 100f).roundToInt()}%",
                value = currentTransform.y,
                valueRange = 0f..1f,
                accentColor = CyberCyan,
                testTag = "keyframe_y_slider",
                onValueChange = { newY ->
                    onUpdateTransform(null, newY, null, null, null, null)
                }
            )
            TransformSliderRow(
                label = "Scale",
                valueText = "${"%.2f".format(currentTransform.scale)}x",
                value = currentTransform.scale,
                valueRange = 0.25f..2.5f,
                accentColor = NeonPurpleBright,
                testTag = "keyframe_scale_slider",
                onValueChange = { newScale ->
                    onUpdateTransform(null, null, newScale, null, null, null)
                }
            )
            TransformSliderRow(
                label = "Rotate",
                valueText = "${currentTransform.rotationDeg.roundToInt()}°",
                value = currentTransform.rotationDeg,
                valueRange = -180f..180f,
                accentColor = NeonPurpleBright,
                testTag = "keyframe_rotation_slider",
                onValueChange = { newRot ->
                    onUpdateTransform(null, null, null, newRot, null, null)
                }
            )
            TransformSliderRow(
                label = "Opacity",
                valueText = "${(currentTransform.opacity * 100f).roundToInt()}%",
                value = currentTransform.opacity,
                valueRange = 0f..1f,
                accentColor = CyberCyan,
                testTag = "keyframe_opacity_slider",
                onValueChange = { newAlpha ->
                    onUpdateTransform(null, null, null, null, newAlpha, null)
                }
            )

            // Easing Curve Selector
            Text("Segment Easing", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(KeyframeEasing.entries) { easing ->
                    val active = easing == activeEasing
                    Surface(
                        onClick = {
                            onUpdateTransform(null, null, null, null, null, easing)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) NeonPurple.copy(alpha = 0.24f) else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (active) NeonPurple else StudioBorder),
                        modifier = Modifier.testTag("easing_${easing.name.lowercase()}")
                    ) {
                        Text(
                            text = easing.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) TextPrimary else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Motion Blur Effect Option for Smooth Keyframe Transitions
            val liveBlur = KeyframeInterpolator.evaluateMotionBlur(
                keyframes = keyframes,
                timeMs = playheadMs,
                target = selectedTarget,
                enabled = clip.motionBlurEnabled,
                intensity = clip.motionBlurIntensity
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioSurfaceElevated,
                border = BorderStroke(
                    1.dp,
                    if (clip.motionBlurEnabled) CyberCyan.copy(alpha = 0.6f) else StudioBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Motion Blur",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                                if (clip.motionBlurEnabled) {
                                    Text(
                                        text = if (liveBlur.trailSamples.isNotEmpty()) {
                                            "ACTIVE ${(liveBlur.speedNormalized * 100f).roundToInt()}%"
                                        } else {
                                            "READY"
                                        },
                                        style = StudioMonoStyle,
                                        color = CyberCyan
                                    )
                                }
                            }
                            Text(
                                text = "Velocity-based shutter blur between keyframes",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = clip.motionBlurEnabled,
                            onCheckedChange = onToggleMotionBlur,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VoidBlack,
                                checkedTrackColor = CyberCyan,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = ObsidianBg
                            ),
                            modifier = Modifier.testTag("motion_blur_toggle")
                        )
                    }

                    if (clip.motionBlurEnabled) {
                        TransformSliderRow(
                            label = "Shutter Blur Strength",
                            valueText = "${(clip.motionBlurIntensity * 100f).roundToInt()}%",
                            value = clip.motionBlurIntensity,
                            valueRange = 0.1f..1f,
                            accentColor = CyberCyan,
                            testTag = "motion_blur_intensity_slider",
                            onValueChange = onMotionBlurIntensityChange
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Subtle 35%" to 0.35f,
                                "Anime 65%" to 0.65f,
                                "Turbo 100%" to 1.0f
                            ).forEach { (label, presetVal) ->
                                val selectedPreset = kotlin.math.abs(clip.motionBlurIntensity - presetVal) < 0.06f
                                Surface(
                                    onClick = { onMotionBlurIntensityChange(presetVal) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedPreset) CyberCyan.copy(alpha = 0.2f) else ObsidianBg,
                                    border = BorderStroke(1.dp, if (selectedPreset) CyberCyan else StudioBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 36.dp)
                                        .testTag("motion_blur_preset_${(presetVal * 100).roundToInt()}")
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selectedPreset) CyberCyan else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        if (keyframes.size < 2) {
                            Text(
                                text = "Add 2+ keyframes to animate directional blur across transitions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyframeTimelineStrip(
    trimStartMs: Long,
    trimEndMs: Long,
    playheadMs: Long,
    keyframes: List<OverlayKeyframe>,
    activeKeyframeId: String?,
    onSeekMs: (Long) -> Unit
) {
    val span = (trimEndMs - trimStartMs).coerceAtLeast(100L).toFloat()
    val playFrac = ((playheadMs - trimStartMs).toFloat() / span).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(StudioSurfaceElevated, RoundedCornerShape(10.dp))
            .pointerInput(trimStartMs, trimEndMs) {
                detectTapGestures { offset ->
                    val pad = 16f
                    val usable = (size.width - pad * 2f).coerceAtLeast(1f)
                    val frac = ((offset.x - pad) / usable).coerceIn(0f, 1f)
                    val targetMs = trimStartMs + (frac * span).toLong()
                    onSeekMs(targetMs)
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            val pad = 16.dp.toPx()
            val usableW = (size.width - pad * 2f).coerceAtLeast(1f)
            val centerY = size.height / 2f

            // Base rail
            drawLine(
                color = StudioBorder,
                start = Offset(pad, centerY),
                end = Offset(pad + usableW, centerY),
                strokeWidth = 3.dp.toPx()
            )

            // Active span between first & last keyframe
            if (keyframes.size >= 2) {
                val firstFrac = ((keyframes.first().timeMs - trimStartMs).toFloat() / span).coerceIn(0f, 1f)
                val lastFrac = ((keyframes.last().timeMs - trimStartMs).toFloat() / span).coerceIn(0f, 1f)
                drawLine(
                    color = NeonPurple.copy(alpha = 0.7f),
                    start = Offset(pad + firstFrac * usableW, centerY),
                    end = Offset(pad + lastFrac * usableW, centerY),
                    strokeWidth = 4.dp.toPx()
                )
            }

            // Keyframe diamond markers
            val radius = 6.dp.toPx()
            keyframes.forEach { kf ->
                val frac = ((kf.timeMs - trimStartMs).toFloat() / span).coerceIn(0f, 1f)
                val cx = pad + frac * usableW
                val isSelected = kf.id == activeKeyframeId
                val diamond = Path().apply {
                    moveTo(cx, centerY - radius)
                    lineTo(cx + radius, centerY)
                    lineTo(cx, centerY + radius)
                    lineTo(cx - radius, centerY)
                    close()
                }
                drawPath(
                    path = diamond,
                    color = if (isSelected) CyberCyan else NeonPurpleBright
                )
                if (isSelected) {
                    drawCircle(
                        color = CyberCyan.copy(alpha = 0.35f),
                        radius = radius * 1.7f,
                        center = Offset(cx, centerY),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // Playhead vertical line
            val px = pad + playFrac * usableW
            drawLine(
                color = TextPrimary,
                start = Offset(px, 4.dp.toPx()),
                end = Offset(px, size.height - 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
private fun TransformSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(valueText, style = StudioMonoStyle, color = accentColor)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun DiamondGlyph(
    color: Color,
    filled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rx = size.width / 2f
        val ry = size.height / 2f
        val path = Path().apply {
            moveTo(cx, cy - ry)
            lineTo(cx + rx, cy)
            lineTo(cx, cy + ry)
            lineTo(cx - rx, cy)
            close()
        }
        if (filled) {
            drawPath(path = path, color = color)
        } else {
            drawPath(path = path, color = color, style = Stroke(width = 1.6.dp.toPx()))
        }
    }
}
