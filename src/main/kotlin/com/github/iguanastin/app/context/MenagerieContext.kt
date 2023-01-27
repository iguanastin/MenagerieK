package com.github.iguanastin.app.context

import com.github.iguanastin.app.menagerie.api.MenagerieAPI
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.import.MenagerieImporter
import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Menagerie
import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.app.settings.AppSettings
import javafx.beans.value.ChangeListener
import java.util.*
import kotlin.concurrent.thread

class MenagerieContext(val menagerie: Menagerie, val importer: MenagerieImporter, val database: MenagerieDatabase, val prefs: AppSettings) {

    val edits: Stack<Edit> = Stack()

    val api = MenagerieAPI(this, prefs.api.pageSize.value)
    private val apiPageSizeListener = ChangeListener { _, _, new ->
        api.pageSize = new
    }

    init {
        prefs.api.pageSize.property.addListener(apiPageSizeListener)
    }

    fun close() {
        try {
            importer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        prefs.api.pageSize.property.removeListener(apiPageSizeListener)
        api.stop()

        thread(start = true, name = "Database Shutdown") {
            database.closeAndCompress()
        }
    }

    fun undoLastEdit(): Edit {
        return edits.pop().apply { undo() }
    }

    fun tagEdit(items: List<Item>, add: List<Tag> = emptyList(), remove: List<Tag> = emptyList()): TagEdit {
        return TagEdit(items, add, remove).apply {
            if (perform()) edits.push(this)
        }
    }

    fun groupRenameEdit(group: GroupItem, newName: String): GroupRenameEdit {
        return GroupRenameEdit(group, newName).apply {
            if (perform()) edits.push(this)
        }
    }

    fun tagColorEdit(tag: Tag, newColor: String?): TagColorEdit {
        return TagColorEdit(tag, newColor).apply {
            if (perform()) edits.push(this)
        }
    }

}