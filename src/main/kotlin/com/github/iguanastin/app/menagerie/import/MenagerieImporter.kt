package com.github.iguanastin.app.menagerie.import

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MenagerieImporter {

    @Volatile
    private var importThreadRunning = false
    private val importQueue: BlockingQueue<ImportJob> = LinkedBlockingQueue()


    init {
        thread(start = true, isDaemon = true, name = "Menagerie Importer") {
            importThreadRunning = true
            while (importThreadRunning) {
                val job = importQueue.poll(1, TimeUnit.SECONDS)
                if (!importThreadRunning) break
                if (job == null) continue

                // TODO
            }
        }
    }

}