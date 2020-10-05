package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.Menagerie
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MenagerieImporter(val menagerie: Menagerie) {

    @Volatile
    private var importThreadRunning = false
    private val importQueue: BlockingQueue<ImportJob> = LinkedBlockingQueue()
    val onImport: MutableSet<(Item) -> Unit> = mutableSetOf()


    init {
        thread(start = true, isDaemon = true, name = "Menagerie Importer") {
            importThreadRunning = true
            while (importThreadRunning) {
                val job = importQueue.poll(3, TimeUnit.SECONDS)
                if (!importThreadRunning) break
                if (job == null) continue

                try {
                    val item = job.import(menagerie)
                    onImport.forEach { it(item) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    TODO("Handle this better")
                }
            }
        }
    }

    fun close() {
        importThreadRunning = false
    }

}