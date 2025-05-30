package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.model.Menagerie
import com.github.iguanastin.app.menagerie.model.Tag
import javafx.collections.ListChangeListener
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

class Importer(val menagerie: Menagerie) : Thread("File Importer") {

    private val queue: BlockingQueue<Import> = LinkedBlockingQueue()

    @Volatile
    private var running = false

    @Volatile
    var paused = false

    @Suppress("RemoveExplicitTypeArguments")
    private val menagerieImportsListener = ListChangeListener<Import> { change ->
        while (change.next()) {
            change.addedSubList.forEach { i ->
                queue.add(i)
            }
        }
    }

    init {
        start()

        menagerie.imports.addListener(menagerieImportsListener)
        menagerie.imports.forEach {
            queue.add(it)
        }
    }

    override fun run() {
        running = true
        while (running) {
            var job: Import? = null
            try {
                job = queue.poll(3, TimeUnit.SECONDS)
                if (!running) break
                while (paused) {
                    sleep(3000)
                }
                if (job == null) continue

                if (job.status.value == Import.Status.READY) {
                    job.start(menagerie)
                    job.join()
                }

                menagerie.removeImport(job)
            } catch (e: InterruptedException) {
                // Should only be interrupted by closing the importer
                job?.cancel()
                log.info { "Importer interrupted" }
            }
        }
        menagerie.imports.removeListener(menagerieImportsListener)
    }

    fun fromWeb(url: String, directory: File, tags: List<Tag>? = null): Import {
        return Import.fromWeb(menagerie.reserveImportID(), url, directory, tags).also {
            menagerie.addImport(it)
        }
    }

    fun fromLocal(file: File, group: ImportGroup? = null, tags: List<Tag>? = null): Import {
        return Import.fromLocal(menagerie.reserveImportID(), file, group, tags).also {
            menagerie.addImport(it)
        }
    }

    fun createGroup(title: String): ImportGroup {
        return ImportGroup(title, menagerie.reserveImportTempGroupID())
    }

    fun close() {
        menagerie.imports.removeListener(menagerieImportsListener)
        running = false
        interrupt()
    }

}