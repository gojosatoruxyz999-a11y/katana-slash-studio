package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.DefaultNeonPalettes
import com.example.data.local.NeonPalettePreset
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnionBeforeRose
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

fun formatHexColor(argb: Long): String {
    return String.format("#%06X", (argb.toInt() and 0x00FFFFFF))
}

fun parseHexColor(input: String): Long? {
    val clean = input.trim().removePrefix("#")
    return when (clean.length) {
        6 -> clean.toLongOrNull(16)?.let { 0xFF000000L or it }
        8 -> clean.toLongOrNull(16)?.let { 0xFF000000L or (it and 0x00FFFFFFL) }
        else -> null
    }
}

private fun hsvToOpaqueArgb(hue: Float, sat: Float, value: Float): Long {
    val intColor = AndroidColor.HSVToColor(
        floatArrayOf(
            hue.coerceIn(0f, 360f),
            sat.coerceIn(0f, 1f),
            value.coerceIn(0f, 1f)
        )
    )
    return 0xFF000000L or (intColor.toLong() and 0x00FFFFFFL)
}

private fun argbToHsvTriple(argb: Long): Triple<Float, Float, Float> {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV((0xFF000000L or (argb and 0x00FFFFFFL)).toInt(), hsv)
    return Triple(hsv[0], hsv[1], hsv[2])
}

@Composable
fun NeonPalettePresetBar(
    selectedColorArgb: Long,
    favoriteColors: List<Long>,
    presets: List<NeonPalettePreset>,
    activePresetId: String,
    onSelectColor: (Long) -> Unit,
    onSelectPreset: (String) -> Unit,
    onToggleSaveFavorite: (Long) -> Unit,
    onOpenColorPicker: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val activePreset = presets.firstOrNull { it.id == activePresetId }
        ?: presets.firstOrNull()
        ?: NeonPalettePreset(
            id = DefaultNeonPalettes.PRESET_FAVORITES,
            name = "Saved Neon",
            colors = favoriteColors,
            isBuiltIn = true
        )

    val normalizedSelected = 0xFF000000L or (selectedColorArgb and 0x00FFFFFFL)
    val isFavorite = favoriteColors.any { (0xFF000000L or (it and 0x00FFFFFFL)) == normalizedSelected }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Palette Preset Selector Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .testTag("palette_preset_chips_row")
            ) {
                items(presets, key = { it.id }) { preset ->
                    val selected = preset.id == activePreset.id
                    Surface(
                        onClick = { onSelectPreset(preset.id) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) NeonPurple.copy(alpha = 0.22f) else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (selected) NeonPurpleBright else StudioBorder),
                        modifier = Modifier.testTag("preset_chip_${preset.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // Mini dual-dot preview of the preset's lead neon hues
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                preset.colors.take(3).forEach { c ->
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(c.toInt()))
                                    )
                                }
                            }
                            Text(
                                text = preset.name,
                                style = StudioMonoStyle,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) TextPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(8.dp))
                trailingContent()
            }
        }

        // Swatches + Custom Color Picker Trigger + Quick Save Favorite Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Custom Color Picker button with spectrum ring
            Surface(
                onClick = onOpenColorPicker,
                shape = CircleShape,
                color = StudioSurfaceElevated,
                border = BorderStroke(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            NeonPurpleBright,
                            CyberCyan,
                            Color(0xFF10B981),
                            Color(0xFFF59E0B),
                            Color(0xFFF43F5E),
                            NeonPurpleBright
                        )
                    )
                ),
                modifier = Modifier
                    .size(34.dp)
                    .testTag("open_color_picker_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(normalizedSelected.toInt()))
                            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = "Open custom color picker",
                        tint = VoidBlack.copy(alpha = 0.7f),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            // Active Preset Swatches
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .testTag("palette_swatches_row")
            ) {
                itemsIndexed(activePreset.colors) { index, argb ->
                    val normalizedSwatch = 0xFF000000L or (argb and 0x00FFFFFFL)
                    val selected = normalizedSwatch == normalizedSelected
                    Box(
                        modifier = Modifier
                            .size(if (selected) 30.dp else 25.dp)
                            .clip(CircleShape)
                            .background(Color(normalizedSwatch.toInt()))
                            .clickable { onSelectColor(normalizedSwatch) }
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, NeonPurpleBright, CircleShape)
                                } else {
                                    Modifier.border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                }
                            )
                            .testTag("palette_swatch_$index")
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (normalizedSwatch == 0xFFF8FAFC) VoidBlack else Color.White
                                    )
                            )
                        }
                    }
                }
            }

            // Quick Save / Saved Favorite Color Button
            Surface(
                onClick = { onToggleSaveFavorite(normalizedSelected) },
                shape = RoundedCornerShape(9.dp),
                color = if (isFavorite) CyberCyan.copy(alpha = 0.16f) else StudioSurfaceElevated,
                border = BorderStroke(1.dp, if (isFavorite) CyberCyan else StudioBorder),
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .testTag("toggle_save_favorite_color_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                        contentDescription = if (isFavorite) "Saved in Neon Favorites" else "Save color to Neon Favorites",
                        tint = if (isFavorite) CyberCyan else NeonPurpleBright,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isFavorite) "Saved" else "Save",
                        style = StudioMonoStyle,
                        color = if (isFavorite) CyberCyan else TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun NeonColorPickerDialog(
    initialColorArgb: Long,
    favoriteColors: List<Long>,
    presets: List<NeonPalettePreset>,
    activePresetId: String,
    onSelectPreset: (String) -> Unit,
    onSaveFavoriteColor: (Long) -> Unit,
    onRemoveFavoriteColor: (Long) -> Unit,
    onSaveCustomPreset: (String, List<Long>) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onApplyColor: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val startHsv = remember(initialColorArgb) { argbToHsvTriple(initialColorArgb) }
    var hue by remember { mutableFloatStateOf(startHsv.first) }
    var saturation by remember { mutableFloatStateOf(startHsv.second) }
    var brightness by remember { mutableFloatStateOf(startHsv.third) }

    val draftColorArgb = remember(hue, saturation, brightness) {
        hsvToOpaqueArgb(hue, saturation, brightness)
    }
    var hexInput by remember(draftColorArgb) { mutableStateOf(formatHexColor(draftColorArgb)) }
    var hexError by remember { mutableStateOf(false) }

    var showNewPresetForm by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    var svBoxSize by remember { mutableStateOf(IntSize(1, 1)) }
    var hueBarSize by remember { mutableStateOf(IntSize(1, 1)) }

    val isAlreadyFavorite = favoriteColors.any {
        (0xFF000000L or (it and 0x00FFFFFFL)) == draftColorArgb
    }

    fun applyPickedRgb(argb: Long) {
        val (h, s, v) = argbToHsvTriple(argb)
        hue = h
        saturation = s
        brightness = v
        hexError = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 540.dp)
                .testTag("neon_color_picker_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = StudioSurface,
            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header + Color Preview Comparison
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Neon Color Studio",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Customize hue & save favorite neon presets",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("color_picker_close_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close color picker", tint = TextSecondary)
                    }
                }

                // Current vs New Preview Pill + Editable Hex Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianBg,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                                    .background(Color(initialColorArgb.toInt()))
                            )
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                                    .background(Color(draftColorArgb.toInt()))
                            )
                        }
                    }

                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            hexInput = raw.uppercase().take(9)
                            val parsed = parseHexColor(raw)
                            if (parsed != null) {
                                val (h, s, v) = argbToHsvTriple(parsed)
                                hue = h
                                saturation = s
                                brightness = v
                                hexError = false
                            } else {
                                hexError = true
                            }
                        },
                        isError = hexError,
                        singleLine = true,
                        label = { Text("Hex Color") },
                        textStyle = StudioMonoStyle,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("color_picker_hex_input")
                    )
                }

                // 2D Saturation x Value Gradient Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                        .onSizeChanged { svBoxSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                val w = svBoxSize.width.coerceAtLeast(1).toFloat()
                                val h = svBoxSize.height.coerceAtLeast(1).toFloat()
                                saturation = (pos.x / w).coerceIn(0f, 1f)
                                brightness = (1f - (pos.y / h)).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val w = svBoxSize.width.coerceAtLeast(1).toFloat()
                                val h = svBoxSize.height.coerceAtLeast(1).toFloat()
                                saturation = (change.position.x / w).coerceIn(0f, 1f)
                                brightness = (1f - (change.position.y / h)).coerceIn(0f, 1f)
                            }
                        }
                        .testTag("color_picker_sv_box")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pureHueColor = Color(hsvToOpaqueArgb(hue, 1f, 1f).toInt())
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.White, pureHueColor)
                            )
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black)
                            )
                        )
                        val cx = saturation * size.width
                        val cy = (1f - brightness) * size.height
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.65f),
                            radius = 12.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 10.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Hue Spectrum Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                        .onSizeChanged { hueBarSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                val w = hueBarSize.width.coerceAtLeast(1).toFloat()
                                hue = ((pos.x / w).coerceIn(0f, 1f) * 360f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val w = hueBarSize.width.coerceAtLeast(1).toFloat()
                                hue = ((change.position.x / w).coerceIn(0f, 1f) * 360f)
                            }
                        }
                        .testTag("color_picker_hue_bar")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val spectrum = listOf(
                            Color(0xFFFF0000),
                            Color(0xFFFFFF00),
                            Color(0xFF00FF00),
                            Color(0xFF00FFFF),
                            Color(0xFF0000FF),
                            Color(0xFFFF00FF),
                            Color(0xFFFF0000)
                        )
                        drawRoundRect(
                            brush = Brush.horizontalGradient(spectrum),
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        )
                        val tx = (hue / 360f).coerceIn(0f, 1f) * size.width
                        drawCircle(
                            color = VoidBlack,
                            radius = 11.dp.toPx(),
                            center = Offset(tx, size.height * 0.5f),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 9.dp.toPx(),
                            center = Offset(tx, size.height * 0.5f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Saved Neon Favorites Section
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianBg,
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Saved Neon Favorites (${favoriteColors.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = onResetDefaults,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.heightIn(min = 34.dp)
                                ) {
                                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = TextMuted, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reset", style = StudioMonoStyle, color = TextMuted)
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(favoriteColors) { favArgb ->
                                val normFav = 0xFF000000L or (favArgb and 0x00FFFFFFL)
                                val selected = normFav == draftColorArgb
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("saved_favorite_swatch_${formatHexColor(normFav)}")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .align(Alignment.Center)
                                            .clip(CircleShape)
                                            .background(Color(normFav.toInt()))
                                            .clickable { applyPickedRgb(normFav) }
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) CyberCyan else Color.White.copy(alpha = 0.25f),
                                                shape = CircleShape
                                            )
                                    )
                                    if (favoriteColors.size > 1) {
                                        Surface(
                                            onClick = { onRemoveFavoriteColor(normFav) },
                                            shape = CircleShape,
                                            color = VoidBlack.copy(alpha = 0.9f),
                                            border = BorderStroke(1.dp, OnionBeforeRose.copy(alpha = 0.7f)),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .align(Alignment.TopEnd)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    contentDescription = "Remove saved color ${formatHexColor(normFav)}",
                                                    tint = OnionBeforeRose,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Preset Packs Switcher + Save Custom Preset Mix
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Palette Presets",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary
                            )
                            TextButton(
                                onClick = { showNewPresetForm = !showNewPresetForm },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("new_palette_preset_toggle")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save Mix", style = StudioMonoStyle, color = CyberCyan)
                            }
                        }

                        AnimatedVisibility(visible = showNewPresetForm) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newPresetName,
                                    onValueChange = { newPresetName = it.take(24) },
                                    singleLine = true,
                                    label = { Text("Preset Name") },
                                    placeholder = { Text("Cyber Katana") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("new_preset_name_input")
                                )
                                Button(
                                    onClick = {
                                        val colorsToSave = (listOf(draftColorArgb) + favoriteColors).distinct().take(8)
                                        onSaveCustomPreset(newPresetName.ifBlank { "Neon Mix" }, colorsToSave)
                                        newPresetName = ""
                                        showNewPresetForm = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = VoidBlack),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("confirm_save_preset_button")
                                ) {
                                    Text("Save", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(presets, key = { it.id }) { preset ->
                                val isActive = preset.id == activePresetId
                                Surface(
                                    onClick = {
                                        onSelectPreset(preset.id)
                                        preset.colors.firstOrNull()?.let(::applyPickedRgb)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isActive) NeonPurple.copy(alpha = 0.24f) else StudioSurfaceElevated,
                                    border = BorderStroke(1.dp, if (isActive) NeonPurpleBright else StudioBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = preset.name,
                                            style = StudioMonoStyle,
                                            color = if (isActive) TextPrimary else TextSecondary
                                        )
                                        if (!preset.isBuiltIn) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete preset ${preset.name}",
                                                tint = OnionBeforeRose,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { onDeleteCustomPreset(preset.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSaveFavoriteColor(draftColorArgb) },
                        enabled = !isAlreadyFavorite,
                        border = BorderStroke(1.dp, if (isAlreadyFavorite) StudioBorder else CyberCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("color_picker_save_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isAlreadyFavorite) Icons.Rounded.Check else Icons.Rounded.BookmarkAdd,
                            contentDescription = null,
                            tint = if (isAlreadyFavorite) TextMuted else CyberCyan,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isAlreadyFavorite) "Saved" else "Save Swatch",
                            color = if (isAlreadyFavorite) TextMuted else CyberCyan
                        )
                    }

                    Button(
                        onClick = {
                            onApplyColor(draftColorArgb)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = TextPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("color_picker_apply_button")
                    ) {
                        Text("Apply Color", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
