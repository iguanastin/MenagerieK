package com.github.iguanastin.app.menagerie

import javafx.scene.image.Image
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class Histogram {

    companion object {
        const val BIN_SIZE = 32
        const val NUM_CHANNELS = 4
        const val BLACK_AND_WHITE_CONFIDENCE = 0.25

        fun channelToInputStream(channel: DoubleArray): ByteArrayInputStream? {
            val bb = ByteBuffer.wrap(ByteArray(BIN_SIZE * 8))
            for (d in channel) {
                bb.putDouble(d)
            }
            return ByteArrayInputStream(bb.array())
        }

        fun inputStreamToChannel(stream: InputStream): DoubleArray {
            val b = ByteArray(BIN_SIZE * 8)
            if (stream.read(b) != BIN_SIZE * 8) throw HistogramReadException("Mismatched stream length")
            val bb = ByteBuffer.wrap(b)
            val result = DoubleArray(BIN_SIZE)
            for (i in 0 until BIN_SIZE) {
                result[i] = bb.double
            }
            return result
        }
    }


    private val alpha: DoubleArray
    private val red: DoubleArray
    private val green: DoubleArray
    private val blue: DoubleArray

    var isColorful: Boolean? = null
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
        private set


    constructor(a: InputStream, r: InputStream, g: InputStream, b: InputStream) {
        try {
            alpha = inputStreamToChannel(a)
            red = inputStreamToChannel(r)
            green = inputStreamToChannel(g)
            blue = inputStreamToChannel(b)
        } catch (e: IOException) {
            throw HistogramReadException("InputStream of invalid length")
        }
    }

    constructor(image: Image) {
        alpha = DoubleArray(BIN_SIZE)
        red = DoubleArray(BIN_SIZE)
        green = DoubleArray(BIN_SIZE)
        blue = DoubleArray(BIN_SIZE)

        if (image.isBackgroundLoading && image.progress != 1.0) throw HistogramReadException("Given media is not loaded yet")
        val pixelReader = image.pixelReader ?: throw HistogramReadException("Unable to get PixelReader")

        for (y in 0 until image.height.toInt()) {
            for (x in 0 until image.width.toInt()) {
                val color = pixelReader.getArgb(x, y)
                val a = 0xff and (color shr 24)
                val r = 0xff and (color shr 16)
                val g = 0xff and (color shr 8)
                val b = 0xff and color

                alpha[a / (256 / BIN_SIZE)]++
                red[r / (256 / BIN_SIZE)]++
                green[g / (256 / BIN_SIZE)]++
                blue[b / (256 / BIN_SIZE)]++
            }
        }

        val pixelCount = (image.width * image.height).toLong()
        for (i in 0 until BIN_SIZE) {
            alpha[i] = alpha[i] / pixelCount
            red[i] = red[i] / pixelCount
            green[i] = green[i] / pixelCount
            blue[i] = blue[i] / pixelCount
        }
    }


    fun alphaToInputStream(): ByteArrayInputStream? { return channelToInputStream(alpha) }

    fun redToInputStream(): ByteArrayInputStream? { return channelToInputStream(red) }

    fun greenToInputStream(): ByteArrayInputStream? { return channelToInputStream(green) }

    fun blueToInputStream(): ByteArrayInputStream? { return channelToInputStream(blue) }

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
        return 1 - (da + dr + dg + db) / 8
    }


}