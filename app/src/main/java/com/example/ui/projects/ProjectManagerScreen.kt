package com.example.ui.projects

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ProjectType
import com.example.data.model.StudioProject
import com.example.ui.components.FrameThumbnailCanvas
import com.example.ui.components.StudioTopHeader
import com.example.ui.dashboard.CreateProjectDialog
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
import com.example.ui.theme.VoidBlack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectManagerScreen(
    viewModel: ProjectsViewModel,
    onBack: () -> Unit,
    onOpenProject: (StudioProject) -> Unit,
    onExportProject: (StudioProject) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<StudioProject?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<StudioProject?>(null) }

    val dateFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        StudioTopHeader(
            title = "Project Manager",
            subtitle = "${state.projects.size} saved locally in Room DB",
            onBack = onBack,
            actions = {
                OutlinedButton(
                    onClick = { viewModel.openBatchDialog(initialTab = 0) },
                    enabled = state.projects.isNotEmpty() || state.currentUser != null,
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.65f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("projects_batch_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Checklist,
                        contentDescription = "Batch export or cloud backup",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Batch", style = MaterialTheme.typography.labelMedium, color = CyberCyan)
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { showCreate = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("projects_new_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New", style = MaterialTheme.typography.labelMedium)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Search projects...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val active = state.selectedFilter == null
                    Surface(
                        onClick = { viewModel.setFilter(null) },
                        shape = RoundedCornerShape(50),
                        color = if (active) NeonPurple.copy(alpha = 0.24f) else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (active) NeonPurple else StudioBorder)
                    ) {
                        Text(
                            text = "All",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) TextPrimary else TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
                items(ProjectType.entries) { type ->
                    val active = state.selectedFilter == type
                    Surface(
                        onClick = { viewModel.setFilter(type) },
                        shape = RoundedCornerShape(50),
                        color = if (active) NeonPurple.copy(alpha = 0.24f) else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (active) NeonPurple else StudioBorder)
                    ) {
                        Text(
                            text = type.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) TextPrimary else TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Surface(
                onClick = { viewModel.openBatchDialog(initialTab = 0) },
                shape = RoundedCornerShape(14.dp),
                color = StudioSurface,
                border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("projects_batch_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Outbox,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Multi-Project Batch & Cloud Backup",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Select multiple projects to export GIF/MP4/JSON or sync to cloud",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        onClick = { viewModel.openBatchDialog(initialTab = 1) },
                        shape = RoundedCornerShape(10.dp),
                        color = NeonPurple.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, NeonPurple),
                        modifier = Modifier.testTag("projects_cloud_quick_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudUpload,
                                contentDescription = "Open cloud backup",
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Cloud",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        if (state.projects.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = TextMuted, modifier = Modifier.size(42.dp))
                    Text("No matching projects found", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.projects, key = { it.id }) { project ->
                    Surface(
                        onClick = { onOpenProject(project) },
                        shape = RoundedCornerShape(16.dp),
                        color = StudioSurface,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("project_row_${project.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp, 54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VoidBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                val thumb = project.frames.firstOrNull()
                                if (thumb != null) {
                                    FrameThumbnailCanvas(frame = thumb, modifier = Modifier.fillMaxSize())
                                } else {
                                    Icon(
                                        imageVector = when (project.type) {
                                            ProjectType.VIDEO -> Icons.Rounded.MovieCreation
                                            ProjectType.SOUND -> Icons.Rounded.GraphicEq
                                            ProjectType.DRAWING -> Icons.Rounded.Brush
                                            ProjectType.ANIMATION -> Icons.Rounded.Animation
                                        },
                                        contentDescription = null,
                                        tint = NeonPurple
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${project.type.label} • ${dateFormatter.format(Date(project.updatedAt))}",
                                    style = StudioMonoStyle,
                                    color = CyberCyan
                                )
                            }

                            if (project.type == ProjectType.ANIMATION || project.type == ProjectType.DRAWING) {
                                IconButton(onClick = { onExportProject(project) }) {
                                    Icon(Icons.Rounded.Outbox, contentDescription = "Export project", tint = CyberCyan)
                                }
                            }
                            IconButton(onClick = {
                                renameTarget = project
                                renameInput = project.title
                            }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Rename project", tint = TextSecondary)
                            }
                            IconButton(onClick = { viewModel.duplicateProject(project.id) }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate project", tint = TextSecondary)
                            }
                            IconButton(onClick = { deleteTarget = project }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete project", tint = OnionBeforeRose)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateProjectDialog(
            onDismiss = { showCreate = false },
            onCreate = { title, type, fps ->
                showCreate = false
                viewModel.createProject(title, type, fps, onOpenProject)
            }
        )
    }

    val rTarget = renameTarget
    if (rTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = StudioSurfaceElevated,
            title = { Text("Rename Project", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Project Title") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameProject(rTarget, renameInput)
                        renameTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    val dTarget = deleteTarget
    if (dTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = StudioSurfaceElevated,
            title = { Text("Delete Project?", color = TextPrimary) },
            text = { Text("Remove \"${dTarget.title}\" from local storage? This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(dTarget.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OnionBeforeRose)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (state.batch.isDialogOpen) {
        BatchActionsDialog(
            projects = state.projects,
            batchState = state.batch,
            currentUser = state.currentUser,
            cloudBackups = state.cloudBackups,
            onDismiss = viewModel::closeBatchDialog,
            onSelectTab = viewModel::setBatchDialogTab,
            onToggleProject = viewModel::toggleProjectSelection,
            onSelectAll = viewModel::selectAllVisibleProjects,
            onClearSelection = viewModel::clearProjectSelection,
            onFormatChange = viewModel::setBatchExportFormat,
            onResolutionChange = viewModel::setBatchResolutionHeight,
            onRunBatchExport = viewModel::runBatchExport,
            onBackupToCloud = viewModel::backupSelectedToCloud,
            onRestoreBackup = viewModel::restoreCloudBackup,
            onDeleteBackup = viewModel::deleteCloudBackup,
            onAuthSuccess = viewModel::notifyAuthStateChanged,
            onAuthError = viewModel::reportCloudError
        )
    }
}
