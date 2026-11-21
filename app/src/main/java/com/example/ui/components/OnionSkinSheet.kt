package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.OnionSkinConfig
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.OnionAfterCyan
import com.example.ui.theme.OnionBeforeRose
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import kotlin.math.roundToInt

/**
 * Shared Onion Skinning Light-Table Settings Sheet for both Drawing Studio and Animation Studio.
 * Allows toggling master onion skinning, independently toggling Previous and Next frame overlays,
 * configuring frame range (1-3), adjusting opacity, and toggling Rose/Cyan tinting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnionSkinSettingsBottomSheet(
    config: OnionSkinConfig,
    onUpdate: ((OnionSkinConfig) -> OnionSkinConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Onion Skinning Light-Table", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Preview semi-transparent previous & next frames on canvas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { enabled -> onUpdate { it.copy(enabled = enabled) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = VoidBlack, checkedTrackColor = NeonPurple),
                    modifier = Modifier.testTag("onion_master_switch")
                )
            }

            // Previous Frames (Before)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, OnionBeforeRose.copy(alpha = if (config.showBefore) 0.55f else 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Previous Frames (Before)", style = MaterialTheme.typography.titleMedium, color = OnionBeforeRose)
                            Text(
                                text = if (config.showBefore) "${config.beforeCount} frames • ${(config.beforeOpacity * 100).roundToInt()}% opacity" else "Overlay hidden",
                                style = StudioMonoStyle,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = config.showBefore,
                            onCheckedChange = { show ->
                                onUpdate {
                                    it.copy(
                                        showBefore = show,
                                        beforeCount = if (show && it.beforeCount == 0) 1 else it.beforeCount
                                    )
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = VoidBlack, checkedTrackColor = OnionBeforeRose),
                            modifier = Modifier.testTag("onion_prev_switch")
                        )
                    }
                    if (config.showBefore) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..3).forEach { count ->
                                val active = config.beforeCount == count
                                Surface(
                                    onClick = { onUpdate { it.copy(beforeCount = count, showBefore = true) } },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) OnionBeforeRose.copy(alpha = 0.25f) else StudioSurface,
                                    border = BorderStroke(1.dp, if (active) OnionBeforeRose else StudioBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "$count",
                                        style = StudioMonoStyle,
                                        color = if (active) OnionBeforeRose else TextSecondary,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                        Slider(
                            value = config.beforeOpacity,
                            onValueChange = { v -> onUpdate { it.copy(beforeOpacity = v) } },
                            valueRange = 0.10f..0.75f,
                            colors = SliderDefaults.colors(thumbColor = OnionBeforeRose, activeTrackColor = OnionBeforeRose)
                        )
                    }
                }
            }

            // Next Frames (After)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, OnionAfterCyan.copy(alpha = if (config.showAfter) 0.55f else 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Next Frames (After)", style = MaterialTheme.typography.titleMedium, color = OnionAfterCyan)
                            Text(
                                text = if (config.showAfter) "${config.afterCount} frames • ${(config.afterOpacity * 100).roundToInt()}% opacity" else "Overlay hidden",
                                style = StudioMonoStyle,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = config.showAfter,
                            onCheckedChange = { show ->
                                onUpdate {
                                    it.copy(
                                        showAfter = show,
                                        afterCount = if (show && it.afterCount == 0) 1 else it.afterCount
                                    )
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = VoidBlack, checkedTrackColor = OnionAfterCyan),
                            modifier = Modifier.testTag("onion_next_switch")
                        )
                    }
                    if (config.showAfter) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..3).forEach { count ->
                                val active = config.afterCount == count
                                Surface(
                                    onClick = { onUpdate { it.copy(afterCount = count, showAfter = true) } },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) OnionAfterCyan.copy(alpha = 0.25f) else StudioSurface,
                                    border = BorderStroke(1.dp, if (active) OnionAfterCyan else StudioBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "$count",
                                        style = StudioMonoStyle,
                                        color = if (active) OnionAfterCyan else TextSecondary,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                        Slider(
                            value = config.afterOpacity,
                            onValueChange = { v -> onUpdate { it.copy(afterOpacity = v) } },
                            valueRange = 0.10f..0.75f,
                            colors = SliderDefaults.colors(thumbColor = OnionAfterCyan, activeTrackColor = OnionAfterCyan)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Anime Light-Table Color Tint", style = MaterialTheme.typography.titleMedium)
                    Text("Rose for previous frames, Cyan for next frames", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Switch(
                    checked = config.tintEnabled,
                    onCheckedChange = { tint -> onUpdate { it.copy(tintEnabled = tint) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = VoidBlack, checkedTrackColor = CyberCyan)
                )
            }
        }
    }
}
