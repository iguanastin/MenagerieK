package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.model.Menagerie
import javafx.collections.ListChangeListener
import mu.KotlinLogging
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

class Importer(val menagerie: Menagerie) : Thread("File Importer") {

    private val queue: BlockingQueue<Import> = LinkedBlockingQueue()

    @Volatile
    private var running = false


    private val menagerieImportsListener = ListChangeListener<Import> { change ->
        while (change.next()) {
            change.addedSubList.forEach { i -> add(i) }
        }
    }

    init {
        start()

        menagerie.imports.forEach { add(it) }
        menagerie.imports.addListener(menagerieImportsListener)
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

                menagerie.imports.remove(job)
            } catch (e: InterruptedException) {
                // Should only be interrupted by closing the importer
                job?.cancel()
                log.info { "Importer interrupted" }
            }
        }
        menagerie.imports.removeListener(menagerieImportsListener)
    }

    fun add(job: Import) {
        queue.add(job)
    }

    fun close() {
        menagerie.imports.removeListener(menagerieImportsListener)
        running = false
        interrupt()
    }

}