package com.github.iguanastin.app

import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import com.github.iguanastin.app.menagerie.import.ImportJob
import com.github.iguanastin.app.menagerie.import.ImportJobIntoGroup
import com.github.iguanastin.app.menagerie.import.MenagerieImporter
import com.github.iguanastin.app.menagerie.import.RemoteImportJob
import com.github.iguanastin.app.menagerie.model.*
import com.github.iguanastin.app.menagerie.view.ElementOfFilter
import com.github.iguanastin.app.menagerie.view.MenagerieView
import com.github.iguanastin.view.MainView
import com.github.iguanastin.view.dialog.*
import com.github.iguanastin.view.runOnUIThread
import javafx.event.EventHandler
import javafx.scene.control.ButtonType
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.TransferMode
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Screen
import javafx.stage.Stage
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.io.IOException
import java.util.prefs.Preferences
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

class MyApp : App(MainView::class, Styles::class) {

    private val uiPrefs: Preferences = Preferences.userRoot().node("com/github/iguanastin/MenagerieK/ui")
    private val contextPrefs: Preferences = Preferences.userRoot().node("com/github/iguanastin/MenagerieK/context")

    private val dbURL = contextPrefs.get("db_url", "~/test-sfw-v9")

    //    private val dbURL = contextPrefs.get("db_url", "~/menagerie-test")
    private val dbUser = contextPrefs.get("db_user", "sa")
    private val dbPass = contextPrefs.get("db_pass", "")

    private lateinit var manager: MenagerieDatabase
    private lateinit var menagerie: Menagerie
    private lateinit var importer: MenagerieImporter

    private lateinit var root: MainView


    override fun start(stage: Stage) {
        super.start(stage)
        root = find(primaryView) as MainView

        log.info("Starting app")

        initMainStageProperties(stage)

        loadMenagerie(stage) { manager, menagerie, importer ->
            manager.updateErrorHandlers.add { e ->
                // TODO show error to user
                log.error("Error occurred while updating database", e)
            }
            importer.onError.add { e ->
                // TODO show error to user
                log.error("Error occurred while importing", e)
            }
            importer.onQueued.add { job ->
                runOnUIThread {
                    root.imports.add(ImportNotification(job))
                    root.imports.sortBy { it.isFinished }
                }
            }

            purgeZombieTags(menagerie)

            runOnUIThread {
                root.navigateForward(MenagerieView(menagerie, "", true, false, listOf(ElementOfFilter(null, true))))
            }

            initViewControls()
        }
    }

    private fun purgeZombieTags(menagerie: Menagerie) {
        val toRemove = mutableSetOf<Tag>()
        for (tag in menagerie.tags) {
            if (tag.frequency == 0) {
                toRemove.add(tag)
            }
        }
        toRemove.forEach {
            log.info("Purging zombie tag: $it")
            menagerie.removeTag(it)
        }
    }

    private fun initViewControls() {
        root.root.onDragOver = EventHandler { event ->
            if (event.gestureSource == null && (event.dragboard.hasFiles() || event.dragboard.hasUrl())) {
                root.dragOverlay.show()
                event.apply {
                    acceptTransferModes(*TransferMode.ANY)
                    consume()
                }
            }
        }
        root.root.onDragDropped = EventHandler { event ->
            if (event.isAccepted) {
                if (event.dragboard.url?.startsWith("http") == true) {
                    importer.enqueue(RemoteImportJob.intoDirectory(event.dragboard.url, File("D:\\Downloads"))) // Better downloads folder
                }
                if (event.dragboard.files?.isNotEmpty() == true) {
                    importFilesDialog(event.dragboard.files)
                }

                event.apply {
                    isDropCompleted = true
                    consume()
                }
            }
        }
        root.root.onDragExited = EventHandler {
            root.dragOverlay.hide()
        }

        root.itemGrid.addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.I && event.isShortcutDown) {
                importShortcut(event.isShiftDown)
                event.consume()
            }
            if (event.code == KeyCode.DELETE) {
                deleteShortcut(event.isShortcutDown)
                event.consume()
            }
            if (event.code == KeyCode.R && event.isShortcutDown && !event.isShiftDown) {
                renameGroupShortcut()
                event.consume()
            }
        }
    }

    private fun renameGroupShortcut() {
        if (root.itemGrid.selected.size == 1 && root.itemGrid.selected.first() is GroupItem) {
            val group = root.itemGrid.selected.first() as GroupItem

            root.root.add(TextInputDialog(header = "Rename group", text = group.title, onAccept = {
                group.title = it
            }))
        }
    }

    private fun deleteShortcut(shortcutDown: Boolean) {
        val del: List<Item> = mutableListOf<Item>().apply { addAll(root.itemGrid.selected) }
        if (del.isNotEmpty()) {
            if (!shortcutDown) {
                root.root.confirm("Delete items?", "Delete items and their files?\nWARNING: Deletes files!") {
                    onConfirm = {
                        deleteFiles(del)
                    }
                }
            } else {
                root.root.confirm("Drop items?", "Drop these items from the database?\n(Does not delete files)") {
                    onConfirm = {
                        deleteItems(del)
                    }
                }
            }
        }
    }

    private fun importShortcut(shiftDown: Boolean) {
        runOnUIThread {
            if (shiftDown) {
                val dc = DirectoryChooser()
                val dir = contextPrefs.get("import_last_dir", null)
                if (dir != null) dc.initialDirectory = File(dir)
                dc.title = "Import directory"
                val folder = dc.showDialog(root.currentWindow)
                if (folder != null) {
                    contextPrefs.put("import_last_dir", folder.parent)
                    importFilesDialog(listOf(folder))
                }
            } else {
                val fc = FileChooser()
                val dir = contextPrefs.get("import_last_dir", null)
                if (dir != null) fc.initialDirectory = File(dir)
                fc.title = "Import files"
                val files = fc.showOpenMultipleDialog(root.currentWindow)
                if (!files.isNullOrEmpty()) {
                    contextPrefs.put("import_last_dir", files.first().parent)
                    importFilesDialog(files)
                }
            }
        }
    }

    private fun importFilesDialog(files: List<File>) {
        root.root.importdialog(files) {
            individually = {
                files.forEach { file ->
                    if (file.isDirectory) {
                        recursiveFiles(file).forEach { if (!menagerie.hasFile(it)) importer.enqueue(ImportJob(it)) }
                    } else {
                        importer.enqueue(ImportJob(file))
                    }
                }
            }
            asGroup = {
                val jobs = mutableListOf<ImportJob>()
                files.forEach { file ->
                    if (file.isDirectory) {
                        recursiveFiles(file).forEach { if (!menagerie.hasFile(it)) jobs.add(ImportJob(it)) }
                    } else {
                        if (!menagerie.hasFile(file)) jobs.add(ImportJob(file))
                    }
                }

                var title = "Unnamed Group"
                if (files.size == 1 && files.first().isDirectory) {
                    title = files.first().name
                }

                ImportJobIntoGroup.asGroup(jobs, title).forEach { importer.enqueue(it) }
            }
            dirsAsGroups = {
                for (file in files) {
                    if (file.isDirectory) {
                        val jobs = mutableListOf<ImportJob>()
                        recursiveFiles(file).forEach { if (!menagerie.hasFile(it)) jobs.add(ImportJob(it)) }
                        ImportJobIntoGroup.asGroup(jobs, file.name).forEach { importer.enqueue(it) }
                    } else if (!menagerie.hasFile(file)) {
                        importer.enqueue(ImportJob(file))
                    }
                }
            }
            if (!scene.window.isFocused) scene.window.requestFocus()
        }
    }

    private fun recursiveFiles(folder: File): List<File> {
        if (folder.isDirectory) {
            val result = mutableListOf<File>()
            folder.listFiles()?.sortedWith(WindowsExplorerComparator())!!.forEach {
                if (it.isDirectory) {
                    result.addAll(recursiveFiles(it))
                } else {
                    result.add(it)
                }
            }
            return result
        } else {
            return listOf(folder)
        }
    }

    private fun deleteItems(del: List<Item>) {
        del.forEach { item ->
            log.info("Removing item: $item")
            menagerie.removeItem(item)
            if (item is GroupItem) item.items.forEach {
                log.info("Removing item: $it")
                menagerie.removeItem(it)
            }
        }
    }

    private fun deleteFiles(del: List<Item>) {
        deleteItems(del)

        del.forEach {
            if (it is FileItem) {
                try {
                    log.info("Deleting file: ${it.file}")
                    it.file.delete()
                } catch (e: IOException) {
                    log.error("Exception while deleting file: ${it.file}", e)
                }
            } else if (it is GroupItem) {
                for (item in it.items) {
                    if (item is FileItem) {
                        try {
                            log.info("Deleting file: ${item.file}")
                            item.file.delete()
                        } catch (e: IOException) {
                            log.error("Exception while deleting file: ${item.file}", e)
                        }
                    }
                }
            }
        }
    }

    override fun stop() {
        super.stop()

        log.info("Stopping app")

        try {
            importer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        thread(start = true) {
            manager.closeAndCompress()
        }
    }

    private fun loadMenagerie(stage: Stage, after: ((manager: MenagerieDatabase, menagerie: Menagerie, importer: MenagerieImporter) -> Unit)? = null) {
        try {
            manager = MenagerieDatabase(dbURL, dbUser, dbPass)

            val load: () -> Unit = {
                runOnUIThread {
                    val progress = ProgressDialog(header = "Loading database", message = "($dbURL)")
                    root.root.add(progress)

                    thread(name = "Menagerie Loader", start = true) {
                        try {
                            menagerie = manager.loadMenagerie()
                            importer = MenagerieImporter(menagerie)
                            runOnUIThread { progress.close() }

                            after?.invoke(manager, menagerie, importer)
                        } catch (e: MenagerieDatabaseException) {
                            e.printStackTrace()
                            runOnUIThread { error(title = "Error", header = "Failed to read database: $dbURL", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = stage) }
                        }
                    }
                }
            }
            val migrate: () -> Unit = {
                runOnUIThread {
                    val progress = ProgressDialog(header = "Migrating database to v${MenagerieDatabase.REQUIRED_DATABASE_VERSION}", message = " ($dbURL)")
                    root.root.add(progress)

                    thread(name = "Database migrator thread", start = true) {
                        try {
                            manager.migrateDatabase()
                            runOnUIThread { progress.close() }

                            load()
                        } catch (e: MenagerieDatabaseException) {
                            e.printStackTrace()
                            runOnUIThread { error(title = "Error", header = "Failed to migrate database: $dbURL", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = stage) }
                        }
                    }
                }
            }

            if (manager.needsMigration()) {
                if (manager.canMigrate()) {
                    if (manager.version == -1) {
                        confirm(title = "Database initialization", header = "Database needs to be initialized: $dbURL", owner = stage, actionFn = {
                            migrate()
                        })
                    } else {
                        confirm(title = "Database migration", header = "Database ($dbURL) needs to update (v${manager.version} -> v${MenagerieDatabase.REQUIRED_DATABASE_VERSION})", owner = stage, actionFn = {
                            migrate()
                        })
                    }
                } else {
                    error(title = "Incompatible database", header = "Database v${manager.version} is not supported!", content = "Update to database version 8 with the latest Java Menagerie application\n-OR-\nCreate a new database", owner = stage)
                }
            } else {
                load()
            }
        } catch (e: Exception) {
            error(title = "Error", header = "Failed to connect to database: $dbURL", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = stage)
        }
    }

    private fun initMainStageProperties(stage: Stage) {
        // Maximized
        stage.isMaximized = uiPrefs.getBoolean("maximized", false)
        stage.maximizedProperty()?.addListener { _, _, newValue ->
            uiPrefs.putBoolean("maximized", newValue)
        }

        // Width
        stage.width = uiPrefs.getDouble("width", 600.0)
        stage.widthProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble("width", newValue.toDouble())
        }

        // Height
        stage.height = uiPrefs.getDouble("height", 400.0)
        stage.heightProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble("height", newValue.toDouble())
        }

        // Screen position
        val screen = Screen.getPrimary().visualBounds
        // X
        stage.x = uiPrefs.getDouble("x", (screen.maxX + screen.minX) / 2)
        stage.xProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble("x", newValue.toDouble())
        }
        // Y
        stage.y = uiPrefs.getDouble("y", (screen.maxY + screen.minY) / 2)
        stage.yProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble("y", newValue.toDouble())
        }
    }

}