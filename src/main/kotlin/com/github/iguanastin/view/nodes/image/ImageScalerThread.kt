package com.github.iguanastin.view.nodes.image

import com.mortennobel.imagescaling.AdvancedResizeOp
import com.mortennobel.imagescaling.ResampleFilters
import com.mortennobel.imagescaling.ResampleOp
import javafx.embed.swing.SwingFXUtils
import mu.KotlinLogging
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

class ImageScalerThread : Thread() {

    @Volatile
    private var running = false

    private val queue: BlockingQueue<ImageScaleJob> = LinkedBlockingQueue()


    override fun run() {
        running = true

        while (running) {
            val job = queue.poll(1, TimeUnit.SECONDS)
            if (!running) break
            if (job == null) continue

            try {
                val bimg = SwingFXUtils.fromFXImage(job.source, null)

                val resizeOp = ResampleOp((bimg.width / job.targetScale + 0.5).toInt(), (bimg.height / job.targetScale + 0.5).toInt())
                resizeOp.unsharpenMask = AdvancedResizeOp.UnsharpenMask.Normal
                resizeOp.filter = ResampleFilters.getLanczos3Filter()
                val scaledImage = resizeOp.filter(bimg, bimg)

                job.onSuccess(SwingFXUtils.toFXImage(scaledImage, null))
            } catch (e: Throwable) {
                log.error("Failed to scale image", e)
                job.onError(e)
            }
        }
    }

    fun enqueue(job: ImageScaleJob) {
        queue.clear()
        queue.put(job)
    }

    fun close() {
        running = false
    }

}