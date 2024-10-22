package com.github.iguanastin.app.menagerie.model

import com.github.iguanastin.view.blockUntilLoaded
import javafx.scene.image.Image
import javafx.scene.image.PixelFormat
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.IntBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt


class Histogram private constructor(
        val alpha: DoubleArray = DoubleArray(BIN_SIZE),
        val red: DoubleArray = DoubleArray(BIN_SIZE),
        val green: DoubleArray = DoubleArray(BIN_SIZE),
        val blue: DoubleArray = DoubleArray(BIN_SIZE)) {

    private var _colorful: Boolean? = null
        get() {
            if (field == null) {
                var d = 0.0

                for (i in 0 until BIN_SIZE) {
                    d += max(max(red[i], green[i]), blue[i]) - min(min(red[i], green[i]), blue[i])
                }

                field = d > BLACK_AND_WHITE_CONFIDENCE
            }
            return field
        }
    val isColorful: Boolean
        get() = _colorful!!


    companion object {
        const val BIN_SIZE = 32
        const val NUM_CHANNELS = 4
        const val BLACK_AND_WHITE_CONFIDENCE = 0.25

        fun channelToInputStream(channel: DoubleArray): ByteArrayInputStream {
            val bb = ByteBuffer.wrap(ByteArray(BIN_SIZE * 8))
            for (d in channel) {
                bb.putDouble(d)
            }
            return ByteArrayInputStream(bb.array())
        }

        private fun inputStreamToChannel(stream: InputStream, output: DoubleArray = DoubleArray(BIN_SIZE)): DoubleArray {
            val b = ByteArray(BIN_SIZE * 8)
            if (stream.read(b) != BIN_SIZE * 8) throw HistogramReadException("Mismatched stream length")
            val bb = ByteBuffer.wrap(b)
            for (i in 0 until BIN_SIZE) {
                output[i] = bb.double
            }
            return output
        }

        fun from(a: InputStream, r: InputStream, g: InputStream, b: InputStream): Histogram? {
            val hist = Histogram()

            try {
                inputStreamToChannel(a, hist.alpha)
                inputStreamToChannel(r, hist.red)
                inputStreamToChannel(g, hist.green)
                inputStreamToChannel(b, hist.blue)
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }

            return hist
        }

        fun from(image: Image): Histogram? {
            val hist = Histogram()

            image.blockUntilLoaded()
            val pixelReader = image.pixelReader ?: return null

            val bufferRows = 10 // Buffer n rows at a time
            val width = image.width.toInt()
            val buffer = IntBuffer.wrap(IntArray(width * bufferRows))
            val format = PixelFormat.getIntArgbInstance()
            for (y in 0 until image.height.toInt() / bufferRows) {
                pixelReader.getPixels(0, y * bufferRows, width, bufferRows, format, buffer, width)
                while (buffer.hasRemaining()) {
                    val color = buffer.get()
                    val a = 0xff and (color shr 24)
                    val r = 0xff and (color shr 16)
                    val g = 0xff and (color shr 8)
                    val b = 0xff and color

                    hist.alpha[a / (256 / BIN_SIZE)]++
                    hist.red[r / (256 / BIN_SIZE)]++
                    hist.green[g / (256 / BIN_SIZE)]++
                    hist.blue[b / (256 / BIN_SIZE)]++
                }
                buffer.clear()
            }

            val pixelCount = (image.width * image.height).toLong()
            for (i in 0 until BIN_SIZE) {
                hist.alpha[i] = hist.alpha[i] / pixelCount
                hist.red[i] = hist.red[i] / pixelCount
                hist.green[i] = hist.green[i] / pixelCount
                hist.blue[i] = hist.blue[i] / pixelCount
            }

            return hist
        }
    }


    fun alphaToInputStream(): ByteArrayInputStream? {
        return channelToInputStream(alpha)
    }

    fun redToInputStream(): ByteArrayInputStream? {
        return channelToInputStream(red)
    }

    fun greenToInputStream(): ByteArrayInputStream? {
        return channelToInputStream(green)
    }

    fun blueToInputStream(): ByteArrayInputStream? {
        return channelToInputStream(blue)
    }

    fun similarityTo(other: Histogram): Double {
        var da = 0.0
        var dr = 0.0
        var dg = 0.0
        var db = 0.0
        for (i in 0 until BIN_SIZE) {
            da += abs(alpha[i] - other.alpha[i])
            dr += abs(red[i] - other.red[i])
            dg += abs(green[i] - other.green[i])
            db += abs(blue[i] - other.blue[i])
        }

        var error = (da + dr + dg + db) / 8

        if (!isColorful || !other.isColorful) {
            error = sqrt(error)
        }

        return 1 - error
    }


}