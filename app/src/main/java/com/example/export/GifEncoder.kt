package com.example.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.example.data.model.AnimationFrame
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Memory-safe pure-Kotlin GIF89a animated GIF exporter optimized for 4GB RAM devices.
 * Reuses a single RGB_565/ARGB_8888 Bitmap across all frames and quantizes to a 256-color palette.
 */
object GifEncoder {

    fun exportFramesToGif(
        frames: List<AnimationFrame>,
        fps: Int,
        width: Int,
        height: Int,
        outFile: File,
        outroConfig: com.example.data.model.StudioOutroConfig? = null,
        onProgress: (Float) -> Unit
    ): File {
        require(frames.isNotEmpty()) { "No frames to export" }
        val safeWidth = width.coerceIn(160, 640)
        val safeHeight = height.coerceIn(90, 640)
        val safeFps = fps.coerceIn(1, 30)
        val delayCs = (100f / safeFps).toInt().coerceAtLeast(3)
        val outroCount = if (outroConfig?.enabled == true) safeFps else 0
        val totalProgressSteps = frames.size + outroCount

        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pixels = IntArray(safeWidth * safeHeight)
        val indexed = ByteArray(safeWidth * safeHeight)

        // Pre-built 6-7-6 uniform RGB palette (252 entries + 4 studio neon colors = 256)
        val paletteBytes = buildStudioPalette()

        try {
            BufferedOutputStream(FileOutputStream(outFile)).use { out ->
                // Header
                out.write("GIF89a".toByteArray(Charsets.US_ASCII))
                // Logical Screen Descriptor
                writeShort(out, safeWidth)
                writeShort(out, safeHeight)
                out.write(0xF7) // Global Color Table Flag=1, ColorRes=7, Sort=0, Size=7 (256 colors)
                out.write(0x00) // Background color index
                out.write(0x00) // Pixel aspect ratio
                // Global Color Table
                out.write(paletteBytes)

                // Netscape 2.0 Infinite Loop Application Extension
                out.write(0x21)
                out.write(0xFF)
                out.write(11)
                out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
                out.write(3)
                out.write(1)
                writeShort(out, 0) // 0 = loop forever
                out.write(0)

                frames.forEachIndexed { idx, frame ->
                    FrameRenderer.drawFrameToAndroidCanvas(canvas, frame, safeWidth, safeHeight)
                    bitmap.getPixels(pixels, 0, safeWidth, 0, 0, safeWidth, safeHeight)
                    quantizePixels(pixels, indexed)

                    // Graphic Control Extension
                    out.write(0x21)
                    out.write(0xF9)
                    out.write(4)
                    out.write(0x04) // disposal = do not dispose
                    writeShort(out, delayCs * frame.holdFrames.coerceAtLeast(1))
                    out.write(0)
                    out.write(0)

                    // Image Descriptor
                    out.write(0x2C)
                    writeShort(out, 0)
                    writeShort(out, 0)
                    writeShort(out, safeWidth)
                    writeShort(out, safeHeight)
                    out.write(0x00) // No local color table

                    // LZW Image Data
                    writeLzwImageData(out, indexed)
                    onProgress((idx + 1).toFloat() / totalProgressSteps)
                }

                if (outroCount > 0 && outroConfig != null) {
                    for (oIdx in 0 until outroCount) {
                        val p = (oIdx + 1).toFloat() / outroCount.toFloat()
                        OutroCardRenderer.drawOutroFrame(
                            canvas = canvas,
                            creatorText = outroConfig.creatorText,
                            width = safeWidth,
                            height = safeHeight,
                            progress = p
                        )
                        bitmap.getPixels(pixels, 0, safeWidth, 0, 0, safeWidth, safeHeight)
                        quantizePixels(pixels, indexed)

                        out.write(0x21)
                        out.write(0xF9)
                        out.write(4)
                        out.write(0x04)
                        writeShort(out, delayCs)
                        out.write(0)
                        out.write(0)

                        out.write(0x2C)
                        writeShort(out, 0)
                        writeShort(out, 0)
                        writeShort(out, safeWidth)
                        writeShort(out, safeHeight)
                        out.write(0x00)

                        writeLzwImageData(out, indexed)
                        onProgress((frames.size + oIdx + 1).toFloat() / totalProgressSteps)
                    }
                }

                // GIF Trailer
                out.write(0x3B)
                out.flush()
            }
        } finally {
            bitmap.recycle()
        }
        return outFile
    }

    private fun buildStudioPalette(): ByteArray {
        val table = ByteArray(256 * 3)
        var idx = 0
        // 6x7x6 = 252 color cube
        for (r in 0 until 6) {
            for (g in 0 until 7) {
                for (b in 0 until 6) {
                    table[idx++] = (r * 51).toByte()
                    table[idx++] = (g * 42).toByte()
                    table[idx++] = (b * 51).toByte()
                }
            }
        }
        // Extra studio obsidian & neon purple precision slots (252..255)
        val extras = intArrayOf(
            Color.rgb(10, 10, 18),   // 252: Obsidian bg
            Color.rgb(168, 85, 247), // 253: Neon purple
            Color.rgb(34, 211, 238), // 254: Cyber cyan
            Color.rgb(244, 63, 94)   // 255: Onion rose
        )
        for (c in extras) {
            table[idx++] = Color.red(c).toByte()
            table[idx++] = Color.green(c).toByte()
            table[idx++] = Color.blue(c).toByte()
        }
        return table
    }

    private fun quantizePixels(pixels: IntArray, outIndexed: ByteArray) {
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (r < 16 && g < 16 && b < 24) {
                outIndexed[i] = 252.toByte()
            } else {
                val ri = (r * 5 + 127) / 255
                val gi = (g * 6 + 127) / 255
                val bi = (b * 5 + 127) / 255
                outIndexed[i] = (ri * 42 + gi * 6 + bi).toByte()
            }
        }
    }

    private fun writeLzwImageData(out: OutputStream, indexed: ByteArray) {
        val minCodeSize = 8
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize // 256
        val eofCode = clearCode + 1         // 257

        val bitStream = GifBitSubBlockWriter(out)
        var codeSize = minCodeSize + 1
        var nextCode = eofCode + 1

        val dict = HashMap<Int, Int>(4096)

        bitStream.writeBits(clearCode, codeSize)
        var prefix = indexed[0].toInt() and 0xFF

        for (i in 1 until indexed.size) {
            val k = indexed[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val existing = dict[key]
            if (existing != null) {
                prefix = existing
            } else {
                bitStream.writeBits(prefix, codeSize)
                if (nextCode < 4096) {
                    dict[key] = nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    bitStream.writeBits(clearCode, codeSize)
                    dict.clear()
                    codeSize = minCodeSize + 1
                    nextCode = eofCode + 1
                }
                prefix = k
            }
        }
        bitStream.writeBits(prefix, codeSize)
        bitStream.writeBits(eofCode, codeSize)
        bitStream.finish()
        out.write(0) // Block terminator
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private class GifBitSubBlockWriter(private val out: OutputStream) {
        private val block = ByteArray(255)
        private var blockLen = 0
        private var curAccum = 0
        private var curBits = 0

        fun writeBits(code: Int, bits: Int) {
            curAccum = curAccum or (code shl curBits)
            curBits += bits
            while (curBits >= 8) {
                addByte((curAccum and 0xFF).toByte())
                curAccum = curAccum ushr 8
                curBits -= 8
            }
        }

        fun finish() {
            if (curBits > 0) {
                addByte((curAccum and 0xFF).toByte())
                curAccum = 0
                curBits = 0
            }
            flushBlock()
        }

        private fun addByte(b: Byte) {
            block[blockLen++] = b
            if (blockLen >= 254) {
                flushBlock()
            }
        }

        private fun flushBlock() {
            if (blockLen > 0) {
                out.write(blockLen)
                out.write(block, 0, blockLen)
                blockLen = 0
            }
        }
    }
}
