package com.example.ui.video

import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.OverlayTargetType
import com.example.data.model.VideoEffectType
import com.example.data.model.VideoTransitionType
import com.example.ui.components.FeatureMaturity
import com.example.ui.components.StudioStatusBadge
import com.example.ui.components.StudioTopHeader
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import kotlinx.coroutines.delay

@Composable
fun VideoEditorScreen(
    viewModel: VideoEditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val project = state.project
    val clips = project?.videoClips.orEmpty()
    val selectedClip = clips.getOrNull(state.selectedClipIndex)

    val snackbarHostState = remember { SnackbarHostState() }
    var showTextDialog by remember { mutableStateOf(false) }
    var overlayInput by remember { mutableStateOf("") }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importVideoUri(uri)
        }
    }

    val imageOverlayPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importImageOverlayUri(uri)
        }
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Keep playhead & VideoView synchronized within trim bounds (~30fps polling for smooth keyframes)
    LaunchedEffect(state.isPlaying, selectedClip?.id, selectedClip?.trimStartMs, selectedClip?.trimEndMs) {
        val view = videoViewRef ?: return@LaunchedEffect
        val clip = selectedClip ?: return@LaunchedEffect
        if (state.isPlaying) {
            if (view.currentPosition < clip.trimStartMs || view.currentPosition >= clip.trimEndMs) {
                view.seekTo(clip.trimStartMs.toInt())
            }
            view.start()
            while (state.isPlaying) {
                val pos = view.currentPosition.toLong()
                viewModel.seekPlayhead(pos)
                if (pos >= clip.trimEndMs) {
                    view.pause()
                    view.seekTo(clip.trimStartMs.toInt())
                    viewModel.seekPlayhead(clip.trimStartMs)
                    viewModel.setPlaying(false)
                    break
                }
                delay(33)
            }
        } else {
            if (view.isPlaying) view.pause()
        }
    }

    // Seek video preview frame when jumping to keyframes or tapping keyframe timeline while paused
    LaunchedEffect(state.playheadMs, state.isPlaying, selectedClip?.id) {
        val view = videoViewRef
        if (!state.isPlaying && view != null && selectedClip != null) {
            if (kotlin.math.abs(view.currentPosition - state.playheadMs) > 60L) {
                view.seekTo(state.playheadMs.toInt())
            }
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
                title = project?.title ?: "Video Editor",
                subtitle = "${clips.size} clips • Timeline & Cut Engine",
                onBack = {
                    viewModel.setPlaying(false)
                    onBack()
                },
                actions = {
                    StudioStatusBadge(maturity = FeatureMaturity.INCREMENTAL)
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("import_video_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Video Monitor Preview
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianBg,
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        val density = LocalDensity.current
                        val boxWidthPx = with(density) { maxWidth.toPx() }
                        val boxHeightPx = with(density) { maxHeight.toPx() }

                        if (selectedClip != null) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = false
                                            seekTo(selectedClip.trimStartMs.toInt())
                                        }
                                        setVideoURI(Uri.parse(selectedClip.uriString))
                                        videoViewRef = this
                                    }
                                },
                                update = { view ->
                                    if (view.tag != selectedClip.uriString) {
                                        view.tag = selectedClip.uriString
                                        view.setVideoURI(Uri.parse(selectedClip.uriString))
                                        view.seekTo(selectedClip.trimStartMs.toInt())
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Live Color Effect Filter Overlay
                            val fxTint = when (selectedClip.effect) {
                                VideoEffectType.NONE -> Color.Transparent
                                VideoEffectType.NEON_PURPLE -> NeonPurple.copy(alpha = 0.22f)
                                VideoEffectType.ANIME_CONTRAST -> CyberCyan.copy(alpha = 0.14f)
                                VideoEffectType.MANGA_BW -> Color.Black.copy(alpha = 0.35f)
                                VideoEffectType.SAKURA_TINT -> OnionBeforeRose.copy(alpha = 0.18f)
                            }
                            if (fxTint != Color.Transparent) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(fxTint)
                                )
                            }

                            // Keyframed Image Overlay Preview (+ Directional Motion Blur)
                            if (!selectedClip.imageOverlayUri.isNullOrBlank()) {
                                val imgTf = KeyframeInterpolator.evaluate(
                                    keyframes = selectedClip.imageKeyframes,
                                    timeMs = state.playheadMs,
                                    target = OverlayTargetType.IMAGE
                                )
                                val imgBlur = KeyframeInterpolator.evaluateMotionBlur(
                                    keyframes = selectedClip.imageKeyframes,
                                    timeMs = state.playheadMs,
                                    target = OverlayTargetType.IMAGE,
                                    enabled = selectedClip.motionBlurEnabled,
                                    intensity = selectedClip.motionBlurIntensity
                                )
                                val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    (imgBlur.blurRadiusXRatio > 0.04f || imgBlur.blurRadiusYRatio > 0.04f)
                                ) {
                                    Modifier.blur(
                                        radiusX = (imgBlur.blurRadiusXRatio * 10f).dp,
                                        radiusY = (imgBlur.blurRadiusYRatio * 10f).dp,
                                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                                    )
                                } else {
                                    Modifier
                                }
                                imgBlur.trailSamples.asReversed().forEach { sample ->
                                    AsyncImage(
                                        model = selectedClip.imageOverlayUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(110.dp)
                                            .graphicsLayer {
                                                translationX = (sample.transform.x - 0.5f) * boxWidthPx
                                                translationY = (sample.transform.y - 0.5f) * boxHeightPx
                                                scaleX = sample.transform.scale
                                                scaleY = sample.transform.scale
                                                rotationZ = sample.transform.rotationDeg
                                                alpha = (sample.transform.opacity * sample.alphaFactor).coerceIn(0f, 1f)
                                            }
                                            .then(blurModifier)
                                    )
                                }
                                AsyncImage(
                                    model = selectedClip.imageOverlayUri,
                                    contentDescription = "Animated image overlay",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(110.dp)
                                        .graphicsLayer {
                                            translationX = (imgTf.x - 0.5f) * boxWidthPx
                                            translationY = (imgTf.y - 0.5f) * boxHeightPx
                                            scaleX = imgTf.scale
                                            scaleY = imgTf.scale
                                            rotationZ = imgTf.rotationDeg
                                            alpha = imgTf.opacity
                                        }
                                        .then(
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                                (imgBlur.blurRadiusXRatio > 0.08f || imgBlur.blurRadiusYRatio > 0.08f)
                                            ) {
                                                Modifier.blur(
                                                    radiusX = (imgBlur.blurRadiusXRatio * 3.5f).dp,
                                                    radiusY = (imgBlur.blurRadiusYRatio * 3.5f).dp,
                                                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            }

                            // Keyframed Text Overlay Preview (+ Directional Motion Blur)
                            if (selectedClip.overlayText.isNotBlank()) {
                                val txtTf = KeyframeInterpolator.evaluate(
                                    keyframes = selectedClip.textKeyframes,
                                    timeMs = state.playheadMs,
                                    target = OverlayTargetType.TEXT
                                )
                                val txtBlur = KeyframeInterpolator.evaluateMotionBlur(
                                    keyframes = selectedClip.textKeyframes,
                                    timeMs = state.playheadMs,
                                    target = OverlayTargetType.TEXT,
                                    enabled = selectedClip.motionBlurEnabled,
                                    intensity = selectedClip.motionBlurIntensity
                                )
                                val txtTrailBlurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    (txtBlur.blurRadiusXRatio > 0.04f || txtBlur.blurRadiusYRatio > 0.04f)
                                ) {
                                    Modifier.blur(
                                        radiusX = (txtBlur.blurRadiusXRatio * 10f).dp,
                                        radiusY = (txtBlur.blurRadiusYRatio * 10f).dp,
                                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                                    )
                                } else {
                                    Modifier
                                }
                                txtBlur.trailSamples.asReversed().forEach { sample ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = VoidBlack.copy(alpha = 0.55f),
                                        border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.7f)),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .graphicsLayer {
                                                translationX = (sample.transform.x - 0.5f) * boxWidthPx
                                                translationY = (sample.transform.y - 0.5f) * boxHeightPx
                                                scaleX = sample.transform.scale
                                                scaleY = sample.transform.scale
                                                rotationZ = sample.transform.rotationDeg
                                                alpha = (sample.transform.opacity * sample.alphaFactor).coerceIn(0f, 1f)
                                            }
                                            .then(txtTrailBlurModifier)
                                    ) {
                                        Text(
                                            text = selectedClip.overlayText,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = VoidBlack.copy(alpha = 0.72f),
                                    border = BorderStroke(1.dp, NeonPurple),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .graphicsLayer {
                                            translationX = (txtTf.x - 0.5f) * boxWidthPx
                                            translationY = (txtTf.y - 0.5f) * boxHeightPx
                                            scaleX = txtTf.scale
                                            scaleY = txtTf.scale
                                            rotationZ = txtTf.rotationDeg
                                            alpha = txtTf.opacity
                                        }
                                        .then(
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                                (txtBlur.blurRadiusXRatio > 0.08f || txtBlur.blurRadiusYRatio > 0.08f)
                                            ) {
                                                Modifier.blur(
                                                    radiusX = (txtBlur.blurRadiusXRatio * 3.0f).dp,
                                                    radiusY = (txtBlur.blurRadiusYRatio * 3.0f).dp,
                                                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    Text(
                                        text = selectedClip.overlayText,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            // Subtle HUD pill showing active keyframes & motion blur state
                            val totalKfs = selectedClip.textKeyframes.size + selectedClip.imageKeyframes.size
                            if (totalKfs > 0 || selectedClip.motionBlurEnabled) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = VoidBlack.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                ) {
                                    val hudText = buildString {
                                        append("♦ $totalKfs KF")
                                        if (selectedClip.motionBlurEnabled) append(" • BLUR")
                                    }
                                    Text(
                                        text = hudText,
                                        style = StudioMonoStyle,
                                        color = CyberCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.VideoLibrary,
                                    contentDescription = null,
                                    tint = NeonPurple,
                                    modifier = Modifier.size(42.dp)
                                )
                                Text(
                                    text = "Import a video from your device to trim, cut & split",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Button(
                                    onClick = {
                                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                                ) {
                                    Text("Select Video")
                                }
                            }
                        }
                    }
                }

                // Clip Timeline Strip
                if (clips.isNotEmpty()) {
                    Text("Clip Timeline (${clips.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(clips, key = { _, c -> c.id }) { idx, clip ->
                            val selected = idx == state.selectedClipIndex
                            val thumbs = state.thumbnailsByClipId[clip.id].orEmpty()
                            Surface(
                                onClick = { viewModel.selectClip(idx) },
                                shape = RoundedCornerShape(12.dp),
                                color = StudioSurfaceElevated,
                                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) NeonPurple else StudioBorder),
                                modifier = Modifier
                                    .width(170.dp)
                                    .height(74.dp)
                                    .testTag("video_clip_$idx")
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    ) {
                                        if (thumbs.isNotEmpty()) {
                                            thumbs.forEach { bmp ->
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxSize()
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(ObsidianBg),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Rounded.MovieCreation, contentDescription = null, tint = TextMuted)
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = clip.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = formatMs(clip.effectiveDurationMs),
                                            style = StudioMonoStyle,
                                            color = CyberCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Selected Clip Editing Controls (Playhead, Trim, Cut, Split, Effects, Transitions, Text)
                if (selectedClip != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = StudioSurface,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Playhead scrubber + Play/Pause
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    onClick = viewModel::togglePlay,
                                    shape = CircleShape,
                                    color = if (state.isPlaying) CyberCyan else NeonPurple,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = "Play or pause clip",
                                            tint = VoidBlack
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Playhead", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                        Text(formatMs(state.playheadMs), style = StudioMonoStyle, color = CyberCyan)
                                    }
                                    Slider(
                                        value = state.playheadMs.toFloat().coerceIn(
                                            selectedClip.trimStartMs.toFloat(),
                                            selectedClip.trimEndMs.toFloat().coerceAtLeast(selectedClip.trimStartMs.toFloat() + 1f)
                                        ),
                                        onValueChange = { ms ->
                                            viewModel.seekPlayhead(ms.toLong())
                                            videoViewRef?.seekTo(ms.toInt())
                                        },
                                        valueRange = selectedClip.trimStartMs.toFloat()..selectedClip.trimEndMs.toFloat().coerceAtLeast(selectedClip.trimStartMs.toFloat() + 1f),
                                        colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan)
                                    )
                                }
                            }

                            // Trim Start & End Range Slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Trim Start / End", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                    Text(
                                        text = "${formatMs(selectedClip.trimStartMs)} — ${formatMs(selectedClip.trimEndMs)}",
                                        style = StudioMonoStyle,
                                        color = NeonPurpleBright
                                    )
                                }
                                RangeSlider(
                                    value = selectedClip.trimStartMs.toFloat()..selectedClip.trimEndMs.toFloat(),
                                    onValueChange = { range ->
                                        viewModel.updateTrimRange(range.start.toLong(), range.endInclusive.toLong())
                                    },
                                    valueRange = 0f..selectedClip.durationMs.toFloat().coerceAtLeast(500f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonPurple,
                                        activeTrackColor = NeonPurple
                                    ),
                                    modifier = Modifier.testTag("video_trim_slider")
                                )
                            }

                            // Split & Cut Actions
                            Text("Split & Cut Operations", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = viewModel::splitSelectedClipAtPlayhead,
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("split_clip_button")
                                ) {
                                    Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Split", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.cutClipAtPlayhead(keepBeforePlayhead = false) },
                                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cut_before_button")
                                ) {
                                    Text("Cut Left", color = CyberCyan, style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.cutClipAtPlayhead(keepBeforePlayhead = true) },
                                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cut_after_button")
                                ) {
                                    Text("Cut Right", color = CyberCyan, style = MaterialTheme.typography.labelMedium)
                                }
                                IconButton(
                                    onClick = viewModel::deleteSelectedClip,
                                    modifier = Modifier.testTag("delete_clip_button")
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete clip", tint = OnionBeforeRose)
                                }
                            }

                            // Effects
                            Text("Visual Effect", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(VideoEffectType.entries) { fx ->
                                    val active = fx == selectedClip.effect
                                    Surface(
                                        onClick = { viewModel.setClipEffect(fx) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (active) NeonPurple.copy(alpha = 0.24f) else StudioSurfaceElevated,
                                        border = BorderStroke(1.dp, if (active) NeonPurple else StudioBorder)
                                    ) {
                                        Text(
                                            text = fx.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (active) TextPrimary else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }

                            // Transitions & Text Overlay
                            Text("Transition In", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(VideoTransitionType.entries) { tr ->
                                    val active = tr == selectedClip.transitionIn
                                    Surface(
                                        onClick = { viewModel.setClipTransition(tr) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (active) CyberCyan.copy(alpha = 0.2f) else StudioSurfaceElevated,
                                        border = BorderStroke(1.dp, if (active) CyberCyan else StudioBorder)
                                    ) {
                                        Text(
                                            text = tr.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (active) CyberCyan else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }

                            KeyframeTrackPanel(
                                clip = selectedClip,
                                playheadMs = state.playheadMs,
                                selectedTarget = state.selectedOverlayTarget,
                                onSelectTarget = viewModel::selectOverlayTarget,
                                onEditTextClick = {
                                    overlayInput = selectedClip.overlayText
                                    showTextDialog = true
                                },
                                onImportImageClick = {
                                    imageOverlayPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onClearImageClick = viewModel::clearImageOverlay,
                                onSeekPlayhead = { ms ->
                                    viewModel.seekPlayhead(ms)
                                    videoViewRef?.seekTo(ms.toInt())
                                },
                                onAddOrUpdateKeyframe = viewModel::addOrUpdateKeyframeAtPlayhead,
                                onDeleteKeyframeAtPlayhead = viewModel::deleteKeyframeAtPlayhead,
                                onJumpToKeyframe = { kf ->
                                    viewModel.jumpToKeyframe(kf)
                                    videoViewRef?.seekTo(kf.timeMs.toInt())
                                },
                                onJumpAdjacentKeyframe = { dir ->
                                    viewModel.jumpToAdjacentKeyframe(dir)
                                },
                                onClearAllKeyframes = viewModel::clearKeyframesForActiveTarget,
                                onUpdateTransform = { x, y, scale, rot, opacity, easing ->
                                    viewModel.updateTransformAtPlayhead(x, y, scale, rot, opacity, easing)
                                },
                                onToggleMotionBlur = viewModel::setMotionBlurEnabled,
                                onMotionBlurIntensityChange = viewModel::setMotionBlurIntensity
                            )
                        }
                    }
                }

                // Clear Incremental Feature Disclosure Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberBeta.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, AmberBeta.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Incremental Module Notice: Clip import, trim, cut, split, effects, text/image overlays & keyframe curves preview live and persist in Room DB. Multi-clip MediaCodec render baking is queued for a future build.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmberBeta,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            containerColor = StudioSurfaceElevated,
            title = { Text("Clip Text Overlay", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = overlayInput,
                    onValueChange = { overlayInput = it },
                    label = { Text("Anime Title / Dialogue") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setClipOverlayText(overlayInput.trim())
                        showTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
