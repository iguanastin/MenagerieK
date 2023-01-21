package com.github.iguanastin.app

import com.github.iguanastin.app.context.MenagerieContext
import com.github.iguanastin.app.context.TagEdit
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import com.github.iguanastin.app.menagerie.duplicates.local.CPUDuplicateFinder
import com.github.iguanastin.app.menagerie.duplicates.local.CUDADuplicateFinder
import com.github.iguanastin.app.menagerie.duplicates.remote.OnlineMatchSet
import com.github.iguanastin.app.menagerie.import.ImportJob
import com.github.iguanastin.app.menagerie.import.ImportJobIntoGroup
import com.github.iguanastin.app.menagerie.import.MenagerieImporter
import com.github.iguanastin.app.menagerie.import.RemoteImportJob
import com.github.iguanastin.app.menagerie.model.*
import com.github.iguanastin.app.menagerie.view.MenagerieView
import com.github.iguanastin.app.menagerie.view.filters.ElementOfFilter
import com.github.iguanastin.app.utils.WindowsExplorerComparator
import com.github.iguanastin.app.utils.expandGroups
import com.github.iguanastin.view.MainView
import com.github.iguanastin.view.dialog.*
import com.github.iguanastin.view.runOnUIThread
import javafx.application.Platform
import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.scene.control.ButtonType
import javafx.scene.image.Image
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
        const val rmiCommunicatorName = "communicator"

        const val prefsKeyDBURL = "db_url"
        const val prefsKeyDBUser = "db_user"
        const val prefsKeyDBPass = "db_pass"
        const val prefsConfidenceKey = "confidence"
        const val prefsEnableCudaKey = "cuda"
        const val prefsAPIKey = "api"
        const val prefsAPIPortKey = "api_port"
        const val prefsDownloadsKey = "downloads"
        const val prefsMaximizedKey = "maximized"
        const val prefsWidthKey = "width"
        const val prefsHeightKey = "height"
        const val prefsXKey = "x"
        const val prefsYKey = "y"

        const val defaultAPIPort = 54321
        const val defaultConfidence = 0.95
        const val defaultDatabaseUrl = "~/menagerie"
        const val defaultDatabaseUser = "sa"
        const val defaultDatabasePassword = ""
        const val defaultCUDAEnabled = false
        const val defaultAPIEnabled = true
        val defaultDownloadsPath: String? = null
    }

    private val uiPrefs: Preferences = Preferences.userRoot().node("iguanastin/MenagerieK/ui")
    private val contextPrefs: Preferences = Preferences.userRoot().node("iguanastin/MenagerieK/context")

    private var context: MenagerieContext? = null

    private lateinit var rmiRegistry: Registry
    private lateinit var rmiCommunicator: MenagerieRMICommunicator

    private lateinit var root: MainView

    private val preLoadImportQueue: MutableList<String> = mutableListOf()


    override fun start(stage: Stage) {
        initInterProcessCommunicator()

        handleParameters()

        super.start(stage)
        root = find(primaryView) as MainView

        // Add window icons to stage
        // TODO icon works as expected in IDE, but doesn't set the windows toolbar icon when launched externally
        stage.icons.addAll(
            Image(MyApp::class.java.getResource("/imgs/icons/16.png")!!.toExternalForm()),
            Image(MyApp::class.java.getResource("/imgs/icons/32.png")!!.toExternalForm()),
            Image(MyApp::class.java.getResource("/imgs/icons/64.png")!!.toExternalForm()),
            Image(MyApp::class.java.getResource("/imgs/icons/128.png")!!.toExternalForm()),
        )

        log.info("Starting Menagerie")
        initMainStageProperties(stage)
        initViewControls()
        loadMenagerie(
            stage,
            contextPrefs.get(prefsKeyDBURL, defaultDatabaseUrl),
            contextPrefs.get(prefsKeyDBUser, defaultDatabaseUser),
            contextPrefs.get(prefsKeyDBPass, defaultDatabasePassword)
        ) { context ->
            onMenagerieLoaded(context)
        }
    }

    private fun onMenagerieLoaded(context: MenagerieContext) {
        this.context = context

        context.database.updateErrorHandlers.add { e ->
            log.error("Error occurred while updating database", e)
            information("Error while updating database", e.message, owner = root.currentWindow, title = "Error")
            // TODO show better error to user
        }

        context.importer.afterEach.add {
            // If only the first item in search is selected when an item is imported, select the newly imported item
            val singleSelected = root.itemGrid.selected.singleOrNull() ?: return@add
            if (singleSelected == root.itemGrid.items.first()) {
                runOnUIThread { root.itemGrid.select(root.itemGrid.items.first()) }
            }
        }

        // Start the HTTP API server
        if (contextPrefs.getBoolean(prefsAPIKey, defaultAPIEnabled)) {
            context.api.start(contextPrefs.getInt(prefsAPIPortKey, defaultAPIPort))
        }

        purgeUnusedTags(context.menagerie)
        initImporterListeners(context)

        // Initial search
        runOnUIThread {
            root.navigateForward(
                MenagerieView(
                    context.menagerie,
                    searchString = "",
                    descending = true,
                    shuffle = false,
                    filters = listOf(ElementOfFilter(null, true))
                )
            )

            log.info("Flushing ${preLoadImportQueue.size} urls from pre-load import queue")
            preLoadImportQueue.forEach { rmiCommunicator.importUrl(it) }
        }
    }

    private fun initInterProcessCommunicator() {
        try {
            rmiRegistry = LocateRegistry.createRegistry(1099)
            rmiCommunicator = object : MenagerieRMICommunicator {
                override fun importUrl(url: String) {
                    if (context != null) {
                        val splitString = url.split(",")

                        val sanitizedUrl = splitString[0]

                        val tags: List<Tag> =
                            splitString.subList(1, splitString.size).mapNotNull { context!!.menagerie.getTag(it) }

                        runOnUIThread { downloadFileFromWeb(sanitizedUrl, tags) }
                    } else {
                        log.info("Storing url for import once app is ready: $url")
                        preLoadImportQueue.add(url)
                    }
                }

                override fun bringToFront() {
                    root.currentStage?.toFront()
                }
            }
            rmiRegistry.bind(rmiCommunicatorName, UnicastRemoteObject.exportObject(rmiCommunicator, 0))

            // Import url if in parameter
            if (parameters.named.containsKey("import")) {
                rmiCommunicator.importUrl(parameters.named["import"]?.removePrefix("menagerie:")!!)
            }
        } catch (e: ExportException) {
            // Cannot open an RMI registry as Menagerie instance is already running
            try {
                rmiRegistry = LocateRegistry.getRegistry(1099)
                val communicator = (rmiRegistry.lookup(rmiCommunicatorName) as MenagerieRMICommunicator)

                // Attempt to send an import message to the open menagerie instance
                val url = parameters.named["import"]?.removePrefix("menagerie:")
                if (url != null) {
                    communicator.importUrl(url)
                } else {
                    communicator.bringToFront()
                }
            } catch (e: Exception) {
                log.error("Failed to communicate", e)
                exitProcess(1)
            }

            exitProcess(0)
        }
    }

    private fun handleParameters() {
        if ("--reset" in parameters.unnamed) {
            context?.prefs?.clear()
            uiPrefs.clear()
        }

        if (parameters.named.containsKey("db")) contextPrefs.put(
            prefsKeyDBURL, parameters.named["db"]
                ?: defaultDatabaseUrl
        )
        if (parameters.named.containsKey("db-url")) contextPrefs.put(
            prefsKeyDBURL, parameters.named["db-url"]
                ?: defaultDatabaseUrl
        )

        if (parameters.named.containsKey("dbu")) contextPrefs.put(
            prefsKeyDBUser, parameters.named["dbu"]
                ?: defaultDatabaseUser
        )
        if (parameters.named.containsKey("db-user")) contextPrefs.put(
            prefsKeyDBUser, parameters.named["db-user"]
                ?: defaultDatabaseUser
        )

        if (parameters.named.containsKey("dbp")) contextPrefs.put(
            prefsKeyDBPass, parameters.named["dbp"]
                ?: defaultDatabasePassword
        )
        if (parameters.named.containsKey("db-pass")) contextPrefs.put(
            prefsKeyDBPass, parameters.named["db-pass"]
                ?: defaultDatabasePassword
        )

        if ("--api-only" in parameters.unnamed) exitProcess(0)
    }

    private fun initImporterListeners(context: MenagerieContext) {
        context.importer.onError.add { e ->
            log.error("Error occurred while importing", e)
            information("Import failed", e.message, owner = root.currentWindow, title = "Error")
            // TODO show better error message to user
        }
        context.importer.onQueued.add { job ->
            runOnUIThread {
                root.imports.apply {
                    add(ImportNotification(job))
                    sortBy { it.isFinished }
                }
            }
        }
        context.importer.afterEach.add { job ->
            val item = job.item ?: return@add
            val similar = CPUDuplicateFinder.findDuplicates(
                listOf(item),
                context.menagerie.items,
                contextPrefs.getDouble(prefsConfidenceKey, defaultConfidence),
                false
            )

            runOnUIThread { root.similar.addAll(0, similar.filter { it !in root.similar }) }
        }

        // Listen to item list and remove similar pairs containing removed items
        context.menagerie.items.addListener(ListChangeListener { change ->
            while (change.next()) {
                if (change.removedSize > 0) {
                    runOnUIThread { root.similar.removeIf { it.obj1 in change.removed || it.obj2 in change.removed } }
                }
            }
        })
    }

    private fun purgeUnusedTags(menagerie: Menagerie) {
        val toRemove = mutableSetOf<Tag>()
        for (tag in menagerie.tags) {
            if (tag.frequency == 0) {
                toRemove.add(tag)
            }
        }
        toRemove.forEach {
            log.info("Purging unused tag: $it")
            menagerie.removeTag(it)
        }
    }

    private fun initViewControls() {
        initExternalDragDrop()
        initItemGridKeyHandler()
        initEditTagsControl()
    }

    private fun initEditTagsControl() {
        root.applyTagEdit.onAction = EventHandler { event ->
            val items = root.itemGrid.selected
            val tagsToAdd = mutableListOf<Tag>()
            val tagsToRemove = mutableListOf<Tag>()

            for (name in root.editTags.text.trim().split(Regex("\\s+"))) {
                if (name.isBlank()) continue // Ignore empty and blank additions

                val menagerie = root.itemGrid.selected[0].menagerie
                if (name.startsWith('-')) {
                    if (name.length == 1) continue
                    val tag: Tag = menagerie.getTag(name.substring(1)) ?: continue
                    tagsToRemove.add(tag)
                } else {
                    var tag: Tag? = menagerie.getTag(name)
                    if (tag == null) {
                        tag = Tag(menagerie.reserveTagID(), name)
                        menagerie.addTag(tag)
                    }
                    tagsToAdd.add(tag)
                }
            }

            val edit = TagEdit(items, tagsToAdd, tagsToRemove)
            if (items.isNotEmpty() && (tagsToAdd.isNotEmpty() || tagsToRemove.isNotEmpty())) {
                edit.applyEdit()
                context?.tagEdits?.push(edit)
            }

            root.editTagsPane.hide()
            event.consume()
        }
    }

    private fun initItemGridKeyHandler() {
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
                root.root.add(FindOnlineChooseMatcherDialog(expandGroups(root.itemGrid.selected).map { OnlineMatchSet(it) }))
            }
            if (event.code == KeyCode.Z && event.isShortcutDown && !event.isShiftDown && !event.isAltDown) {
                event.consume()
                undoLastEdit()
            }
        }
    }

    private fun undoLastEdit() {
        if (context?.tagEdits?.isNotEmpty() == true) {
            val peek = context?.tagEdits?.peek() ?: return
            root.root.confirm(
                "Undo tag edit",
                peek.addedHistory.entries.joinToString("\n") { "'${it.key.name}' added to ${it.value.size} items" } + "\n\n" + peek.removedHistory.entries.joinToString(
                    "\n"
                ) { "'${it.key.name}' removed from ${it.value.size} items" }).onConfirm = {
                context?.tagEdits?.pop()?.undoEdit()
            }
        }
    }

    private fun initExternalDragDrop() {
        root.root.onDragOver = EventHandler { event ->
            if (context != null && event.gestureSource == null && (event.dragboard.hasFiles() || event.dragboard.hasUrl())) {
                root.dragOverlay.show()
                event.apply {
                    acceptTransferModes(*TransferMode.ANY)
                    consume()
                }
            }
        }
        root.root.onDragDropped = EventHandler { event ->
            if (context != null && event.isAccepted) {
                if (event.dragboard.url?.startsWith("http") == true) {
                    downloadFileFromWeb(event.dragboard.url)
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
    }

    fun ungroupShortcut() {
        val group = root.itemGrid.selected.singleOrNull() ?: return
        if (group !is GroupItem) return

        root.root.confirm("Ungroup", "Ungroup \"${group.title}\"?") {
            onConfirm = {
                group.clearItems()
                group.menagerie.removeItem(group)
            }
        }
    }

    fun groupShortcut() {
        if (root.itemGrid.selected.isEmpty()) return
        val menagerie = context?.menagerie ?: return

        root.root.add(TextInputDialog("Create group", prompt = "Title", onAccept = { title ->
            val group = GroupItem(menagerie.reserveItemID(), System.currentTimeMillis(), menagerie, title)
            val tags = mutableSetOf<Tag>()

            for (item in ArrayList(root.itemGrid.selected)) {
                tags.addAll(item.tags)

                when (item) {
                    is FileItem -> {
                        group.addItem(item)
                    }
                    is GroupItem -> {
                        menagerie.removeItem(item)
                        item.items.forEach {
                            group.addItem(it)
                            tags.addAll(it.tags)
                        }
                    }
                }
            }

            tags.forEach { group.addTag(it) }

            group.addTag(menagerie.getOrMakeTag("tagme"))

            menagerie.addItem(group)

            Platform.runLater {
                root.itemGrid.select(group)
            }
        }))
    }

    private fun duplicatesShortcut(event: KeyEvent) {
        val menagerie = context?.menagerie ?: return

        val first = expandGroups(root.itemGrid.selected)
        val second = if (event.isAltDown) expandGroups(menagerie.items) else first

        val progress =
            ProgressDialog(header = "Finding duplicates", message = "${first.size} against ${second.size} items")
        root.root.add(progress)

        thread(start = true, isDaemon = true, name = "DuplicateFinder") {
            try {
                val pairs = if (contextPrefs.getBoolean(prefsEnableCudaKey, defaultCUDAEnabled)) {
                    CUDADuplicateFinder.findDuplicates(
                        first,
                        second,
                        contextPrefs.getDouble(prefsConfidenceKey, defaultConfidence).toFloat(),
                        100000
                    )
                } else {
                    CPUDuplicateFinder.findDuplicates(
                        first,
                        second,
                        contextPrefs.getDouble(prefsConfidenceKey, defaultConfidence)
                    )
                }
                if (pairs is MutableList) pairs.removeIf { menagerie.hasNonDupe(it) }

                runOnUIThread {
                    progress.close()
                    root.root.add(DuplicateResolverDialog(pairs.asObservable()))
                }
            } catch (e: Exception) {
                log.error("Error while finding duplicates", e)
                runOnUIThread { progress.close() }
            }
        }
    }

    private fun downloadFileFromWeb(url: String, tags: List<Tag>? = null) {
        val importer = context?.importer ?: return

        val download = { folder: File? ->
            if (folder != null && folder.exists() && folder.isDirectory) {
                importer.enqueue(RemoteImportJob.intoDirectory(url, folder, tags))
            }
        }

        val folderPath = contextPrefs.get(prefsDownloadsKey, defaultDownloadsPath)

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
        val group = root.itemGrid.selected.singleOrNull() ?: return
        if (group !is GroupItem) return

        root.root.add(TextInputDialog(header = "Rename group", text = group.title, onAccept = {
            group.title = it
        }))
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
                val dir = contextPrefs.get(prefsDownloadsKey, defaultDownloadsPath)
                if (dir != null) dc.initialDirectory = File(dir)
                dc.title = "Import directory"
                val folder = dc.showDialog(root.currentWindow)
                if (folder != null) {
                    contextPrefs.put(prefsDownloadsKey, folder.parent)
                    importFilesDialog(listOf(folder))
                }
            } else {
                val fc = FileChooser()
                val dir = contextPrefs.get(prefsDownloadsKey, defaultDownloadsPath)
                if (dir != null) fc.initialDirectory = File(dir)
                fc.title = "Import files"
                val files = fc.showOpenMultipleDialog(root.currentWindow)
                if (!files.isNullOrEmpty()) {
                    contextPrefs.put(prefsDownloadsKey, files.first().parent)
                    importFilesDialog(files)
                }
            }
        }
    }

    private fun importFilesDialog(files: List<File>) {
        val menagerie = context?.menagerie ?: return
        val importer = context?.importer ?: return

        root.root.importdialog(files) {
            individually = {
                files.forEach { file ->
                    if (file.isDirectory) {
                        getFilesRecursively(file).forEach { if (!menagerie.hasFile(it)) importer.enqueue(ImportJob(it)) }
                    } else {
                        importer.enqueue(ImportJob(file))
                    }
                }
            }
            asGroup = {
                val jobs = mutableListOf<ImportJob>()
                files.forEach { file ->
                    if (file.isDirectory) {
                        getFilesRecursively(file).forEach { if (!menagerie.hasFile(it)) jobs.add(ImportJob(it)) }
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
                        getFilesRecursively(file).forEach { if (!menagerie.hasFile(it)) jobs.add(ImportJob(it)) }
                        ImportJobIntoGroup.asGroup(jobs, file.name).forEach { importer.enqueue(it) }
                    } else if (!menagerie.hasFile(file)) {
                        importer.enqueue(ImportJob(file))
                    }
                }
            }
            if (!scene.window.isFocused) scene.window.requestFocus()
        }
    }

    private fun getFilesRecursively(folder: File): List<File> {
        if (folder.isDirectory) {
            val result = mutableListOf<File>()
            folder.listFiles()?.sortedWith(WindowsExplorerComparator())!!.forEach {
                if (it.isDirectory) {
                    result.addAll(getFilesRecursively(it))
                } else {
                    result.add(it)
                }
            }
            return result
        } else {
            return listOf(folder)
        }
    }

    private fun deleteItems(items: List<Item>) {
        val menagerie = context?.menagerie ?: return

        items.forEach { item ->
            log.info("Removing item: $item")
            if (item is GroupItem) {
                ArrayList(item.items).forEach {
                    log.info("Removing item: $it")
                    menagerie.removeItem(it)
                }
            }
            menagerie.removeItem(item)
        }
    }

    private fun deleteFiles(items: List<Item>) {
        deleteItems(items)

        items.forEach {
            if (it is FileItem) {
                deleteFile(it)
            } else if (it is GroupItem) {
                for (item in it.items) {
                    deleteFile(item)
                }
            }
        }
    }

    private fun deleteFile(item: FileItem) {
        try {
            log.info("Deleting file: ${item.file}")
            item.file.delete()
        } catch (e: IOException) {
            log.error("Exception while deleting file: ${item.file}", e)
        }
    }

    override fun stop() {
        super.stop()

        log.info("Stopping app")

        // Close RMI communicator
        UnicastRemoteObject.unexportObject(rmiCommunicator, true)
        rmiRegistry.unbind(rmiCommunicatorName)

        context?.close()
    }

    private fun loadMenagerie(
        stage: Stage,
        dbURL: String,
        dbUser: String,
        dbPass: String,
        after: ((MenagerieContext) -> Unit)? = null
    ) {
        try {
            val database = MenagerieDatabase(dbURL, dbUser, dbPass)

            val load: () -> Unit = {
                runOnUIThread {
                    val progress = ProgressDialog(header = "Loading database", message = "($dbURL)")
                    root.root.add(progress)

                    thread(name = "Menagerie Loader", start = true) {
                        try {
                            val menagerie = database.loadMenagerie { msg -> runOnUIThread { progress.message = msg } }
                            val importer = MenagerieImporter(menagerie)
                            runOnUIThread { progress.close() }

                            after?.invoke(MenagerieContext(menagerie, importer, database, contextPrefs))
                        } catch (e: MenagerieDatabaseException) {
                            e.printStackTrace()
                            runOnUIThread {
                                error(
                                    title = "Error",
                                    header = "Failed to read database: $dbURL",
                                    content = e.localizedMessage,
                                    owner = stage,
                                    buttons = arrayOf(ButtonType.OK)
                                )
                            }
                        }
                    }
                }
            }
            val migrate: () -> Unit = {
                runOnUIThread {
                    val progress = ProgressDialog(
                        header = "Migrating database to v${MenagerieDatabase.REQUIRED_DATABASE_VERSION}",
                        message = " ($dbURL)"
                    )
                    root.root.add(progress)

                    thread(name = "Database migrator thread", start = true) {
                        try {
                            database.migrateDatabase()
                            runOnUIThread { progress.close() }

                            load()
                        } catch (e: MenagerieDatabaseException) {
                            e.printStackTrace()
                            runOnUIThread {
                                error(
                                    title = "Error",
                                    header = "Failed to migrate database: $dbURL",
                                    content = e.localizedMessage,
                                    owner = stage,
                                    buttons = arrayOf(ButtonType.OK)
                                )
                            }
                        }
                    }
                }
            }

            if (database.needsMigration()) {
                if (database.canMigrate()) {
                    if (database.version == -1) {
                        confirm(
                            title = "Database initialization",
                            header = "Database needs to be initialized: $dbURL",
                            owner = stage,
                            actionFn = {
                                migrate()
                            })
                    } else {
                        confirm(
                            title = "Database migration",
                            header = "Database ($dbURL) needs to update (v${database.version} -> v${MenagerieDatabase.REQUIRED_DATABASE_VERSION})",
                            owner = stage,
                            actionFn = {
                                migrate()
                            })
                    }
                } else {
                    error(
                        title = "Incompatible database",
                        header = "Database v${database.version} is not supported!",
                        content = "Update to database version 8 with the latest Java Menagerie application\n-OR-\nCreate a new database",
                        owner = stage
                    )
                }
            } else {
                load()
            }
        } catch (e: Exception) {
            error(
                title = "Error",
                header = "Failed to connect to database: $dbURL",
                content = e.localizedMessage,
                owner = stage,
                buttons = arrayOf(ButtonType.OK)
            )
        }
    }

    private fun initMainStageProperties(stage: Stage) {
        // Maximized
        stage.isMaximized = uiPrefs.getBoolean(prefsMaximizedKey, false)
        stage.maximizedProperty()?.addListener { _, _, newValue ->
            uiPrefs.putBoolean(prefsMaximizedKey, newValue)
        }

        // Width
        stage.width = uiPrefs.getDouble(prefsWidthKey, 600.0)
        stage.widthProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble(prefsWidthKey, newValue.toDouble())
        }

        // Height
        stage.height = uiPrefs.getDouble(prefsHeightKey, 400.0)
        stage.heightProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble(prefsHeightKey, newValue.toDouble())
        }

        // Screen position
        val screen = Screen.getPrimary().visualBounds
        // X
        stage.x = uiPrefs.getDouble(prefsXKey, (screen.maxX + screen.minX) / 2)
        stage.xProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble(prefsXKey, newValue.toDouble())
        }
        // Y
        stage.y = uiPrefs.getDouble(prefsYKey, (screen.maxY + screen.minY) / 2)
        stage.yProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble(prefsYKey, newValue.toDouble())
        }
    }

}

fun main(vararg args: String) {
    launch<MyApp>(*args)
}