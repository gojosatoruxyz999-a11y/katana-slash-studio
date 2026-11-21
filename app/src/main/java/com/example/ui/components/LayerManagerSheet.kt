package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.StudioLayer
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.OnionBeforeRose
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Comprehensive Layer Management System shared by Animation Studio and Drawing Studio.
 * Supports creating, deleting, renaming, reordering (up/down), merging down,
 * toggling visibility/lock, adjusting opacity, and syncing layer structure across frames.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerManagerBottomSheet(
    layers: List<StudioLayer>,
    activeLayerIndex: Int,
    showTimelineSyncAction: Boolean,
    onSelectLayer: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onDeleteLayer: (Int) -> Unit,
    onRenameLayer: (Int, String) -> Unit,
    onMoveLayerUp: (Int) -> Unit,
    onMoveLayerDown: (Int) -> Unit,
    onMergeLayerDown: (Int) -> Unit,
    onToggleVisibility: (Int) -> Unit,
    onToggleLock: (Int) -> Unit,
    onChangeOpacity: (Int, Float) -> Unit,
    onSyncLayerStructureAcrossFrames: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var renameTargetIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = NeonPurple
                    )
                    Column {
                        Text(
                            text = "Layer Stack",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "${layers.size} layers • Top renders above bottom",
                            style = StudioMonoStyle,
                            color = TextSecondary
                        )
                    }
                }

                Button(
                    onClick = onAddLayer,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    modifier = Modifier.testTag("add_layer_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Layer")
                }
            }

            if (showTimelineSyncAction && onSyncLayerStructureAcrossFrames != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSyncLayerStructureAcrossFrames,
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_layers_timeline_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sync Layer Names Across All Timeline Frames",
                        color = CyberCyan,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Display layers from top-most (last in list) to bottom-most (first in list)
            val reversedIndices = layers.indices.reversed().toList()
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(reversedIndices, key = { _, idx -> layers[idx].id }) { _, layerIdx ->
                    val layer = layers[layerIdx]
                    val isSelected = layerIdx == activeLayerIndex
                    Surface(
                        onClick = { onSelectLayer(layerIdx) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) NeonPurple.copy(alpha = 0.14f) else StudioSurfaceElevated,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) NeonPurple else StudioBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("layer_card_$layerIdx")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onToggleVisibility(layerIdx) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (layer.isVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = "Toggle layer visibility",
                                        tint = if (layer.isVisible) CyberCyan else TextMuted
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = layer.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (layer.isVisible) TextPrimary else TextMuted
                                    )
                                    Text(
                                        text = "${layer.strokes.size} strokes • ${(layer.opacity * 100).roundToInt()}% opacity",
                                        style = StudioMonoStyle,
                                        color = TextSecondary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        renameTargetIndex = layerIdx
                                        renameText = layer.name
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Rename layer",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onToggleLock(layerIdx) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = if (layer.isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                        contentDescription = "Lock or unlock layer",
                                        tint = if (layer.isLocked) OnionBeforeRose else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Opacity slider & layer ordering / merge / delete actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Opacity",
                                    style = StudioMonoStyle,
                                    color = TextSecondary,
                                    modifier = Modifier.width(58.dp)
                                )
                                Slider(
                                    value = layer.opacity,
                                    onValueChange = { onChangeOpacity(layerIdx, it) },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonPurple,
                                        activeTrackColor = NeonPurple
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Move Up (higher in draw order = index + 1)
                                OutlinedButton(
                                    onClick = { onMoveLayerUp(layerIdx) },
                                    enabled = layerIdx < layers.lastIndex,
                                    border = BorderStroke(1.dp, StudioBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                }
                                // Move Down (lower in draw order = index - 1)
                                OutlinedButton(
                                    onClick = { onMoveLayerDown(layerIdx) },
                                    enabled = layerIdx > 0,
                                    border = BorderStroke(1.dp, StudioBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                }
                                // Merge Down
                                OutlinedButton(
                                    onClick = { onMergeLayerDown(layerIdx) },
                                    enabled = layerIdx > 0,
                                    border = BorderStroke(1.dp, StudioBorder),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Icon(Icons.Rounded.MergeType, contentDescription = "Merge Down", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Merge", style = MaterialTheme.typography.labelMedium)
                                }
                                // Delete
                                OutlinedButton(
                                    onClick = { onDeleteLayer(layerIdx) },
                                    enabled = layers.size > 1,
                                    border = BorderStroke(1.dp, OnionBeforeRose.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete Layer",
                                        tint = if (layers.size > 1) OnionBeforeRose else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val targetIdx = renameTargetIndex
    if (targetIdx != null) {
        AlertDialog(
            onDismissRequest = { renameTargetIndex = null },
            containerColor = StudioSurfaceElevated,
            title = { Text("Rename Layer", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Layer Name") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            onRenameLayer(targetIdx, renameText.trim())
                        }
                        renameTargetIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetIndex = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
