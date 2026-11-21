package com.example.ui.export

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.FeatureMaturity
import com.example.ui.components.StudioStatusBadge
import com.example.ui.components.StudioTopHeader
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.OnionBeforeRose
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import java.io.File

@Composable
fun ExportScreen(
    viewModel: ExportViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StudioTopHeader(
                title = "Export Engine",
                subtitle = "GIF89a & Hardware H.264 MP4",
                onBack = onBack,
                actions = {
                    StudioStatusBadge(maturity = FeatureMaturity.LIVE)
                    Spacer(Modifier.width(8.dp))
                }
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Project Selection
                item {
                    Text("1. Select Animation / Artwork", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    if (state.exportableProjects.isEmpty()) {
                        Text(
                            text = "No Animation or Drawing projects available yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.exportableProjects, key = { it.id }) { proj ->
                                val selected = proj.id == state.selectedProjectId
                                Surface(
                                    onClick = { viewModel.selectProject(proj.id) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) NeonPurple.copy(alpha = 0.24f) else StudioSurfaceElevated,
                                    border = BorderStroke(1.dp, if (selected) NeonPurple else StudioBorder)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(proj.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        Text("${proj.frames.size} frames • ${proj.fps} FPS", style = StudioMonoStyle, color = CyberCyan)
                                    }
                                }
                            }
                        }
                    }
                }

                // Format Selection
                item {
                    Text("2. Output Format", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExportFormat.entries.forEach { fmt ->
                            val selected = fmt == state.selectedFormat
                            Surface(
                                onClick = { viewModel.selectFormat(fmt) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) NeonPurple.copy(alpha = 0.22f) else StudioSurface,
                                border = BorderStroke(1.5.dp, if (selected) NeonPurple else StudioBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_format_${fmt.ext}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (fmt == ExportFormat.GIF) Icons.Rounded.Gif else Icons.Rounded.Movie,
                                        contentDescription = null,
                                        tint = if (selected) NeonPurpleBright else TextSecondary
                                    )
                                    Text(fmt.label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                }
                            }
                        }
                    }
                }

                // Resolution Selection
                item {
                    Text("3. Resolution Preset", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExportResolution.entries.forEach { res ->
                            val selected = res == state.selectedResolution
                            Surface(
                                onClick = { viewModel.selectResolution(res) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) CyberCyan.copy(alpha = 0.16f) else StudioSurface,
                                border = BorderStroke(1.5.dp, if (selected) CyberCyan else StudioBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(res.label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                    Text("${res.width} x ${res.height}", style = StudioMonoStyle, color = CyberCyan)
                                }
                            }
                        }
                    }
                }

                // Render Action Button & Progress
                item {
                    Button(
                        onClick = viewModel::startExport,
                        enabled = !state.isExporting && state.selectedProjectId != null,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_export_button")
                    ) {
                        Icon(Icons.Rounded.Outbox, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.isExporting) "Rendering ${state.selectedFormat.ext.uppercase()}..." else "Render ${state.selectedFormat.ext.uppercase()} Now",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    if (state.isExporting) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            color = CyberCyan,
                            trackColor = StudioSurfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Exported Files History
                item {
                    Text(
                        text = "Exported Files (${state.exportedFiles.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                items(state.exportedFiles, key = { it.absolutePath }) { file ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StudioSurface,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    text = "${file.extension.uppercase()} • ${(file.length() / 1024).coerceAtLeast(1)} KB",
                                    style = StudioMonoStyle,
                                    color = EmeraldLive
                                )
                            }
                            IconButton(onClick = {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val mime = when (file.extension.lowercase()) {
                                    "gif" -> "image/gif"
                                    "mp4" -> "video/mp4"
                                    else -> "image/png"
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
                            }) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share exported file", tint = CyberCyan)
                            }
                            IconButton(onClick = { viewModel.deleteExportedFile(file) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete exported file", tint = OnionBeforeRose)
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.TopCenter))
    }
}
