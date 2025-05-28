package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.model.Menagerie
import mu.KotlinLogging
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

class Importer(val menagerie: Menagerie) : Thread("File Importer") {

    private val queue: BlockingQueue<Import> = LinkedBlockingQueue()

    @Volatile
    private var running = false


    init {
        start()
    }

    override fun run() {
        running = true
        while (running) {
            var job: Import? = null
            try {
                job = queue.poll(3, TimeUnit.SECONDS)
                if (!running) break
                if (job?.status != Import.Status.READY) continue

                job.start(menagerie)
                job.join()
                // TODO update menagerie to remove the import job
            } catch (e: InterruptedException) {
                // Should only be interrupted by closing the importer
                job?.cancel()
                log.info { "Importer interrupted" }
            }
        }
    }

    fun add(job: Import) {
        queue.add(job)
    }

    fun close() {
        running = false
        interrupt()
    }

}