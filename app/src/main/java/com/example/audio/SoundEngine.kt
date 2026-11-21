package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Native Android Audio Engine for RONIN STUDIO Sound Studio:
 * - Synthesizes anime sound effects in real time via AudioTrack (no external files needed).
 * - Records voiceovers using MediaRecorder to app storage.
 * - Plays back recorded voice clips using MediaPlayer.
 */
class SoundEngine(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var activeRecordingFile: File? = null

    fun startRecording(outFile: File): Boolean {
        stopRecording()
        return runCatching {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(outFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            activeRecordingFile = outFile
            true
        }.getOrElse {
            runCatching { mediaRecorder?.release() }
            mediaRecorder = null
            false
        }
    }

    fun stopRecording(): File? {
        val file = activeRecordingFile
        runCatching {
            mediaRecorder?.stop()
        }
        runCatching {
            mediaRecorder?.release()
        }
        mediaRecorder = null
        activeRecordingFile = null
        return if (file != null && file.exists() && file.length() > 0L) file else null
    }

    fun playAudioFile(filePath: String, volume: Float = 0.9f, onComplete: () -> Unit = {}) {
        stopPlayback()
        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                setVolume(volume, volume)
                setOnCompletionListener {
                    stopPlayback()
                    onComplete()
                }
                prepare()
                start()
            }
            mediaPlayer = player
        }.onFailure {
            onComplete()
        }
    }

    fun stopPlayback() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    suspend fun playAnimeSfx(presetId: String, volume: Float = 0.85f) = withContext(Dispatchers.Default) {
        val sampleRate = 22050
        val durationSec = when (presetId) {
            "katana_slash" -> 0.45
            "bass_impact" -> 0.65
            "spirit_charge" -> 0.80
            else -> 0.18
        }
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val rng = Random(42)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = when (presetId) {
                "katana_slash" -> exp(-t * 8.0) * sin(PI * (t / durationSec))
                "bass_impact" -> exp(-t * 5.5)
                "spirit_charge" -> (t / durationSec) * exp(-(t - durationSec * 0.7) * (t - durationSec * 0.7) * 18.0)
                else -> exp(-t * 25.0)
            }
            val wave = when (presetId) {
                "katana_slash" -> {
                    val sweepFreq = 1600.0 - (t / durationSec) * 1150.0
                    val metallic = sin(2.0 * PI * sweepFreq * t) * 0.55 +
                        sin(2.0 * PI * (sweepFreq * 1.49) * t) * 0.25
                    val whoosh = (rng.nextDouble() * 2.0 - 1.0) * 0.45
                    (metallic + whoosh) * env
                }
                "bass_impact" -> {
                    val freq = 140.0 * exp(-t * 4.5) + 42.0
                    sin(2.0 * PI * freq * t) * env
                }
                "spirit_charge" -> {
                    val freq = 280.0 + (t / durationSec) * 680.0
                    (sin(2.0 * PI * freq * t) * 0.6 + sin(2.0 * PI * freq * 2.0 * t) * 0.4) * env
                }
                else -> sin(2.0 * PI * 980.0 * t) * env
            }
            samples[i] = (wave * volume * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        runCatching {
            track.write(samples, 0, samples.size)
            track.play()
            kotlinx.coroutines.delay((durationSec * 1000).toLong() + 60L)
        }
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }
}
