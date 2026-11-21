package com.example.ui.animation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import java.io.File

@Composable
fun StudioExportSaveModal(
    projectTitle: String,
    frameCount: Int,
    modalState: StudioExportModalState,
    onDismiss: () -> Unit,
    onBuildProjectJson: () -> String?,
    onSaveProjectLocally: () -> Unit,
    onSavedToUri: (String) -> Unit,
    onLoadProjectJson: (String) -> Boolean,
    onToggleTransparentPng: (Boolean) -> Unit,
    onExportPng: () -> Unit,
    onSelectFormat: (AnimationExportFormat) -> Unit,
    onSelectExportFps: (Int) -> Unit,
    onToggleOutro: (Boolean) -> Unit,
    onOutroTextChange: (String) -> Unit,
    onExportAnimation: () -> Unit,
    onOpenFullExportHub: () -> Unit
) {
    val context = LocalContext.current

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = onBuildProjectJson() ?: return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                onSavedToUri("${projectTitle}.ronin.json")
            }
        }
    }

    val loadJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull()
            if (!json.isNullOrBlank()) {
                onLoadProjectJson(json)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 580.dp)
                .testTag("studio_export_save_modal"),
            shape = RoundedCornerShape(22.dp),
            color = StudioSurface,
            border = BorderStroke(1.dp, StudioBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Export & Save Studio",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$projectTitle • $frameCount frame(s)",
                            style = StudioMonoStyle,
                            color = CyberCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("modal_export_close")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close modal", tint = TextSecondary)
                    }
                }

                // 1. Project (.json) Save & Load
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianBg,
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Project File (.json)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onSaveProjectLocally()
                                    val clean = projectTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "katana_cut" }
                                    saveJsonLauncher.launch("${clean}.ronin.json")
                                },
                                border = BorderStroke(1.dp, NeonPurple),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("modal_save_json_button")
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, tint = NeonPurpleBright, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save .json", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = { loadJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                                border = BorderStroke(1.dp, CyberCyan),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("modal_load_json_button")
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Load .json", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // 2. High-Res Current Frame PNG
                Surface(
                    shape = RoundedCornerShape(16.dp),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Current Frame PNG (1920x1200)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Transparent Background",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = modalState.transparentPngBackground,
                                onCheckedChange = onToggleTransparentPng,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = VoidBlack,
                                    checkedTrackColor = CyberCyan
                                ),
                                modifier = Modifier.testTag("modal_png_transparent_toggle")
                            )
                        }

                        OutlinedButton(
                            onClick = onExportPng,
                            enabled = !modalState.isExporting,
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("modal_export_png_button")
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Export Current Frame PNG", color = CyberCyan)
                        }
                    }
                }

                // 3. Full Animation Video / GIF + FPS + Optional 1s Outro End-Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianBg,
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Full Animation Export",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )

                        // Format Selector (MP4 Video / Animated GIF)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimationExportFormat.entries.forEach { fmt ->
                                val active = modalState.selectedFormat == fmt
                                Surface(
                                    onClick = { onSelectFormat(fmt) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (active) NeonPurple.copy(alpha = 0.22f) else StudioSurfaceElevated,
                                    border = BorderStroke(1.dp, if (active) NeonPurpleBright else StudioBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("modal_export_format_${fmt.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (fmt == AnimationExportFormat.MP4) Icons.Rounded.Movie else Icons.Rounded.Gif,
                                            contentDescription = null,
                                            tint = if (active) NeonPurpleBright else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = fmt.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (active) TextPrimary else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // FPS Selector (8 / 12 / 24 FPS)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Export FPS:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                            listOf(8, 12, 24).forEach { fpsOpt ->
                                val active = modalState.exportFps == fpsOpt
                                Surface(
                                    onClick = { onSelectExportFps(fpsOpt) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (active) CyberCyan.copy(alpha = 0.18f) else StudioSurfaceElevated,
                                    border = BorderStroke(1.dp, if (active) CyberCyan else StudioBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                        .testTag("modal_fps_$fpsOpt")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${fpsOpt} FPS",
                                            style = StudioMonoStyle,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (active) CyberCyan else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = StudioBorder.copy(alpha = 0.5f))

                        // Include 1s Studio Outro Toggle + Editable Creator Text
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Include 1s Studio Outro",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Appends dark katana slash end-card",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = modalState.includeOutro,
                                onCheckedChange = onToggleOutro,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = VoidBlack,
                                    checkedTrackColor = NeonPurpleBright
                                ),
                                modifier = Modifier.testTag("modal_outro_toggle")
                            )
                        }

                        AnimatedVisibility(visible = modalState.includeOutro) {
                            OutlinedTextField(
                                value = modalState.outroCreatorText,
                                onValueChange = onOutroTextChange,
                                singleLine = true,
                                label = { Text("Outro Creator Text") },
                                placeholder = { Text("Made with Katana Slash Canvas") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("modal_outro_text_input")
                            )
                        }

                        if (modalState.isExporting) {
                            LinearProgressIndicator(
                                progress = { modalState.exportProgress.coerceIn(0f, 1f) },
                                color = CyberCyan,
                                trackColor = StudioSurfaceHighlight,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        Button(
                            onClick = onExportAnimation,
                            enabled = !modalState.isExporting && frameCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonPurple,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("modal_export_animation_button")
                        ) {
                            if (modalState.isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = TextPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Rendering...")
                            } else {
                                Icon(Icons.Rounded.Outbox, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Export ${modalState.selectedFormat.label} (${modalState.exportFps} FPS)")
                            }
                        }
                    }
                }

                // Export Status + Quick Share
                if (!modalState.modalStatusText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioSurfaceElevated,
                        border = BorderStroke(1.dp, EmeraldLive.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = modalState.modalStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldLive,
                                modifier = Modifier.weight(1f)
                            )
                            val lastFile: File? = modalState.lastExportedFile
                            if (lastFile != null && lastFile.exists()) {
                                OutlinedButton(
                                    onClick = {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            lastFile
                                        )
                                        val mime = when (lastFile.extension.lowercase()) {
                                            "gif" -> "image/gif"
                                            "mp4" -> "video/mp4"
                                            "png" -> "image/png"
                                            else -> "application/json"
                                        }
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = mime
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share ${lastFile.name}"))
                                    },
                                    border = BorderStroke(1.dp, CyberCyan),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.heightIn(min = 40.dp)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Share export", tint = CyberCyan, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share", color = CyberCyan, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onOpenFullExportHub()
                        }
                    ) {
                        Text("Open Full Export Hub", color = TextMuted)
                    }
                }
            }
        }
    }
}
