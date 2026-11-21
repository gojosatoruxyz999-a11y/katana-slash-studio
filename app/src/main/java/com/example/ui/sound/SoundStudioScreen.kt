package com.example.ui.sound

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.FeatureMaturity
import com.example.ui.components.StudioStatusBadge
import com.example.ui.components.StudioTopHeader
import com.example.ui.video.formatMs
import com.example.ui.theme.AmberBeta
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OnionBeforeRose
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioMonoStyle
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import kotlin.math.roundToInt

private data class AnimeSfxPreset(
    val id: String,
    val title: String,
    val subtitle: String
)

private val ANIME_SFX_PRESETS = listOf(
    AnimeSfxPreset("katana_slash", "Katana Slash", "Metallic blade sweep"),
    AnimeSfxPreset("bass_impact", "Impact Boom", "Sub-bass shockwave"),
    AnimeSfxPreset("spirit_charge", "Aura Charge", "Rising neon energy"),
    AnimeSfxPreset("snap_click", "Shutter Snap", "Sharp cue click")
)

@Composable
fun SoundStudioScreen(
    viewModel: SoundViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val project = state.project
    val snackbarHostState = remember { SnackbarHostState() }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.startVoiceRecording()
        }
    }

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
                title = project?.title ?: "Sound Studio",
                subtitle = "${project?.audioClips?.size ?: 0} audio tracks",
                onBack = onBack,
                actions = {
                    StudioStatusBadge(maturity = FeatureMaturity.INCREMENTAL)
                    Spacer(Modifier.width(8.dp))
                }
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Voiceover Recorder Card
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = StudioSurface,
                        border = BorderStroke(
                            1.dp,
                            if (state.isRecording) OnionBeforeRose else NeonPurple.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Voiceover Booth", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Text(
                                    text = if (state.isRecording) {
                                        "Recording AAC audio... 00:${"%02d".format(state.recordingSeconds)}"
                                    } else {
                                        "Record character dialogue or foley straight to your track"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (state.isRecording) OnionBeforeRose else TextSecondary
                                )
                            }

                            Surface(
                                onClick = {
                                    if (state.isRecording) {
                                        viewModel.stopVoiceRecording()
                                    } else if (hasMicPermission) {
                                        viewModel.startVoiceRecording()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                shape = CircleShape,
                                color = if (state.isRecording) OnionBeforeRose else NeonPurple,
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("voice_record_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (state.isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                        contentDescription = "Record voice",
                                        tint = VoidBlack
                                    )
                                }
                            }
                        }
                    }
                }

                // Synthesized Anime SFX Pad
                item {
                    Text("Anime SFX Synthesizer Pad", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ANIME_SFX_PRESETS.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { preset ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = StudioSurfaceElevated,
                                        border = BorderStroke(1.dp, StudioBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                                                Text(preset.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                            }
                                            Text(preset.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                            Spacer(Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                OutlinedButton(
                                                    onClick = { viewModel.triggerSfx(preset.id, preset.title, addToTrack = false) },
                                                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(15.dp))
                                                    Text("Test", color = CyberCyan, style = MaterialTheme.typography.labelMedium)
                                                }
                                                Button(
                                                    onClick = { viewModel.triggerSfx(preset.id, preset.title, addToTrack = true) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Text("Add", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Project Audio Clips
                item {
                    Text(
                        text = "Project Audio Tracks (${project?.audioClips?.size ?: 0})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                val audioClips = project?.audioClips.orEmpty()
                if (audioClips.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = StudioSurface,
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = NeonPurple)
                                Text(
                                    text = "Record a voice line or add an SFX above to build your audio track.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(audioClips, key = { it.id }) { clip ->
                        val isPlayingThis = state.playingClipId == clip.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = StudioSurface,
                            border = BorderStroke(1.dp, if (isPlayingThis) CyberCyan else StudioBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = { viewModel.togglePlayClip(clip) },
                                        shape = CircleShape,
                                        color = if (isPlayingThis) CyberCyan else NeonPurple,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isPlayingThis) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                                contentDescription = "Play audio clip",
                                                tint = VoidBlack
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(clip.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        Text(
                                            text = "${clip.trackType} • ${formatMs(clip.durationMs)}",
                                            style = StudioMonoStyle,
                                            color = CyberCyan
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteClip(clip.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete clip", tint = OnionBeforeRose)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    Slider(
                                        value = clip.volume,
                                        onValueChange = { viewModel.updateClipVolume(clip.id, it) },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(thumbColor = NeonPurpleBright, activeTrackColor = NeonPurple),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("${(clip.volume * 100).roundToInt()}%", style = StudioMonoStyle, color = TextSecondary)
                                }
                            }
                        }
                    }
                }

                // Clear Incremental Notice
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AmberBeta.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, AmberBeta.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Incremental Module Notice: Live microphone AAC recording, synthesized SFX playback, and per-clip volume save in Room DB. Background music file import & multi-track mixdown rendering are queued.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AmberBeta,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.TopCenter))
    }
}
