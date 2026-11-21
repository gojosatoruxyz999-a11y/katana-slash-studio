package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ProjectType
import com.example.data.model.StudioProject
import com.example.ui.components.FeatureMaturity
import com.example.ui.components.FrameThumbnailCanvas
import com.example.ui.components.StudioStatusBadge
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepViolet
import com.example.ui.theme.EmeraldLive
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

private data class StudioModuleSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val maturity: FeatureMaturity,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenAnimation: (String?) -> Unit,
    onOpenDrawing: (String?) -> Unit,
    onOpenVideo: (String?) -> Unit,
    onOpenSound: (String?) -> Unit,
    onOpenProjects: () -> Unit,
    onOpenExport: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    val latestAnimProject = remember(state.projects) {
        state.projects.firstOrNull { it.type == ProjectType.ANIMATION }
    }

    val modules = listOf(
        StudioModuleSpec(
            title = "Animation Studio",
            subtitle = "Frames, Onion Skin, Layers, Timeline & FPS",
            icon = Icons.Rounded.Animation,
            accent = NeonPurple,
            maturity = FeatureMaturity.LIVE,
            testTag = "module_animation",
            onClick = { onOpenAnimation(latestAnimProject?.id) }
        ),
        StudioModuleSpec(
            title = "Drawing Studio",
            subtitle = "Brushes, Shapes, Colors & Layer Stack",
            icon = Icons.Rounded.Brush,
            accent = CyberCyan,
            maturity = FeatureMaturity.LIVE,
            testTag = "module_drawing",
            onClick = {
                val drawProj = state.projects.firstOrNull { it.type == ProjectType.DRAWING }
                onOpenDrawing(drawProj?.id)
            }
        ),
        StudioModuleSpec(
            title = "Video Editor",
            subtitle = "Import, Trim, Cut, Split, FX & Text",
            icon = Icons.Rounded.MovieCreation,
            accent = NeonPurpleBright,
            maturity = FeatureMaturity.INCREMENTAL,
            testTag = "module_video",
            onClick = {
                val vidProj = state.projects.firstOrNull { it.type == ProjectType.VIDEO }
                onOpenVideo(vidProj?.id)
            }
        ),
        StudioModuleSpec(
            title = "Sound Studio",
            subtitle = "Voice Mic, Anime SFX Pad & Trim",
            icon = Icons.Rounded.GraphicEq,
            accent = OnionBeforeRose,
            maturity = FeatureMaturity.INCREMENTAL,
            testTag = "module_sound",
            onClick = {
                val sndProj = state.projects.firstOrNull { it.type == ProjectType.SOUND }
                onOpenSound(sndProj?.id)
            }
        ),
        StudioModuleSpec(
            title = "Project Manager",
            subtitle = "Save, Reopen, Duplicate & Organize",
            icon = Icons.Rounded.FolderOpen,
            accent = EmeraldLive,
            maturity = FeatureMaturity.LIVE,
            testTag = "module_projects",
            onClick = onOpenProjects
        ),
        StudioModuleSpec(
            title = "Export Engine",
            subtitle = "Animated GIF89a & H.264 MP4 Output",
            icon = Icons.Rounded.Outbox,
            accent = CyberCyan,
            maturity = FeatureMaturity.LIVE,
            testTag = "module_export",
            onClick = { onOpenExport(latestAnimProject?.id ?: state.projects.firstOrNull()?.id) }
        )
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val isWide = maxWidth >= 700.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Studio Brand Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(NeonPurple, DeepViolet)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ronin_icon_fg_1795228059826),
                                contentDescription = "RONIN STUDIO Emblem",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column {
                            Text(
                                text = "RONIN STUDIO",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ANIME CREATOR WORKSPACE",
                                style = StudioMonoStyle,
                                color = NeonPurpleBright
                            )
                        }
                    }

                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("dashboard_new_project_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Cut")
                    }
                }
            }

            // Hero Studio Banner
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.45f)),
                    color = StudioSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_ronin_hero_1795228059817),
                            contentDescription = "RONIN STUDIO Hero Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            VoidBlack.copy(alpha = 0.94f),
                                            VoidBlack.copy(alpha = 0.72f),
                                            VoidBlack.copy(alpha = 0.35f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StudioStatusBadge(maturity = FeatureMaturity.OPTIMIZED)
                                Text(
                                    text = "${state.memoryInfo.availableMb}MB FREE RAM",
                                    style = StudioMonoStyle,
                                    color = CyberCyan
                                )
                            }

                            Column {
                                Text(
                                    text = "Frame-by-Frame Cel Engine",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Onion skinning, layered ink, timeline FPS & native GIF/MP4 export.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 2
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { onOpenAnimation(latestAnimProject?.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("hero_launch_animation_button")
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Animate Now")
                                    }
                                    OutlinedButton(
                                        onClick = { showCreateDialog = true },
                                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Blank Canvas", color = CyberCyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Projects Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Projects",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    TextButton(
                        onClick = onOpenProjects,
                        modifier = Modifier.testTag("view_all_projects_button")
                    ) {
                        Text("Manage All (${state.projects.size})", color = NeonPurpleBright)
                    }
                }
            }

            item {
                if (state.projects.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = StudioSurface,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = NeonPurple)
                            Text(
                                text = "No saved cuts yet. Tap + New Cut to start your first anime scene.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.projects, key = { it.id }) { project ->
                            RecentProjectCard(
                                project = project,
                                onClick = {
                                    when (project.type) {
                                        ProjectType.ANIMATION -> onOpenAnimation(project.id)
                                        ProjectType.DRAWING -> onOpenDrawing(project.id)
                                        ProjectType.VIDEO -> onOpenVideo(project.id)
                                        ProjectType.SOUND -> onOpenSound(project.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Studio Modules Grid
            item {
                Column {
                    Text(
                        text = "Studio Workspaces",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Incremental native modules — LIVE tools are ready; INCREMENTAL modules note queued upgrades.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            val rows = modules.chunked(if (isWide) 3 else 2)
            items(rows) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { mod ->
                        StudioModuleCard(
                            spec = mod,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size < (if (isWide) 3 else 2)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // 4GB RAM Vector Engine Telemetry Footer
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = CyberCyan
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "4GB RAM Low-Memory Vector Architecture",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Frames stay as lightweight vector strokes in Room DB (${state.memoryInfo.availableMb}MB / ${state.memoryInfo.totalMb}MB RAM free). Single-bitmap reuse during export prevents OOM.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, type, fps ->
                showCreateDialog = false
                viewModel.createProject(title, type, fps) { created ->
                    when (created.type) {
                        ProjectType.ANIMATION -> onOpenAnimation(created.id)
                        ProjectType.DRAWING -> onOpenDrawing(created.id)
                        ProjectType.VIDEO -> onOpenVideo(created.id)
                        ProjectType.SOUND -> onOpenSound(created.id)
                    }
                }
            }
        )
    }
}

@Composable
private fun StudioModuleCard(
    spec: StudioModuleSpec,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = spec.onClick,
        shape = RoundedCornerShape(18.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = modifier
            .height(152.dp)
            .testTag(spec.testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(spec.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = spec.title,
                        tint = spec.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                StudioStatusBadge(maturity = spec.maturity)
            }

            Column {
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = spec.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentProjectCard(
    project: StudioProject,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier
            .width(196.dp)
            .testTag("recent_project_${project.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VoidBlack)
            ) {
                val previewFrame = project.frames.firstOrNull()
                if (previewFrame != null) {
                    FrameThumbnailCanvas(
                        frame = previewFrame,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (project.type) {
                                ProjectType.VIDEO -> Icons.Rounded.MovieCreation
                                ProjectType.SOUND -> Icons.Rounded.GraphicEq
                                ProjectType.DRAWING -> Icons.Rounded.Brush
                                ProjectType.ANIMATION -> Icons.Rounded.Animation
                            },
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VoidBlack.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = project.type.label.uppercase(),
                        style = StudioMonoStyle,
                        color = CyberCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = project.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = when (project.type) {
                ProjectType.ANIMATION -> "${project.frames.size} frames • ${project.fps} FPS"
                ProjectType.DRAWING -> "${project.frames.firstOrNull()?.layers?.size ?: 1} layers"
                ProjectType.VIDEO -> "${project.videoClips.size} video clips"
                ProjectType.SOUND -> "${project.audioClips.size} audio clips"
            }
            Text(
                text = meta,
                style = StudioMonoStyle,
                color = TextMuted
            )
        }
    }
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, type: ProjectType, fps: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProjectType.ANIMATION) }
    var selectedFps by remember { mutableIntStateOf(12) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        title = {
            Text("Create New Studio Project", color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Project Title") },
                    placeholder = { Text("e.g., Cyber Ronin Ep.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Studio Mode", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProjectType.entries.forEach { type ->
                        val active = type == selectedType
                        Surface(
                            onClick = { selectedType = type },
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) NeonPurple.copy(alpha = 0.22f) else StudioSurface,
                            border = BorderStroke(1.dp, if (active) NeonPurple else StudioBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) TextPrimary else TextSecondary,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                if (selectedType == ProjectType.ANIMATION) {
                    Text("Frame Rate (FPS)", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(6, 12, 18, 24).forEach { fps ->
                            val active = fps == selectedFps
                            Surface(
                                onClick = { selectedFps = fps },
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) CyberCyan.copy(alpha = 0.2f) else StudioSurface,
                                border = BorderStroke(1.dp, if (active) CyberCyan else StudioBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${fps}fps",
                                    style = StudioMonoStyle,
                                    color = if (active) CyberCyan else TextSecondary,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        title.ifBlank { "Ronin ${selectedType.label}" },
                        selectedType,
                        selectedFps
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
            ) {
                Text("Create & Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
