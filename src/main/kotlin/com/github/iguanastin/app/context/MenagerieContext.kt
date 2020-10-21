package com.github.iguanastin.app.context

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.import.MenagerieImporter
import com.github.iguanastin.app.menagerie.model.Menagerie
import java.util.*
import kotlin.concurrent.thread

class MenagerieContext(val menagerie: Menagerie, val importer: MenagerieImporter, val database: MenagerieDatabase) {

    val tagEdits: Stack<TagEdit> = Stack()

    fun close() {
        try {
            importer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        thread(start = true, name = "Database Shutdown") {
            database.closeAndCompress()
        }
    }

}