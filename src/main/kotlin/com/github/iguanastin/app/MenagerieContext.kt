package com.github.iguanastin.app

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.import.MenagerieImporter
import com.github.iguanastin.app.menagerie.model.Menagerie
import kotlin.concurrent.thread

class MenagerieContext(val menagerie: Menagerie, val importer: MenagerieImporter, val database: MenagerieDatabase) {

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