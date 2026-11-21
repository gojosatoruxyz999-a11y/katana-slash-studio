package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DefaultNeonPalettes
import com.example.data.local.StarterProjectFactory
import com.example.data.local.StudioJsonCodec
import com.example.data.model.DrawTool
import com.example.data.model.KeyframeEasing
import com.example.data.model.OnionSkinConfig
import com.example.data.model.OverlayKeyframe
import com.example.data.model.OverlayTargetType
import com.example.data.model.ProjectType
import com.example.data.model.StudioOutroConfig
import com.example.data.model.VideoClipItem
import com.example.data.remote.CloudBackupMapper
import com.example.export.BatchExportFormat
import com.example.export.BatchProjectExporter
import com.example.export.GifEncoder
import com.example.ui.animation.AnimationViewModel
import com.example.ui.components.formatHexColor
import com.example.ui.components.parseHexColor
import com.example.ui.drawing.DrawingViewModel
import com.example.ui.projects.ProjectsViewModel
import com.example.ui.video.KeyframeInterpolator
import com.example.ui.video.VideoEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioEngineTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `json codec round trips starter frames layers and onion skin`() {
        val starter = StarterProjectFactory.createStarterKatanaProject()
        val framesJson = StudioJsonCodec.encodeFrames(starter.frames)
        val decodedFrames = StudioJsonCodec.decodeFrames(framesJson)
        assertEquals(starter.frames.size, decodedFrames.size)
        assertEquals(starter.frames[0].layers.size, decodedFrames[0].layers.size)
        assertEquals(starter.frames[2].layers[1].strokes.size, decodedFrames[2].layers[1].strokes.size)

        val onion = OnionSkinConfig(enabled = true, showBefore = false, showAfter = true, beforeCount = 2, afterCount = 3, beforeOpacity = 0.45f, afterOpacity = 0.25f, tintEnabled = false)
        val onionDecoded = StudioJsonCodec.decodeOnionSkin(StudioJsonCodec.encodeOnionSkin(onion))
        assertEquals(onion, onionDecoded)
    }

    @Test
    fun `gif encoder writes valid GIF89a header and trailer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val starter = StarterProjectFactory.createStarterKatanaProject()
        val outFile = File(context.cacheDir, "test_export.gif")
        if (outFile.exists()) outFile.delete()

        GifEncoder.exportFramesToGif(
            frames = starter.frames,
            fps = 12,
            width = 320,
            height = 200,
            outFile = outFile
        ) {}

        assertTrue(outFile.exists())
        val bytes = outFile.readBytes()
        assertTrue(bytes.size > 100)
        assertEquals("GIF89a", String(bytes, 0, 6, Charsets.US_ASCII))
        assertEquals(0x3B.toByte(), bytes.last())
        outFile.delete()
    }

    private fun TestScope.awaitUntil(timeoutMs: Long = 5000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            if (!condition()) {
                Thread.sleep(15)
            }
        }
        advanceUntilIdle()
        assertTrue("Timed out waiting for async ViewModel/IO condition", condition())
    }

    @Test
    fun `animation viewmodel manages layers merge reorder and onion skinning`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = RoninAppContainer(context)
        val starter = StarterProjectFactory.createStarterKatanaProject()
        container.repository.saveProject(starter)

        val vm = AnimationViewModel(container.repository, starter.id)
        awaitUntil { vm.uiState.value.project != null }

        val initialLayers = vm.uiState.value.project!!.frames[0].layers.size
        vm.addLayer()
        assertEquals(initialLayers + 1, vm.uiState.value.project!!.frames[0].layers.size)

        val topIdx = vm.uiState.value.project!!.frames[0].layers.lastIndex
        vm.renameLayer(topIdx, "Highlight FX")
        assertEquals("Highlight FX", vm.uiState.value.project!!.frames[0].layers[topIdx].name)

        vm.mergeLayerDown(topIdx)
        assertEquals(initialLayers, vm.uiState.value.project!!.frames[0].layers.size)

        vm.toggleOnionSkin()
        assertFalse(vm.uiState.value.project!!.onionSkin.enabled)
        vm.updateOnionSkin { it.copy(enabled = true, beforeCount = 3, afterCount = 2) }
        assertTrue(vm.uiState.value.project!!.onionSkin.enabled)
        assertEquals(3, vm.uiState.value.project!!.onionSkin.beforeCount)
    }

    @Test
    fun `video editor viewmodel splits trims and cuts clips cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = RoninAppContainer(context)
        val vidProj = container.repository.createProject("Test Edit", ProjectType.VIDEO, 24)

        val vm = VideoEditorViewModel(context, container.repository, vidProj.id)
        awaitUntil { vm.uiState.value.project != null }

        vm.importVideoUri(Uri.parse("content://media/external/video/media/101"))
        awaitUntil { vm.uiState.value.project?.videoClips?.size == 1 }
        assertEquals(1, vm.uiState.value.project!!.videoClips.size)

        val clip = vm.uiState.value.project!!.videoClips[0]
        vm.updateTrimRange(500L, clip.durationMs - 500L)
        vm.seekPlayhead(clip.durationMs / 2L)
        vm.splitSelectedClipAtPlayhead()

        val clipsAfterSplit = vm.uiState.value.project!!.videoClips
        assertEquals(2, clipsAfterSplit.size)
        assertEquals(clipsAfterSplit[0].trimEndMs, clipsAfterSplit[1].trimStartMs)
    }

    @Test
    fun `drawing viewmodel toggles previous and next onion skin overlays across frames`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = RoninAppContainer(context)
        val sketchbook = StarterProjectFactory.createStarterDrawingSketchbookProject()
        container.repository.saveProject(sketchbook)

        val vm = DrawingViewModel(container.repository, sketchbook.id)
        awaitUntil { vm.uiState.value.project != null }

        assertEquals(3, vm.uiState.value.project!!.frames.size)
        assertEquals(1, vm.uiState.value.currentFrameIndex)
        assertTrue(vm.uiState.value.project!!.onionSkin.enabled)
        assertTrue(vm.uiState.value.project!!.onionSkin.showBefore)
        assertTrue(vm.uiState.value.project!!.onionSkin.showAfter)

        vm.toggleShowPreviousFrames()
        assertFalse(vm.uiState.value.project!!.onionSkin.showBefore)
        assertTrue(vm.uiState.value.project!!.onionSkin.showAfter)

        vm.toggleShowNextFrames()
        assertFalse(vm.uiState.value.project!!.onionSkin.showAfter)

        vm.toggleShowPreviousFrames()
        assertTrue(vm.uiState.value.project!!.onionSkin.showBefore)

        vm.addBlankFrameAfterCurrent()
        assertEquals(4, vm.uiState.value.project!!.frames.size)
        assertEquals(2, vm.uiState.value.currentFrameIndex)
    }

    @Test
    fun `keyframe interpolator evaluates linear and eased transforms accurately`() {
        val kfs = listOf(
            OverlayKeyframe(
                id = "kf1",
                timeMs = 1000L,
                x = 0.2f,
                y = 0.2f,
                scale = 1.0f,
                rotationDeg = 0f,
                opacity = 0.0f,
                easing = KeyframeEasing.LINEAR
            ),
            OverlayKeyframe(
                id = "kf2",
                timeMs = 3000L,
                x = 0.8f,
                y = 0.6f,
                scale = 2.0f,
                rotationDeg = 90f,
                opacity = 1.0f,
                easing = KeyframeEasing.EASE_IN_OUT
            )
        )

        val before = KeyframeInterpolator.evaluate(kfs, 200L, OverlayTargetType.TEXT)
        assertEquals(0.2f, before.x, 0.001f)
        assertEquals(0.0f, before.opacity, 0.001f)

        val mid = KeyframeInterpolator.evaluate(kfs, 2000L, OverlayTargetType.TEXT)
        assertEquals(0.5f, mid.x, 0.001f)
        assertEquals(0.4f, mid.y, 0.001f)
        assertEquals(1.5f, mid.scale, 0.001f)
        assertEquals(45f, mid.rotationDeg, 0.01f)
        assertEquals(0.5f, mid.opacity, 0.001f)

        val after = KeyframeInterpolator.evaluate(kfs, 4500L, OverlayTargetType.TEXT)
        assertEquals(0.8f, after.x, 0.001f)
        assertEquals(90f, after.rotationDeg, 0.01f)
        assertEquals(1.0f, after.opacity, 0.001f)
    }

    @Test
    fun `json codec round trips video clips with text and image overlay keyframes`() {
        val clip = VideoClipItem(
            id = "clip_1",
            uriString = "content://media/external/video/media/99",
            displayName = "Katana.mp4",
            durationMs = 5000L,
            overlayText = "RONIN SLASH",
            textKeyframes = listOf(
                OverlayKeyframe("tk1", 0L, x = 0.25f, y = 0.8f, scale = 0.8f, rotationDeg = -15f, opacity = 0.4f, easing = KeyframeEasing.EASE_OUT),
                OverlayKeyframe("tk2", 4000L, x = 0.75f, y = 0.5f, scale = 1.5f, rotationDeg = 15f, opacity = 1.0f, easing = KeyframeEasing.LINEAR)
            ),
            imageOverlayUri = "content://media/external/images/media/42",
            imageOverlayName = "slash_fx.png",
            imageKeyframes = listOf(
                OverlayKeyframe("ik1", 500L, x = 0.5f, y = 0.5f, scale = 1.2f, rotationDeg = 45f, opacity = 0.9f)
            ),
            motionBlurEnabled = true,
            motionBlurIntensity = 0.85f
        )

        val encoded = StudioJsonCodec.encodeVideoClips(listOf(clip))
        val decoded = StudioJsonCodec.decodeVideoClips(encoded)
        assertEquals(1, decoded.size)
        val out = decoded[0]
        assertEquals("RONIN SLASH", out.overlayText)
        assertEquals(2, out.textKeyframes.size)
        assertEquals(KeyframeEasing.EASE_OUT, out.textKeyframes[0].easing)
        assertEquals(1.5f, out.textKeyframes[1].scale, 0.001f)
        assertEquals("content://media/external/images/media/42", out.imageOverlayUri)
        assertEquals("slash_fx.png", out.imageOverlayName)
        assertEquals(1, out.imageKeyframes.size)
        assertEquals(45f, out.imageKeyframes[0].rotationDeg, 0.01f)
        assertTrue(out.motionBlurEnabled)
        assertEquals(0.85f, out.motionBlurIntensity, 0.001f)
    }

    @Test
    fun `video editor viewmodel manages text and image keyframes and preserves motion on split`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = RoninAppContainer(context)
        val vidProj = container.repository.createProject("KF Edit", ProjectType.VIDEO, 24)

        val vm = VideoEditorViewModel(context, container.repository, vidProj.id)
        awaitUntil { vm.uiState.value.project != null }

        vm.importVideoUri(Uri.parse("content://media/external/video/media/202"))
        awaitUntil { vm.uiState.value.project?.videoClips?.size == 1 }

        val clipDuration = vm.uiState.value.project!!.videoClips[0].durationMs
        vm.setClipOverlayText("BANKAI")
        vm.seekPlayhead(0L)
        vm.updateTransformAtPlayhead(x = 0.1f, y = 0.8f, scale = 0.5f, rotationDeg = -30f, opacity = 0.2f, easing = KeyframeEasing.LINEAR)
        vm.seekPlayhead(clipDuration)
        vm.updateTransformAtPlayhead(x = 0.9f, y = 0.2f, scale = 1.5f, rotationDeg = 30f, opacity = 1.0f)

        assertEquals(2, vm.uiState.value.project!!.videoClips[0].textKeyframes.size)

        // Import image overlay and stamp a keyframe
        vm.importImageOverlayUri(Uri.parse("content://media/external/images/media/77"))
        awaitUntil { vm.uiState.value.project?.videoClips?.getOrNull(0)?.imageOverlayUri != null }
        assertEquals(OverlayTargetType.IMAGE, vm.uiState.value.selectedOverlayTarget)
        vm.seekPlayhead(1000L)
        vm.addOrUpdateKeyframeAtPlayhead()
        assertEquals(1, vm.uiState.value.project!!.videoClips[0].imageKeyframes.size)

        // Delete image keyframe at playhead
        vm.deleteKeyframeAtPlayhead()
        assertEquals(0, vm.uiState.value.project!!.videoClips[0].imageKeyframes.size)

        // Split clip at midpoint and verify interpolated boundary keyframe is preserved on both halves
        val mid = clipDuration / 2L
        vm.seekPlayhead(mid)
        vm.splitSelectedClipAtPlayhead()

        val splitClips = vm.uiState.value.project!!.videoClips
        assertEquals(2, splitClips.size)
        assertTrue(splitClips[0].textKeyframes.size >= 2)
        assertTrue(splitClips[1].textKeyframes.size >= 2)
        val firstEndTf = KeyframeInterpolator.evaluate(splitClips[0].textKeyframes, mid, OverlayTargetType.TEXT)
        val secondStartTf = KeyframeInterpolator.evaluate(splitClips[1].textKeyframes, mid, OverlayTargetType.TEXT)
        assertEquals(0.5f, firstEndTf.x, 0.02f)
        assertEquals(firstEndTf.x, secondStartTf.x, 0.001f)
        assertEquals(firstEndTf.scale, secondStartTf.scale, 0.001f)
    }

    @Test
    fun `motion blur evaluator generates velocity-proportional directional trails and respects toggle`() = runTest {
        val kfs = listOf(
            OverlayKeyframe("m1", 0L, x = 0.1f, y = 0.5f, easing = KeyframeEasing.LINEAR),
            OverlayKeyframe("m2", 1000L, x = 0.9f, y = 0.5f, easing = KeyframeEasing.LINEAR)
        )

        val disabledBlur = KeyframeInterpolator.evaluateMotionBlur(
            keyframes = kfs,
            timeMs = 500L,
            target = OverlayTargetType.TEXT,
            enabled = false,
            intensity = 0.8f
        )
        assertTrue(disabledBlur.trailSamples.isEmpty())

        val stationaryBlur = KeyframeInterpolator.evaluateMotionBlur(
            keyframes = kfs,
            timeMs = 1500L,
            target = OverlayTargetType.TEXT,
            enabled = true,
            intensity = 0.8f
        )
        assertTrue(stationaryBlur.trailSamples.isEmpty())

        val activeBlur = KeyframeInterpolator.evaluateMotionBlur(
            keyframes = kfs,
            timeMs = 500L,
            target = OverlayTargetType.TEXT,
            enabled = true,
            intensity = 0.8f,
            sampleCount = 5
        )
        assertEquals(5, activeBlur.trailSamples.size)
        assertTrue(activeBlur.speedNormalized > 0.2f)
        assertTrue(activeBlur.blurRadiusXRatio > activeBlur.blurRadiusYRatio)
        assertTrue(activeBlur.trailSamples.first().transform.x < 0.5f)
        assertTrue(activeBlur.trailSamples.first().alphaFactor > activeBlur.trailSamples.last().alphaFactor)

        // Verify ViewModel motion blur toggle and intensity controls
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = RoninAppContainer(context)
        val vidProj = container.repository.createProject("Blur Edit", ProjectType.VIDEO, 24)
        val vm = VideoEditorViewModel(context, container.repository, vidProj.id)
        awaitUntil { vm.uiState.value.project != null }
        vm.importVideoUri(Uri.parse("content://media/external/video/media/303"))
        awaitUntil { vm.uiState.value.project?.videoClips?.size == 1 }

        assertFalse(vm.uiState.value.project!!.videoClips[0].motionBlurEnabled)
        vm.setMotionBlurEnabled(true)
        assertTrue(vm.uiState.value.project!!.videoClips[0].motionBlurEnabled)
        vm.setMotionBlurIntensity(0.9f)
        assertEquals(0.9f, vm.uiState.value.project!!.videoClips[0].motionBlurIntensity, 0.001f)
    }

    @Test
    fun `cloud backup mapper aligns with firestore rules keys and round trips frames and keyframes`() {
        val anim = StarterProjectFactory.createStarterKatanaProject()
        val videoClip = VideoClipItem(
            id = "vc_1",
            uriString = "content://media/external/video/media/88",
            displayName = "Clip.mp4",
            durationMs = 4000L,
            overlayText = "SLASH",
            textKeyframes = listOf(
                OverlayKeyframe("k1", 0L, x = 0.2f, y = 0.7f, scale = 1.1f, rotationDeg = -10f, opacity = 0.8f),
                OverlayKeyframe("k2", 2000L, x = 0.8f, y = 0.3f, scale = 1.4f, rotationDeg = 10f, opacity = 1.0f)
            ),
            motionBlurEnabled = true,
            motionBlurIntensity = 0.75f
        )
        val combinedProject = anim.copy(videoClips = listOf(videoClip))

        val createPayload = CloudBackupMapper.toFirestoreData(
            project = combinedProject,
            userId = "user_123",
            isCreate = true,
            useServerTimestamps = false
        )
        assertFalse(createPayload.containsKey("id"))
        assertEquals("user_123", createPayload["userId"])
        assertEquals(13, createPayload.size) // 13 + createdAt + updatedAt = 15 on live Firestore write

        val updatePayload = CloudBackupMapper.toFirestoreData(
            project = combinedProject,
            userId = "user_123",
            isCreate = false,
            useServerTimestamps = false
        )
        assertFalse(updatePayload.containsKey("userId"))
        assertFalse(updatePayload.containsKey("createdAt"))
        assertEquals(12, updatePayload.size) // 12 + updatedAt = 13 mutable keys (rules allow <=14)

        val restoredItem = CloudBackupMapper.fromFirestoreMap(
            projectId = combinedProject.id,
            data = createPayload,
            estimatedUpdatedAtMs = 9999L
        )
        val restored = restoredItem.restoredProject!!
        assertEquals(combinedProject.id, restored.id)
        assertEquals(combinedProject.title, restored.title)
        assertEquals(combinedProject.frames.size, restored.frames.size)
        assertEquals(combinedProject.frames[0].layers.size, restored.frames[0].layers.size)
        assertEquals(1, restored.videoClips.size)
        assertEquals(2, restored.videoClips[0].textKeyframes.size)
        assertTrue(restored.videoClips[0].motionBlurEnabled)
        assertEquals(0.75f, restored.videoClips[0].motionBlurIntensity, 0.001f)
    }

    @Test
    fun `batch project exporter exports multiple projects sequentially to GIF and JSON`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exportDir = File(context.cacheDir, "batch_export_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val anim = StarterProjectFactory.createStarterKatanaProject()
        val drawing = StarterProjectFactory.createStarterDrawingSketchbookProject()
        val videoProj = StarterProjectFactory.createNewBlankProject("Video Demo", ProjectType.VIDEO, 24)

        val gifResult = BatchProjectExporter.exportProjects(
            projects = listOf(anim, drawing, videoProj),
            format = BatchExportFormat.GIF,
            resolutionHeight = 360,
            exportsDir = exportDir
        ) { _, _, _, _ -> }

        assertEquals(2, gifResult.exportedFiles.size)
        assertEquals(1, gifResult.skippedMessages.size)
        assertTrue(gifResult.exportedFiles.all { it.exists() && it.length() > 50L })

        val jsonResult = BatchProjectExporter.exportProjects(
            projects = listOf(anim, videoProj),
            format = BatchExportFormat.JSON_BUNDLE,
            resolutionHeight = 480,
            exportsDir = exportDir
        ) { _, _, _, _ -> }

        assertEquals(2, jsonResult.exportedFiles.size)
        assertEquals(0, jsonResult.skippedMessages.size)
        assertTrue(jsonResult.exportedFiles.all { it.name.endsWith(".ronin.json") && it.readText().contains("schemaVersion") })
        exportDir.deleteRecursively()
    }

    @Test
    fun `projects viewmodel multi-select dialog toggles selection and batch exports`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = RoninAppContainer(context)
        container.repository.ensureStarterData()

        val vm = ProjectsViewModel(container.repository)
        awaitUntil { vm.uiState.value.projects.size >= 2 }

        val firstTwo = vm.uiState.value.projects.take(2)
        vm.openBatchDialog(initialTab = 0)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.batch.isDialogOpen)
        assertEquals(vm.uiState.value.projects.size, vm.uiState.value.batch.selectedProjectIds.size)

        vm.clearProjectSelection()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.batch.selectedProjectIds.isEmpty())

        vm.toggleProjectSelection(firstTwo[0].id)
        vm.toggleProjectSelection(firstTwo[1].id)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.batch.selectedProjectIds.size)

        vm.setBatchExportFormat(BatchExportFormat.JSON_BUNDLE)
        advanceUntilIdle()
        vm.runBatchExport()
        awaitUntil { !vm.uiState.value.batch.isBatchExporting && vm.uiState.value.batch.lastExportedFileNames.isNotEmpty() }

        assertEquals(2, vm.uiState.value.batch.lastExportedFileNames.size)
        vm.closeBatchDialog()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.batch.isDialogOpen)
    }

    @Test
    fun `json codec round trips project bundle with studio brushes glow and weights`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = RoninAppContainer(context)
        val starter = StarterProjectFactory.createStarterKatanaProject()

        val jsonBundle = StudioJsonCodec.encodeProjectBundle(starter)
        val decoded = StudioJsonCodec.decodeProjectBundle(jsonBundle)!!

        assertEquals(starter.id, decoded.id)
        assertEquals(starter.title, decoded.title)
        assertEquals(8, decoded.fps)
        assertEquals(starter.frames.size, decoded.frames.size)
        val katanaLayer = decoded.frames[1].layers.firstOrNull { it.strokes.any { s -> s.tool == DrawTool.KATANA_BLADE } }
        assertTrue(katanaLayer != null)
        val bladeStroke = katanaLayer!!.strokes.first { it.tool == DrawTool.KATANA_BLADE }
        assertTrue(bladeStroke.glowIntensity > 0f)
        assertEquals(bladeStroke.points.size, bladeStroke.pointWeights.size)

        val vm = AnimationViewModel(container.repository, null)
        awaitUntil { vm.uiState.value.project != null }
        assertTrue(vm.loadProjectFromJson(jsonBundle))
        assertEquals(starter.title, vm.uiState.value.project!!.title)
    }

    @Test
    fun `animation viewmodel supports studio brushes sliders fullscreen and png export`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = RoninAppContainer(context)
        val starter = StarterProjectFactory.createStarterKatanaProject()
        container.repository.saveProject(starter)

        val vm = AnimationViewModel(container.repository, starter.id)
        awaitUntil { vm.uiState.value.project != null }

        vm.selectTool(DrawTool.KATANA_BLADE)
        vm.setBrushSizePx(42f)
        vm.setBrushOpacity(0.72f)
        vm.setGlowIntensity(0.88f)
        vm.toggleBrushSettingsExpanded()
        vm.toggleFullScreenCanvas()

        assertEquals(42f, vm.uiState.value.brushSizePx, 0.01f)
        assertEquals(0.72f, vm.uiState.value.brushOpacity, 0.01f)
        assertEquals(0.88f, vm.uiState.value.glowIntensity, 0.01f)
        assertTrue(vm.uiState.value.isBrushSettingsExpanded)
        assertTrue(vm.uiState.value.isFullScreenCanvas)

        vm.startStroke(0.1f, 0.5f)
        vm.appendStrokePoint(0.4f, 0.45f)
        vm.appendStrokePoint(0.8f, 0.35f)
        vm.finishStroke()

        val lastLayer = vm.uiState.value.project!!.frames[0].layers[vm.uiState.value.activeLayerIndex]
        val added = lastLayer.strokes.last()
        assertEquals(DrawTool.KATANA_BLADE, added.tool)
        assertEquals(0.72f, added.alpha, 0.01f)
        assertEquals(0.88f, added.glowIntensity, 0.01f)
        assertEquals(3, added.pointWeights.size)

        vm.openExportSaveModal()
        vm.setTransparentPngBackground(true)
        vm.exportCurrentFramePng()
        awaitUntil { !vm.uiState.value.exportModal.isExporting && vm.uiState.value.exportModal.lastExportedFile != null }

        val pngFile = vm.uiState.value.exportModal.lastExportedFile!!
        assertTrue(pngFile.exists())
        assertTrue(pngFile.name.endsWith(".png"))
        assertTrue(pngFile.length() > 500L)
        pngFile.delete()
    }

    @Test
    fun `gif encoder appends 1s studio outro frames when enabled`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val starter = StarterProjectFactory.createStarterKatanaProject()
        val plainGif = File(context.cacheDir, "no_outro.gif").apply { delete() }
        val outroGif = File(context.cacheDir, "with_outro.gif").apply { delete() }

        GifEncoder.exportFramesToGif(
            frames = starter.frames,
            fps = 8,
            width = 320,
            height = 200,
            outFile = plainGif,
            outroConfig = StudioOutroConfig(enabled = false)
        ) {}

        GifEncoder.exportFramesToGif(
            frames = starter.frames,
            fps = 8,
            width = 320,
            height = 200,
            outFile = outroGif,
            outroConfig = StudioOutroConfig(enabled = true, creatorText = "Made with Katana Slash Canvas")
        ) {}

        assertTrue(plainGif.exists() && outroGif.exists())
        assertTrue(outroGif.length() > plainGif.length())
        assertEquals("GIF89a", String(outroGif.readBytes(), 0, 6, Charsets.US_ASCII))
        plainGif.delete()
        outroGif.delete()
    }

    @Test
    fun `hex color utilities parse and format opaque neon colors accurately`() {
        assertEquals("#C084FC", formatHexColor(0xFFC084FC))
        assertEquals(0xFFFF00AA, parseHexColor("#FF00AA"))
        assertEquals(0xFF22D3EE, parseHexColor("22d3ee"))
        assertEquals(null, parseHexColor("not_hex"))
    }

    @Test
    fun `neon palette presets and favorite colors persist and sync across studio viewmodels`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = RoninAppContainer(context)
        val starter = StarterProjectFactory.createStarterKatanaProject()
        container.repository.saveProject(starter)

        val animVm = AnimationViewModel(container.repository, starter.id)
        awaitUntil { animVm.uiState.value.project != null && animVm.uiState.value.palettePresets.isNotEmpty() }

        val customNeon = 0xFFFF2AD4L
        animVm.openColorPicker()
        assertTrue(animVm.uiState.value.isColorPickerOpen)
        animVm.selectColor(customNeon)
        animVm.saveFavoriteNeonColor(customNeon)
        awaitUntil { animVm.uiState.value.favoriteNeonColors.contains(customNeon) }

        animVm.selectPalettePreset(DefaultNeonPalettes.PRESET_TOKYO_ARCADE)
        awaitUntil { animVm.uiState.value.activePalettePresetId == DefaultNeonPalettes.PRESET_TOKYO_ARCADE }

        animVm.saveCustomPalettePreset("Katana Plasma", listOf(customNeon, 0xFF22D3EEL))
        awaitUntil { animVm.uiState.value.palettePresets.any { it.name == "Katana Plasma" } }
        animVm.closeColorPicker()
        assertFalse(animVm.uiState.value.isColorPickerOpen)

        // Verify DrawingViewModel immediately observes the persisted favorite color & custom preset
        val drawVm = DrawingViewModel(container.repository, null)
        awaitUntil {
            drawVm.uiState.value.favoriteNeonColors.contains(customNeon) &&
                drawVm.uiState.value.palettePresets.any { it.name == "Katana Plasma" }
        }
        assertTrue(drawVm.uiState.value.favoriteNeonColors.contains(customNeon))

        // Clean up custom color/preset
        animVm.removeFavoriteNeonColor(customNeon)
        awaitUntil { !animVm.uiState.value.favoriteNeonColors.contains(customNeon) }
        val customPresetId = animVm.uiState.value.palettePresets.first { it.name == "Katana Plasma" }.id
        animVm.deleteCustomPalettePreset(customPresetId)
        awaitUntil { animVm.uiState.value.palettePresets.none { it.id == customPresetId } }
    }
}
