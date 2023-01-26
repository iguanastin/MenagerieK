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
import com.github.iguanastin.app.menagerie.search.MenagerieSearch
import com.github.iguanastin.app.menagerie.search.filters.ElementOfFilter
import com.github.iguanastin.app.settings.AppSettings
import com.github.iguanastin.app.settings.WindowSettings
import com.github.iguanastin.app.utils.WindowsExplorerComparator
import com.github.iguanastin.app.utils.expandGroups
import com.github.iguanastin.view.MainView
import com.github.iguanastin.view.Shortcut
import com.github.iguanastin.view.bindShortcut
import com.github.iguanastin.view.dialog.*
import com.github.iguanastin.view.runOnUIThread
import javafx.application.Platform
import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.event.EventType
import javafx.scene.Node
import javafx.scene.control.ButtonType
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.TransferMode
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import mu.KotlinLogging
import tornadofx.*
import java.awt.Desktop
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

class MyApp : App(MainView::class, Styles::class) {

    companion object {
        const val VERSION = "1.0.2" // When updating version, update it in pom.xml as well

        val shortcuts: MutableList<Shortcut> = mutableListOf()
    }

    private val uiPrefs: WindowSettings = WindowSettings()
    private val contextPrefs: AppSettings = AppSettings()

    private var context: MenagerieContext? = null

    /**
     * Inter-process communicator to avoid having two instances of the app and to enable importing from the browser.
     */
    private lateinit var rmi: MenagerieRMI

    private lateinit var root: MainView

    /**
     * Queue of urls collected before the Menagerie has fully loaded.
     *
     * Gets flushed to the importer once the Menagerie context is ready.
     */
    private val preLoadImportQueue: MutableList<String> = mutableListOf()


    override fun start(stage: Stage) {
        initInterProcessCommunicator()

        handleParameters()

        super.start(stage)
        root = find(primaryView) as MainView

        // Add window icons to stage
        stage.icons.addAll(
            Image(MyApp::class.java.getResource("/imgs/icons/16.png")!!.toExternalForm()),
            Image(MyApp::class.java.getResource("/imgs/icons/32.png")!!.toExternalForm()),
            Image(MyApp::class.java.getResource("/imgs/icons/64.png")!!.toExternalForm()),
            Image(MyApp::class.java.getResource("/imgs/icons/128.png")!!.toExternalForm()),
        )

        log.info("Starting Menagerie")
        initMainStageProperties(stage)
        initViewControls()

        // Load the Menagerie data from disk
        loadMenagerie(
            stage,
            contextPrefs.database.url.value,
            contextPrefs.database.user.value,
            contextPrefs.database.pass.value
        ) { context ->
            onMenagerieLoaded(context)
        }
    }

    private fun onMenagerieLoaded(context: MenagerieContext) {
        this.context = context

        context.database.updateErrorHandlers.add { e ->
            log.error("Error occurred while updating database", e)
            runOnUIThread {
                information(
                    "Error while updating database",
                    e.message,
                    owner = root.currentWindow,
                    title = "Error"
                )
            }
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
        if (contextPrefs.api.enabled.value) {
            context.api.start(contextPrefs.api.port.value)
        }

        purgeUnusedTags(context.menagerie)
        initImporterListeners(context)

        runOnUIThread {
            // Initial search
            root.navigateForward(
                MenagerieSearch(
                    context.menagerie,
                    searchString = "",
                    descending = true,
                    shuffle = false,
                    filters = listOf(ElementOfFilter(null, true))
                )
            )

            log.info("Flushing ${preLoadImportQueue.size} urls from pre-load import queue")
            preLoadImportQueue.forEach { rmi.communicator.importUrl(it) }
        }
    }

    private fun initInterProcessCommunicator() {
        rmi = MenagerieRMI(onServerStart = { rmi ->
            // Import url if in parameter
            if (parameters.named.containsKey("import")) {
                rmi.communicator.importUrl(parameters.named["import"]?.removePrefix("menageriek:")!!)
            }
        }, onClientStart = { rmi ->
            // Attempt to send an import message to the open menagerie instance
            val url = parameters.named["import"]?.removePrefix("menageriek:")
            if (url != null) {
                rmi.communicator.importUrl(url)
            }
            exitProcess(0)
        }, onImportURL = { url ->
            if (context != null) {
                val splitString = url.split(",")

                val sanitizedUrl = splitString[0]

                val tags: List<Tag> =
                    splitString.subList(1, splitString.size).mapNotNull { context!!.menagerie.getTag(it) }

                runOnUIThread { downloadFileFromWeb(sanitizedUrl, tags) }
            } else {
                preLoadImportQueue.add(url)
            }
        }, onFailedConnect = { _, e ->
            log.error("Failed to connect RMI", e)
            exitProcess(1)
        })
    }

    private fun handleParameters() {
        if ("--reset" in parameters.unnamed) {
            contextPrefs.resetToDefaults()
            uiPrefs.resetToDefaults()
        }

        if (parameters.named.containsKey("db")) contextPrefs.database.url.value =
            parameters.named["db"] ?: contextPrefs.database.url.default
        if (parameters.named.containsKey("db-url")) contextPrefs.database.url.value =
            parameters.named["db-url"] ?: contextPrefs.database.url.default

        if (parameters.named.containsKey("dbu")) contextPrefs.database.user.value =
            parameters.named["dbu"] ?: contextPrefs.database.user.value
        if (parameters.named.containsKey("db-user")) contextPrefs.database.user.value =
            parameters.named["db-user"] ?: contextPrefs.database.user.default

        if (parameters.named.containsKey("dbp")) contextPrefs.database.pass.value =
            parameters.named["dbp"] ?: contextPrefs.database.pass.default
        if (parameters.named.containsKey("db-pass")) contextPrefs.database.pass.value =
            parameters.named["db-pass"] ?: contextPrefs.database.pass.default

        if ("--api-only" in parameters.unnamed) exitProcess(0)
    }

    private fun initImporterListeners(context: MenagerieContext) {
        context.importer.onError.add { e ->
            log.error("Error occurred while importing", e)
            runOnUIThread { information("Import failed", e.message, owner = root.currentWindow, title = "Error") }
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
                contextPrefs.duplicate.confidence.value,
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

        root.root.bindVisibleShortcut(KeyCode.Q, ctrl = true, desc = "Exit Menagerie", context = "Global") {
            Platform.exit()
        }
    }

    private fun initEditTagsControl() {
        root.applyTagEdit.onAction = EventHandler { event ->
            val items = root.itemGrid.selected
            if (items.isEmpty()) return@EventHandler // Do nothing if there are no items selected

            val tagsToAdd = mutableListOf<Tag>()
            val tagsToRemove = mutableListOf<Tag>()

            val menagerie = context?.menagerie ?: return@EventHandler // Do nothing if there is no menagerie context

            for (name in root.editTags.text.trim().split(Regex("\\s+"))) {
                if (name.isBlank()) continue // Ignore empty and blank additions

                if (name.startsWith('-')) {
                    if (name.length == 1) continue // Ignore a '-' by itself
                    val tag: Tag = menagerie.getTag(name.substring(1)) ?: continue // Ignore tags that don't exist
                    tagsToRemove.add(tag)
                } else {
                    tagsToAdd.add(menagerie.getOrMakeTag(name))
                }
            }

            if (tagsToAdd.isEmpty() && tagsToRemove.isEmpty()) return@EventHandler // Do nothing if there are no viable tag edits

            // Apply tag edits
            TagEdit(items, tagsToAdd, tagsToRemove).apply {
                applyEdit()
                context?.tagEdits?.push(this)
            }

            root.editTagsPane.hide()
            event.consume()
        }
    }

    private fun initItemGridKeyHandler() {
        root.itemGrid.apply {
            bindVisibleShortcut(KeyCode.H, ctrl = true, desc = "Open help", context = "Main Screen") {
                root.root.helpDialog()
            }
            bindVisibleShortcut(KeyCode.I, ctrl = true, desc = "Import file(s)", context = "Main Screen") {
                importFileShortcut()
            }
            bindVisibleShortcut(KeyCode.I, ctrl = true, shift = true, desc = "Import folder", context = "Main Screen") {
                importFolderShortcut()
            }
            bindVisibleShortcut(KeyCode.DELETE, desc = "Delete selected items and files", context = "Item List") {
                deleteShortcut()
            }
            bindVisibleShortcut(
                KeyCode.DELETE,
                ctrl = true,
                desc = "Drop selected items without deleting files",
                context = "Item List"
            ) {
                forgetShortcut()
            }
            bindVisibleShortcut(KeyCode.R, ctrl = true, desc = "Rename selected group", context = "Item List") {
                renameGroupShortcut()
            }
            bindVisibleShortcut(
                KeyCode.D,
                ctrl = true,
                desc = "Find similar files in selected",
                context = "Item List"
            ) {
                duplicatesShortcutUtil(true)
            }
            bindVisibleShortcut(
                KeyCode.D,
                ctrl = true,
                alt = true,
                desc = "Find similar files in Menagerie",
                context = "Item List"
            ) {
                duplicatesShortcutUtil(false)
            }
            bindVisibleShortcut(KeyCode.G, ctrl = true, desc = "Group selected items", context = "Item List") {
                groupShortcut()
            }
            bindVisibleShortcut(KeyCode.U, ctrl = true, desc = "Ungroup selected group", context = "Item List") {
                ungroupShortcut()
            }
            bindVisibleShortcut(KeyCode.S, ctrl = true, desc = "Open settings", context = "Main Screen") {
                root.root.add(SettingsDialog(contextPrefs))
            }
            bindVisibleShortcut(
                KeyCode.F,
                ctrl = true,
                shift = true,
                desc = "Find similar images online",
                context = "Item List"
            ) {
                root.root.add(FindOnlineChooseMatcherDialog(expandGroups(root.itemGrid.selected).map { OnlineMatchSet(it) }))
            }
            bindVisibleShortcut(KeyCode.Z, ctrl = true, desc = "Undo last tag edit", context = "Main Screen") {
                undoLastEdit()
            }
            bindVisibleShortcut(KeyCode.ENTER, desc = "Open item or group", context = "Item List") {
                onItemAction(selected.singleOrNull() ?: return@bindVisibleShortcut)
            }
            bindVisibleShortcut(KeyCode.BACK_SPACE, desc = "Navigate back", context = "Main Screen") {
                root.navigateBack()
            }
            bindVisibleShortcut(KeyCode.E, ctrl = true, desc = "Edit tags", context = "Item List") {
                root.showEditTagsPane()
            }
            bindVisibleShortcut(KeyCode.F, ctrl = true, desc = "Search", context = "Main Screen") {
                root.focusSearchField()
            }
            bindVisibleShortcut(KeyCode.T, ctrl = true, desc = "Display all tags", context = "Main Screen") {
                root.displayTagsDialog()
            }
            bindVisibleShortcut(KeyCode.D, ctrl = true, shift = true, desc = "Open duplicate resolver dialog", context = "Main Screen") {
                root.openSimilarDialog()
            }
        }
    }

    private fun undoLastEdit() {
        if (context?.tagEdits?.isEmpty() == true) return
        val peek = context?.tagEdits?.peek() ?: return // Return if there are no tag edits in the stack

        root.root.confirm(
            "Undo tag edit",
            peek.addedHistory.entries.joinToString("\n") { "'${it.key.name}' added to ${it.value.size} items" } + "\n\n" + peek.removedHistory.entries.joinToString(
                "\n"
            ) { "'${it.key.name}' removed from ${it.value.size} items" }).onConfirm = {
            context?.tagEdits?.pop()?.undoEdit()
        }
    }

    private fun initExternalDragDrop() {
        root.root.onDragOver = EventHandler { event ->
            if (context == null || event.gestureSource != null || (!event.dragboard.hasFiles() && !event.dragboard.hasUrl())) return@EventHandler

            root.dragOverlay.show()
            event.apply {
                acceptTransferModes(*TransferMode.ANY)
                consume()
            }
        }
        root.root.onDragDropped = EventHandler { event ->
            if (context == null || !event.isAccepted) return@EventHandler

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

    private fun duplicatesShortcutUtil(inSelected: Boolean) {
        val menagerie = context?.menagerie ?: return

        val first = expandGroups(root.itemGrid.selected)
        val second = if (inSelected) first else expandGroups(menagerie.items)

        val progress =
            ProgressDialog(header = "Finding duplicates", message = "${first.size} against ${second.size} items")
        root.root.add(progress)

        thread(start = true, isDaemon = true, name = "DuplicateFinder") {
            try {
                val pairs = if (contextPrefs.duplicate.enableCuda.value) {
                    CUDADuplicateFinder.findDuplicates(
                        first,
                        second,
                        contextPrefs.duplicate.confidence.value.toFloat(),
                        100000
                    )
                } else {
                    CPUDuplicateFinder.findDuplicates(
                        first,
                        second,
                        contextPrefs.duplicate.confidence.value
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

        val folderPath = contextPrefs.general.downloadFolder.value

        if (folderPath.isBlank()) {
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

    private fun forgetShortcut() {
        val del: List<Item> = mutableListOf<Item>().apply { addAll(root.itemGrid.selected) }
        if (del.isEmpty()) return

        runOnUIThread {
            root.root.confirm("Drop items?", "Drop these items from the database?\n(Does not delete files)") {
                onConfirm = {
                    deleteItems(del)
                }
            }
        }
    }

    private fun deleteShortcut() {
        val del: List<Item> = mutableListOf<Item>().apply { addAll(root.itemGrid.selected) }
        if (del.isEmpty()) return

        runOnUIThread {
            root.root.confirm("Delete items?", "Delete items and their files?\nWARNING: Deletes files!") {
                onConfirm = {
                    deleteFiles(del)
                }
            }
        }
    }

    private fun importFileShortcut() {
        runOnUIThread {
            val fc = FileChooser()
            val dir = contextPrefs.general.downloadFolder.value
            fc.initialDirectory = File(dir)
            fc.title = "Import files"
            val files = fc.showOpenMultipleDialog(root.currentWindow)
            if (!files.isNullOrEmpty()) {
                contextPrefs.general.downloadFolder.value = files.first().parent
                importFilesDialog(files)
            }
        }
    }

    private fun importFolderShortcut() {
        runOnUIThread {
            val dc = DirectoryChooser()
            val dir = contextPrefs.general.downloadFolder.value
            dc.initialDirectory = File(dir)
            dc.title = "Import directory"
            val folder = dc.showDialog(root.currentWindow)
            if (folder != null) {
                contextPrefs.general.downloadFolder.value = folder.parent
                importFilesDialog(listOf(folder))
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
        if (!folder.isDirectory) return listOf(folder)

        val result = mutableListOf<File>()
        folder.listFiles()?.sortedWith(WindowsExplorerComparator())!!.forEach {
            if (it.isDirectory) {
                result.addAll(getFilesRecursively(it))
            } else {
                result.add(it)
            }
        }
        return result
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

    fun onItemAction(item: Item) {
        if (item is GroupItem) {
            navigateIntoGroup(item)
        } else if (item is FileItem) {
            Desktop.getDesktop().open(item.file)
        }
    }

    private fun navigateIntoGroup(group: GroupItem) {
        val filter = ElementOfFilter(group, false)
        root.navigateForward(
            MenagerieSearch(
                group.menagerie,
                filter.toString(),
                descending = false,
                shuffle = false,
                filters = listOf(filter),
                sortBy = {
                    if (it is FileItem) {
                        it.elementOf?.items?.indexOf(it)
                    } else {
                        it.id
                    }
                }
            )
        )
    }

    override fun stop() {
        super.stop()

        log.info("Stopping app")

        // Close RMI communicator
        rmi.close()

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
        if (!stage.isMaximized) stage.isMaximized = uiPrefs.ui.maximized.value
        stage.maximizedProperty()?.addListener { _, _, newValue ->
            uiPrefs.ui.maximized.value = newValue
        }

        // Width
        if (!stage.isMaximized) stage.width = uiPrefs.ui.width.value
        stage.widthProperty()?.addListener { _, _, newValue ->
            uiPrefs.ui.width.value = newValue.toDouble()
        }

        // Height
        if (!stage.isMaximized) stage.height = uiPrefs.ui.height.value
        stage.heightProperty()?.addListener { _, _, newValue ->
            uiPrefs.ui.height.value = newValue.toDouble()
        }

        // Screen position
        // X
        if (!stage.isMaximized) stage.x = uiPrefs.ui.x.value
        stage.xProperty()?.addListener { _, _, newValue ->
            uiPrefs.ui.x.value = newValue.toDouble()
        }
        // Y
        if (!stage.isMaximized) stage.y = uiPrefs.ui.y.value
        stage.yProperty()?.addListener { _, _, newValue ->
            uiPrefs.ui.y.value = newValue.toDouble()
        }
    }

}

fun main(vararg args: String) {
    launch<MyApp>(*args)
}

fun Node.bindVisibleShortcut(
    key: KeyCode,
    ctrl: Boolean = false,
    alt: Boolean = false,
    shift: Boolean = false,
    type: EventType<KeyEvent> = KeyEvent.KEY_PRESSED,
    desc: String? = null,
    context: String? = null,
    handler: (event: KeyEvent) -> Unit
): Shortcut {
    return bindShortcut(key, ctrl, alt, shift, type, desc, context, handler).also { MyApp.shortcuts.add(it) }
}