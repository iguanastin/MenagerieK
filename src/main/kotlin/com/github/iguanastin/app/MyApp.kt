package com.github.iguanastin.app

import com.github.iguanastin.app.menagerie.MenagerieCommunicator
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import com.github.iguanastin.app.menagerie.duplicates.CPUDuplicateFinder
import com.github.iguanastin.app.menagerie.duplicates.CUDADuplicateFinder
import com.github.iguanastin.app.menagerie.duplicates.OnlineMatchSet
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
import javafx.application.Platform
import javafx.collections.ListChangeListener
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
import java.rmi.registry.LocateRegistry
import java.rmi.registry.Registry
import java.rmi.server.ExportException
import java.rmi.server.UnicastRemoteObject
import java.util.prefs.Preferences
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

class MyApp : App(MainView::class, Styles::class) {

    companion object {
        const val communicatorName = "communicator"
        const val defaultConfidence = 0.95
        const val defaultDatabaseUrl = "~/menagerie"
        const val defaultDatabaseUser = "sa"
        const val defaultDatabasePassword = ""
        const val defaultCUDAEnabled = false
        val defaultDownloadsPath: String? = null
    }

    private val uiPrefs: Preferences = Preferences.userRoot().node("iguanastin/MenagerieK/ui")
    private val contextPrefs: Preferences = Preferences.userRoot().node("iguanastin/MenagerieK/context")

    private lateinit var dbURL: String
    private lateinit var dbUser: String
    private lateinit var dbPass: String

    private lateinit var manager: MenagerieDatabase
    private lateinit var menagerie: Menagerie
    private lateinit var importer: MenagerieImporter

    private lateinit var registry: Registry
    private lateinit var communicator: MenagerieCommunicator

    private lateinit var root: MainView


    override fun start(stage: Stage) {
        initInterProcessCommunicator()

        super.start(stage)
        root = find(primaryView) as MainView

        handleParameters()

        log.info("Starting app")

        initMainStageProperties(stage)

        loadMenagerie(stage) { manager, menagerie, importer ->
            manager.updateErrorHandlers.add { e ->
                // TODO show error to user
                log.error("Error occurred while updating database", e)
            }

            purgeZombieTags(menagerie)
            initImporterListeners(importer, menagerie)

            runOnUIThread {
                root.navigateForward(MenagerieView(menagerie, "", true, false, listOf(ElementOfFilter(null, true))))
            }

            initViewControls()
        }
    }

    private fun initInterProcessCommunicator() {
        try {
            registry = LocateRegistry.createRegistry(1099)
            communicator = object : MenagerieCommunicator {
                override fun importUrl(url: String) {
                    runOnUIThread { downloadDragDropUtility(url) }
                }
            }
            registry.bind(communicatorName, UnicastRemoteObject.exportObject(communicator, 0))
        } catch (e: ExportException) {
            try {
                registry = LocateRegistry.getRegistry(1099)

                val url = parameters.named["import"]!!
                (registry.lookup(communicatorName) as MenagerieCommunicator).importUrl(url)
            } catch (e: Exception) {
                log.error("Failed to communicate", e)
            } finally {
                exitProcess(0)
            }
        }
    }

    private fun handleParameters() {
        if ("--reset" in parameters.unnamed) {
            contextPrefs.clear()
            uiPrefs.clear()
        }

        dbURL = contextPrefs.get("db_url", defaultDatabaseUrl)
        dbUser = contextPrefs.get("db_user", defaultDatabaseUser)
        dbPass = contextPrefs.get("db_pass", defaultDatabasePassword)

        if (parameters.named.containsKey("db")) dbURL = parameters.named["db"] ?: defaultDatabaseUrl
        if (parameters.named.containsKey("db-url")) dbURL = parameters.named["db-url"] ?: defaultDatabaseUrl

        if (parameters.named.containsKey("dbu")) dbUser = parameters.named["dbu"] ?: defaultDatabaseUser
        if (parameters.named.containsKey("db-user")) dbUser = parameters.named["db-user"] ?: defaultDatabaseUser

        if (parameters.named.containsKey("dbp")) dbPass = parameters.named["dbp"] ?: defaultDatabasePassword
        if (parameters.named.containsKey("db-pass")) dbPass = parameters.named["db-pass"] ?: defaultDatabasePassword
    }

    private fun initImporterListeners(importer: MenagerieImporter, menagerie: Menagerie) {
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
        importer.afterEach.add { job ->
            val item = job.item ?: return@add
            val similar = CPUDuplicateFinder.findDuplicates(listOf(item), menagerie.items, contextPrefs.getDouble("confidence", defaultConfidence), false)

            runOnUIThread { root.similar.addAll(0, similar.filter { it !in root.similar }) }
        }

        menagerie.items.addListener(ListChangeListener { change ->
            while (change.next()) {
                if (change.removedSize > 0) {
                    runOnUIThread { root.similar.removeIf { it.obj1 in change.removed || it.obj2 in change.removed } }
                }
            }
        })
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
                    downloadDragDropUtility(event.dragboard.url)
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
                event.consume()
                importShortcut(event.isShiftDown)
            }
            if (event.code == KeyCode.DELETE) {
                event.consume()
                deleteShortcut(event.isShortcutDown)
            }
            if (event.code == KeyCode.R && event.isShortcutDown && !event.isShiftDown) {
                event.consume()
                renameGroupShortcut()
            }
            if (event.code == KeyCode.D && event.isShortcutDown && !event.isShiftDown) {
                event.consume()
                duplicatesShortcut(event)
            }
            if (event.code == KeyCode.G && event.isShortcutDown && !event.isShiftDown && !event.isAltDown) {
                event.consume()
                groupShortcut()
            }
            if (event.code == KeyCode.U && event.isShortcutDown && !event.isShiftDown && !event.isAltDown) {
                event.consume()
                ungroupShortcut()
            }
            if (event.code == KeyCode.S && event.isShortcutDown && !event.isAltDown && !event.isShiftDown) {
                event.consume()
                root.root.add(SettingsDialog(contextPrefs))
            }
            if (event.code == KeyCode.F && event.isShortcutDown && event.isShiftDown && !event.isAltDown) {
                event.consume()
                root.root.add(SimilarOnlineDialog(expandGroups(root.itemGrid.selected).map { OnlineMatchSet(it) }))
            }
        }
    }

    private fun ungroupShortcut() {
        if (root.itemGrid.selected.size != 1) return
        val group = root.itemGrid.selected.first()
        if (group !is GroupItem) return

        root.root.confirm("Ungroup", "Ungroup \"${group.title}\"?") {
            onConfirm = {
                ArrayList(group.items).forEach { group.removeItem(it) }
                menagerie.removeItem(group)
            }
        }
    }

    private fun groupShortcut() {
        if (root.itemGrid.selected.isEmpty()) return

        root.root.add(TextInputDialog("Create group", prompt = "Title", onAccept = { title ->
            val group = GroupItem(menagerie.reserveItemID(), System.currentTimeMillis(), menagerie, title)

            for (item in ArrayList(root.itemGrid.selected)) {
                when (item) {
                    is FileItem -> {
                        group.addItem(item)
                    }
                    is GroupItem -> {
                        menagerie.removeItem(item)
                        item.items.forEach { group.addItem(it) }
                    }
                }
            }

            menagerie.addItem(group)

            Platform.runLater {
                root.itemGrid.select(group)
            }
        }))
    }

    private fun duplicatesShortcut(event: KeyEvent) {
        val first = expandGroups(root.itemGrid.selected)
        val second = if (event.isAltDown) expandGroups(menagerie.items) else first

        val pairs = if (contextPrefs.getBoolean("cuda", defaultCUDAEnabled)) {
            CUDADuplicateFinder.findDuplicates(first, second, contextPrefs.getDouble("confidence", defaultConfidence).toFloat(), 100000)
        } else {
            CPUDuplicateFinder.findDuplicates(first, second, contextPrefs.getDouble("confidence", defaultConfidence))
        }
        if (pairs is MutableList) pairs.removeIf { menagerie.hasNonDupe(it) }
        root.root.add(DuplicateResolverDialog(pairs.asObservable()))
    }

    private fun downloadDragDropUtility(url: String) {
        val download = { folder: File? ->
            if (folder != null && folder.exists() && folder.isDirectory) {
                importer.enqueue(RemoteImportJob.intoDirectory(url, folder))
            }
        }

        val folderPath = contextPrefs.get("downloads", defaultDownloadsPath)

        if (folderPath.isNullOrBlank()) {
            val dc = DirectoryChooser()
            dc.title = "Download into folder"
            download(dc.showDialog(root.currentWindow))
        } else {
            val folder = File(folderPath)
            if (folder.exists() && folder.isDirectory) {
                download(folder)
            } else {
                val dc = DirectoryChooser()
                dc.title = "Download into folder"
                download(dc.showDialog(root.currentWindow))
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
                val dir = contextPrefs.get("downloads", defaultDownloadsPath)
                if (dir != null) dc.initialDirectory = File(dir)
                dc.title = "Import directory"
                val folder = dc.showDialog(root.currentWindow)
                if (folder != null) {
                    contextPrefs.put("downloads", folder.parent)
                    importFilesDialog(listOf(folder))
                }
            } else {
                val fc = FileChooser()
                val dir = contextPrefs.get("downloads", defaultDownloadsPath)
                if (dir != null) fc.initialDirectory = File(dir)
                fc.title = "Import files"
                val files = fc.showOpenMultipleDialog(root.currentWindow)
                if (!files.isNullOrEmpty()) {
                    contextPrefs.put("downloads", files.first().parent)
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

        // Close RMI communicator
        UnicastRemoteObject.unexportObject(communicator, true)
        registry.unbind(communicatorName)

        try {
            importer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        thread(start = true, name = "Database Shutdown") {
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

fun main(vararg args: String) {
    launch<MyApp>(*args)
}