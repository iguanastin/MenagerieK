package com.github.iguanastin.app.context

import com.github.iguanastin.app.menagerie.api.MenagerieAPI
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.import.MenagerieImporter
import com.github.iguanastin.app.menagerie.model.Menagerie
import java.util.*
import java.util.prefs.Preferences
import kotlin.concurrent.thread

class MenagerieContext(val menagerie: Menagerie, val importer: MenagerieImporter, val database: MenagerieDatabase, val prefs: Preferences) {

    val tagEdits: Stack<TagEdit> = Stack()

    val api = MenagerieAPI(this, 100)

    fun close() {
        try {
            importer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        api.stop()

        thread(start = true, name = "Database Shutdown") {
            database.closeAndCompress()
        }
    }

}