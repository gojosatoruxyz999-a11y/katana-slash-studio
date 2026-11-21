package com.example.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.example.data.model.AnimationFrame
import java.io.File

/**
 * Hardware-surface H.264 MP4 exporter using Android MediaCodec + MediaMuxer.
 * Uses zero intermediate frame bitmaps, keeping RAM footprint minimal for 4GB devices.
 */
object Mp4Exporter {

    fun exportFramesToMp4(
        frames: List<AnimationFrame>,
        fps: Int,
        width: Int,
        height: Int,
        outFile: File,
        outroConfig: com.example.data.model.StudioOutroConfig? = null,
        onProgress: (Float) -> Unit
    ): File {
        require(frames.isNotEmpty()) { "No frames to export" }
        // Even dimensions required by AVC encoders
        val encWidth = (width / 2) * 2
        val encHeight = (height / 2) * 2
        val safeFps = fps.coerceIn(1, 30)
        val outroCount = if (outroConfig?.enabled == true) safeFps else 0
        val totalProgressSteps = frames.size + outroCount

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encWidth, encHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, safeFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        var encoder: MediaCodec? = null
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / safeFps
            var totalTicks = 0L

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        outIdx >= 0 -> {
                            val encodedData = encoder.getOutputBuffer(outIdx)
                            if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && muxerStarted) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                bufferInfo.presentationTimeUs = totalTicks * frameDurationUs
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }

            frames.forEachIndexed { idx, frame ->
                val repeats = frame.holdFrames.coerceAtLeast(1)
                repeat(repeats) {
                    val canvas = inputSurface.lockCanvas(null)
                    try {
                        FrameRenderer.drawFrameToAndroidCanvas(canvas, frame, encWidth, encHeight)
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }
                    totalTicks++
                    drainEncoder(false)
                }
                onProgress((idx + 1).toFloat() / totalProgressSteps)
            }

            if (outroCount > 0 && outroConfig != null) {
                for (oIdx in 0 until outroCount) {
                    val p = (oIdx + 1).toFloat() / outroCount.toFloat()
                    val canvas = inputSurface.lockCanvas(null)
                    try {
                        OutroCardRenderer.drawOutroFrame(
                            canvas = canvas,
                            creatorText = outroConfig.creatorText,
                            width = encWidth,
                            height = encHeight,
                            progress = p
                        )
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }
                    totalTicks++
                    drainEncoder(false)
                    onProgress((frames.size + oIdx + 1).toFloat() / totalProgressSteps)
                }
            }

            drainEncoder(true)
        } finally {
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { inputSurface?.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
        }

        return outFile
    }
}
