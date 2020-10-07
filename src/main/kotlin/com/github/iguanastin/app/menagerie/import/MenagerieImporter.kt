package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.Menagerie
import mu.KotlinLogging
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}
class MenagerieImporter(val menagerie: Menagerie) {

    @Volatile
    private var importThreadRunning = false
    private val importQueue: BlockingQueue<ImportJob> = LinkedBlockingQueue()

    val onImport: MutableSet<(Item) -> Unit> = mutableSetOf()
    val onError: MutableSet<(Exception) -> Unit> = mutableSetOf()


    init {
        log.info("Starting Menagerie importer thread")
        thread(start = true, name = "Menagerie Importer") {
            importThreadRunning = true
            while (importThreadRunning) {
                val job = importQueue.poll(3, TimeUnit.SECONDS)
                if (!importThreadRunning) break
                if (job == null) continue

                try {
                    val item = job.import(menagerie)
                    onImport.forEach { it(item) }
                } catch (e: Exception) {
                    onError.forEach { it(e) }
                }
            }

            log.info("Importer thread finished")
        }
    }

    fun enqueue(job: ImportJob) {
        importQueue.put(job)
    }

    fun close() {
        importThreadRunning = false
    }

}