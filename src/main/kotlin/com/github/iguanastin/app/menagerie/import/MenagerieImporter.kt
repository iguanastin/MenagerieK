package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.model.Menagerie
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

    val onError: MutableSet<(Exception) -> Unit> = mutableSetOf()
    val onQueued: MutableSet<(ImportJob) -> Unit> = mutableSetOf()
    val beforeEach: MutableSet<(ImportJob) -> Unit> = mutableSetOf()
    val afterEach: MutableSet<(ImportJob) -> Unit> = mutableSetOf()


    init {
        log.info("Starting Menagerie importer thread")
        thread(start = true, name = "Menagerie Importer") {
            importThreadRunning = true
            while (importThreadRunning) {
                val job = importQueue.poll(3, TimeUnit.SECONDS)
                if (!importThreadRunning) break
                if (job == null || job.isCancelled) continue

                try {
                    log.info("Importing: ${job.file}")
                    beforeEach.forEach { it(job) }
                    job.onStart.forEach { it(job) }
                    val item = job.import(menagerie)
                    menagerie.addItem(item)
                    job.onFinish.forEach { it(item) }
                    afterEach.forEach { it(job) }
                    log.info("Successfully imported: ${job.item}")
                } catch (e: Exception) {
                    job.cleanupAfterError(e)
                    job.onError.forEach { it(e) }
                    onError.forEach { it(e) }
                }
            }

            log.info("Importer thread finished")
        }
    }

    fun enqueue(job: ImportJob) {
        importQueue.put(job)
        onQueued.forEach { it(job) }
    }

    fun close() {
        importThreadRunning = false
    }

}