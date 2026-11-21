package com.example.ui.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Outbox
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.credentials.CredentialManager
import com.example.data.model.ProjectType
import com.example.data.model.StudioProject
import com.example.data.remote.CloudBackupItem
import com.example.export.BatchExportFormat
import com.example.ui.auth.GoogleSignInButton
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutGoogle
import com.example.ui.components.FrameThumbnailCanvas
import com.example.ui.theme.AmberBeta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnionBeforeRose
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BatchActionsDialog(
    projects: List<StudioProject>,
    batchState: BatchOperationsState,
    currentUser: CloudUserSummary?,
    cloudBackups: List<CloudBackupItem>,
    onDismiss: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onToggleProject: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onFormatChange: (BatchExportFormat) -> Unit,
    onResolutionChange: (Int) -> Unit,
    onRunBatchExport: () -> Unit,
    onBackupToCloud: () -> Unit,
    onRestoreBackup: (CloudBackupItem) -> Unit,
    onDeleteBackup: (String) -> Unit,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    LaunchedEffect(batchState.activeTab, currentUser?.uid) {
        if (batchState.activeTab == 1 && currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val selectedCount = projects.count { it.id in batchState.selectedProjectIds }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 580.dp)
                .testTag("batch_actions_dialog"),
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
                            text = "Batch Export & Cloud Backup",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$selectedCount of ${projects.size} project(s) selected",
                            style = StudioMonoStyle,
                            color = CyberCyan
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("batch_dialog_close")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close batch dialog",
                            tint = TextSecondary
                        )
                    }
                }

                // Mode Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BatchModeTab(
                        label = "Batch Export",
                        icon = Icons.Rounded.Outbox,
                        selected = batchState.activeTab == 0,
                        onClick = { onSelectTab(0) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("batch_tab_export")
                    )
                    BatchModeTab(
                        label = "Cloud Backup",
                        icon = Icons.Rounded.CloudUpload,
                        selected = batchState.activeTab == 1,
                        onClick = { onSelectTab(1) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("batch_tab_cloud")
                    )
                }

                // Multi-select project checklist
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
                            Text(
                                text = "Select Projects",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = onSelectAll,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("batch_select_all")
                                ) {
                                    Text("Select All", color = CyberCyan)
                                }
                                TextButton(
                                    onClick = onClearSelection,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("batch_clear_selection")
                                ) {
                                    Text("Clear", color = TextSecondary)
                                }
                            }
                        }

                        if (projects.isEmpty()) {
                            Text(
                                text = "No projects available in current filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(projects, key = { it.id }) { project ->
                                    val isSelected = project.id in batchState.selectedProjectIds
                                    SelectableProjectRow(
                                        project = project,
                                        selected = isSelected,
                                        onToggle = { onToggleProject(project.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = StudioBorder.copy(alpha = 0.7f))

                if (batchState.activeTab == 0) {
                    BatchExportSection(
                        selectedCount = selectedCount,
                        batchState = batchState,
                        onFormatChange = onFormatChange,
                        onResolutionChange = onResolutionChange,
                        onRunBatchExport = onRunBatchExport
                    )
                } else {
                    CloudBackupSection(
                        selectedCount = selectedCount,
                        batchState = batchState,
                        currentUser = currentUser,
                        cloudBackups = cloudBackups,
                        dateFormatter = dateFormatter,
                        onBackupToCloud = onBackupToCloud,
                        onRestoreBackup = onRestoreBackup,
                        onDeleteBackup = onDeleteBackup,
                        onAuthSuccess = onAuthSuccess,
                        onAuthError = onAuthError,
                        onSignOut = {
                            signOutGoogle(credentialManager = credentialManager, scope = scope)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) NeonPurple.copy(alpha = 0.22f) else StudioSurfaceElevated,
        border = BorderStroke(1.dp, if (selected) NeonPurpleBright else StudioBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) NeonPurpleBright else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SelectableProjectRow(
    project: StudioProject,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) NeonPurple.copy(alpha = 0.14f) else StudioSurfaceElevated,
        border = BorderStroke(1.dp, if (selected) NeonPurple else StudioBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .testTag("batch_select_${project.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) NeonPurpleBright else TextMuted,
                modifier = Modifier.size(22.dp)
            )

            Box(
                modifier = Modifier
                    .size(44.dp, 34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VoidBlack),
                contentAlignment = Alignment.Center
            ) {
                val firstFrame = project.frames.firstOrNull()
                if (firstFrame != null) {
                    FrameThumbnailCanvas(frame = firstFrame, modifier = Modifier.fillMaxWidth())
                } else {
                    Icon(
                        imageVector = when (project.type) {
                            ProjectType.ANIMATION -> Icons.Rounded.Animation
                            ProjectType.DRAWING -> Icons.Rounded.Brush
                            ProjectType.VIDEO -> Icons.Rounded.MovieCreation
                            ProjectType.SOUND -> Icons.Rounded.GraphicEq
                        },
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val countSummary = when (project.type) {
                    ProjectType.ANIMATION, ProjectType.DRAWING -> "${project.frames.size} frame(s)"
                    ProjectType.VIDEO -> "${project.videoClips.size} video clip(s)"
                    ProjectType.SOUND -> "${project.audioClips.size} audio clip(s)"
                }
                Text(
                    text = "${project.type.label} • $countSummary",
                    style = StudioMonoStyle,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BatchExportSection(
    selectedCount: Int,
    batchState: BatchOperationsState,
    onFormatChange: (BatchExportFormat) -> Unit,
    onResolutionChange: (Int) -> Unit,
    onRunBatchExport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Export Format",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BatchExportFormat.entries.forEach { format ->
                val active = batchState.exportFormat == format
                Surface(
                    onClick = { onFormatChange(format) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) CyberCyan.copy(alpha = 0.16f) else StudioSurfaceElevated,
                    border = BorderStroke(1.dp, if (active) CyberCyan else StudioBorder),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("batch_format_${format.name.lowercase()}")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = when (format) {
                                BatchExportFormat.GIF -> "GIF"
                                BatchExportFormat.MP4 -> "MP4"
                                BatchExportFormat.JSON_BUNDLE -> "JSON"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) CyberCyan else TextSecondary
                        )
                    }
                }
            }
        }

        if (batchState.exportFormat != BatchExportFormat.JSON_BUNDLE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Resolution:",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                listOf(480, 720).forEach { height ->
                    val active = batchState.resolutionHeight == height
                    Surface(
                        onClick = { onResolutionChange(height) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) NeonPurple.copy(alpha = 0.22f) else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (active) NeonPurple else StudioBorder),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Text(
                            text = "${height}p",
                            style = StudioMonoStyle,
                            color = if (active) TextPrimary else TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }
            Text(
                text = "GIF/MP4 renders Animation & Drawing projects sequentially (4GB RAM safe). Choose JSON to bundle Video & Sound timelines.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        } else {
            Text(
                text = "Project JSON exports full multi-track data (Animation, Drawing, Video keyframes/motion blur, and Sound clips) into studio exports.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        if (batchState.isBatchExporting) {
            LinearProgressIndicator(
                progress = { batchState.batchProgress.coerceIn(0f, 1f) },
                color = CyberCyan,
                trackColor = StudioSurfaceHighlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }

        if (batchState.batchStatusText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianBg,
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = batchState.batchStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (batchState.lastExportedFileNames.isNotEmpty()) EmeraldLive else AmberBeta
                    )
                    batchState.lastExportedFileNames.take(4).forEach { name ->
                        Text(
                            text = "• $name",
                            style = StudioMonoStyle,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (batchState.lastExportedFileNames.size > 4) {
                        Text(
                            text = "+ ${batchState.lastExportedFileNames.size - 4} more file(s)",
                            style = StudioMonoStyle,
                            color = TextMuted
                        )
                    }
                    batchState.lastSkippedNotes.forEach { note ->
                        Text(
                            text = "Skipped: $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberBeta
                        )
                    }
                }
            }
        }

        Button(
            onClick = onRunBatchExport,
            enabled = !batchState.isBatchExporting && selectedCount > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonPurple,
                contentColor = TextPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("batch_export_confirm_button")
        ) {
            if (batchState.isBatchExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = TextPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Exporting...")
            } else {
                Icon(Icons.Rounded.Outbox, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export Selected ($selectedCount)")
            }
        }
    }
}

@Composable
private fun CloudBackupSection(
    selectedCount: Int,
    batchState: BatchOperationsState,
    currentUser: CloudUserSummary?,
    cloudBackups: List<CloudBackupItem>,
    dateFormatter: SimpleDateFormat,
    onBackupToCloud: () -> Unit,
    onRestoreBackup: (CloudBackupItem) -> Unit,
    onDeleteBackup: (String) -> Unit,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onSignOut: () -> Unit
) {
    if (currentUser == null) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianBg,
            border = BorderStroke(1.dp, StudioBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = CyberCyan)
                    Text(
                        text = "Sign in for Cloud Backup",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Back up your projects, layers, animation frames, and video keyframes privately to your Google-authenticated Firestore library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (!batchState.cloudErrorMessage.isNullOrBlank()) {
                    Text(
                        text = batchState.cloudErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnionBeforeRose
                    )
                }
                GoogleSignInButton(
                    onAuthSuccess = onAuthSuccess,
                    onAuthError = onAuthError
                )
            }
        }
        return
    }

    key(currentUser.uid) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianBg,
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = EmeraldLive)
                        Column {
                            Text(
                                text = currentUser.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentUser.email.isNotBlank()) {
                                Text(
                                    text = currentUser.email,
                                    style = StudioMonoStyle,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StudioBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("google_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign out",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Sign Out", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (batchState.isCloudBackingUp && batchState.cloudBackupProgressText.isNotBlank()) {
                Text(
                    text = batchState.cloudBackupProgressText,
                    style = StudioMonoStyle,
                    color = CyberCyan
                )
            }

            if (!batchState.cloudStatusMessage.isNullOrBlank()) {
                Text(
                    text = batchState.cloudStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = EmeraldLive
                )
            }

            if (!batchState.cloudErrorMessage.isNullOrBlank()) {
                Text(
                    text = batchState.cloudErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnionBeforeRose
                )
            }

            Button(
                onClick = onBackupToCloud,
                enabled = !batchState.isCloudBackingUp && selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPurple,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("batch_backup_confirm_button")
            ) {
                if (batchState.isCloudBackingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = TextPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Backing up...")
                } else {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Backup Selected ($selectedCount)")
                }
            }

            Text(
                text = "Cloud Backups (${cloudBackups.size})",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )

            if (cloudBackups.isEmpty()) {
                Text(
                    text = "No projects backed up yet. Select projects above and tap Backup Selected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(cloudBackups, key = { it.projectId }) { backup ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudioSurfaceElevated,
                            border = BorderStroke(1.dp, StudioBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = backup.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val stamp = if (backup.updatedAtMs > 0L) {
                                        dateFormatter.format(Date(backup.updatedAtMs))
                                    } else {
                                        "Synced"
                                    }
                                    Text(
                                        text = "${backup.projectType.label} • ${backup.fps}fps • $stamp",
                                        style = StudioMonoStyle,
                                        color = CyberCyan,
                                        maxLines = 1
                                    )
                                }
                                IconButton(
                                    onClick = { onRestoreBackup(backup) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("cloud_restore_${backup.projectId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudDownload,
                                        contentDescription = "Restore ${backup.title}",
                                        tint = CyberCyan
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteBackup(backup.projectId) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("cloud_delete_${backup.projectId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete cloud backup ${backup.title}",
                                        tint = OnionBeforeRose
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
