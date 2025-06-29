package com.github.iguanastin.app

import com.github.iguanastin.app.context.Edit
import com.github.iguanastin.app.context.MenagerieContext
import com.github.iguanastin.app.menagerie.database.DatabaseMigrator
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import com.github.iguanastin.app.menagerie.duplicates.local.CPUDuplicateFinder
import com.github.iguanastin.app.menagerie.duplicates.local.CUDADuplicateFinder
import com.github.iguanastin.app.menagerie.import.Importer
import com.github.iguanastin.app.menagerie.model.*
import com.github.iguanastin.app.menagerie.search.MenagerieSearch
import com.github.iguanastin.app.menagerie.search.filters.ElementOfFilter
import com.github.iguanastin.app.settings.AppSettings
import com.github.iguanastin.app.settings.WindowSettings
import com.github.iguanastin.app.utils.Versioning
import com.github.iguanastin.app.utils.WindowsExplorerComparator
import com.github.iguanastin.app.utils.expandGroups
import com.github.iguanastin.view.MainView
import com.github.iguanastin.view.Shortcut
import com.github.iguanastin.view.bindShortcut
import com.github.iguanastin.view.dialog.*
import com.github.iguanastin.view.runOnUIThread
import com.sun.jna.platform.FileUtils
import javafx.application.Platform
import javafx.collections.SetChangeListener
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
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import tornadofx.*
import java.awt.Desktop
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.stream.Collectors
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

class MyApp : App(MainView::class, Styles::class) {

    companion object {
        const val VERSION = "1.2.4" // When updating version, update it in pom.xml as well
        private const val githubRoot = "/iguanastin/menageriek"
        const val githubURL = "https://github.com$githubRoot"
        const val githubReleasesURL = "$githubURL/releases/latest"
        private const val githubAPIReleaseURL = "https://api.github.com/repos$githubRoot/releases/latest"

        val shortcuts: MutableList<Shortcut> = mutableListOf()

        val displayTagSorter: (Tag, Tag) -> Int = { t1, t2 ->
            val r = t1.temporary.compareTo(t2.temporary)
            if (r == 0) {
                t1.name.compareTo(t2.name)
            } else {
                r
            }
        }
    }

    private val uiPrefs: WindowSettings = WindowSettings()
    val settings: AppSettings = AppSettings()

    var context: MenagerieContext? = null

    /**
     * Inter-process communicator to avoid having two instances of the app and to enable importing from the browser.
     */
    private lateinit var rmi: MenagerieRMI

    lateinit var root: MainView

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
            settings.database.url.value,
            settings.database.user.value,
            settings.database.pass.value
        ) { context ->
            onMenagerieLoaded(context)

            checkVersionAndPatchNotes()

            context.menagerie.similarityConfidence = settings.duplicate.confidence.value
            settings.duplicate.confidence.changeListeners.add { d -> context.menagerie.similarityConfidence = d }
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

        // Start the HTTP API server
        if (settings.api.enabled.value) {
            context.api.start(settings.api.port.value)
        }

        purgeUnusedTags(context.menagerie)


        context.menagerie.tags.addListener(SetChangeListener { change ->
            val colorRules = context.prefs.tags.autoColorTags
            if (change.wasAdded()) colorRules.applyRulesTo(change.elementAdded)
        })

        runOnUIThread {
            root.context =
                context // TODO main view should be getting context from MyApp instead of holding its own context

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

            if (context.prefs.hidden.tourOnLaunch.value) {
                root.startTour()
                context.prefs.hidden.tourOnLaunch.value = false
            }
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
                    splitString.subList(1, splitString.size)
                        .map {
                            context!!.menagerie.getOrMakeTag(
                                settings.tags.tagAliases.apply(it),
                                temporaryIfNew = true
                            )
                        }

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
            settings.resetToDefaults()
            uiPrefs.resetToDefaults()
        }

        if (parameters.named.containsKey("db")) settings.database.url.value =
            parameters.named["db"] ?: settings.database.url.default
        if (parameters.named.containsKey("db-url")) settings.database.url.value =
            parameters.named["db-url"] ?: settings.database.url.default

        if (parameters.named.containsKey("dbu")) settings.database.user.value =
            parameters.named["dbu"] ?: settings.database.user.value
        if (parameters.named.containsKey("db-user")) settings.database.user.value =
            parameters.named["db-user"] ?: settings.database.user.default

        if (parameters.named.containsKey("dbp")) settings.database.pass.value =
            parameters.named["dbp"] ?: settings.database.pass.default
        if (parameters.named.containsKey("db-pass")) settings.database.pass.value =
            parameters.named["db-pass"] ?: settings.database.pass.default

        if ("--api-only" in parameters.unnamed) exitProcess(0)
    }

    private fun checkVersionAndPatchNotes() {
        // Check if Menagerie should fetch update info
        if (settings.hidden.dontRemindUpdate.value != VERSION) {
            thread(start = true, isDaemon = true, name = "Github Release Checker") { checkGithubForNewRelease() }
        }

        // Check if version was updated since last launch
        val v = settings.hidden.lastLaunchVersion.value
        if (v.isNotBlank() && Versioning.compare(v, VERSION) < 0) {
            runOnUIThread { root.root.add(InfoStackDialog("Patch Notes v$VERSION", PatchNotes.get(VERSION))) }
        }
        settings.hidden.lastLaunchVersion.value = VERSION
    }

    private fun checkGithubForNewRelease() {
        try {
            HttpClientBuilder.create().build().use { client ->
                client.execute(HttpGet(githubAPIReleaseURL)).use { response ->
                    if (response.statusLine.statusCode != 200) {
                        log.error("Failed to get github API release, HTTP code: ${response.statusLine.statusCode} - ${response.statusLine.reasonPhrase}")
                        @Suppress("UseExpressionBody")
                        return@use
                    }

                    response.entity.content.use { content ->
                        val json = loadJsonObject(
                            BufferedReader(InputStreamReader(content)).lines().collect(Collectors.joining("\n"))
                        )

                        val newVersion = json.getString("tag_name")
                            .removePrefix("v") // Get name of latest release tag from JSON API response

                        if (Versioning.compare(VERSION, newVersion) < 0) {
                            runOnUIThread {
                                root.root.add(
                                    ConfirmStackDialog(
                                        header = "Update available",
                                        message = "There is an update available:\n$VERSION -> $newVersion",
                                        url = githubReleasesURL,
                                        cancelText = "Don't remind me",
                                        onCancel = {
                                            settings.hidden.dontRemindUpdate.value = VERSION
                                        }
                                    )
                                )
                            }
                        } else {
                            log.info("App is up to date. Current: $VERSION, fetched: $newVersion")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Error caught trying to fetch latest release info", e)
        }
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

        root.root.bindVisibleShortcut(KeyCode.Q, ctrl = true, desc = "Exit Menagerie", context = "Global") {
            Platform.exit()
        }
    }

    private fun initItemGridKeyHandler() {
        root.itemGrid.apply {
            bindVisibleShortcut(KeyCode.H, ctrl = true, desc = "Open help", context = "Main Screen") {
                root.openHelpDialog()
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
                duplicatesShortcut(true)
            }
            bindVisibleShortcut(
                KeyCode.D,
                ctrl = true,
                alt = true,
                desc = "Find similar files in Menagerie",
                context = "Item List"
            ) {
                duplicatesShortcut(false)
            }
            bindVisibleShortcut(KeyCode.G, ctrl = true, desc = "Group selected items", context = "Item List") {
                groupShortcut()
            }
            bindVisibleShortcut(KeyCode.U, ctrl = true, desc = "Ungroup selected group", context = "Item List") {
                ungroupShortcut()
            }
            bindVisibleShortcut(KeyCode.S, ctrl = true, desc = "Open settings", context = "Main Screen") {
                root.root.add(SettingsDialog(settings))
            }
            bindVisibleShortcut(
                KeyCode.F,
                ctrl = true,
                shift = true,
                desc = "Find similar images online",
                context = "Item List"
            ) {
                findOnlineShortcut()
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
            bindVisibleShortcut(
                KeyCode.D,
                ctrl = true,
                shift = true,
                desc = "Open duplicate resolver dialog",
                context = "Main Screen"
            ) {
                root.openSimilarDialog()
            }
        }
    }

    fun findOnlineShortcut() {
        root.root.add(FindOnlineChooseMatcherDialog(context ?: return, expandGroups(root.itemGrid.selected)))
    }

    fun undoLastEdit() {
        if (context?.edits?.isEmpty() == true) return
        val peek = context?.edits?.peek() ?: return // Return if there are no edits in the stack

        root.root.confirm(
            header = "Undo edit",
            message = peek.toString()
        ).onConfirm = {
            val edit = context?.undoLastEdit()
            if (edit?.state != Edit.State.Undone) {
                context?.edits?.push(edit)
                root.root.confirm("Error", "Failed to undo last edit")
            }
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
            val group = menagerie.createGroup(title)
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

            Platform.runLater {
                root.itemGrid.select(group)
            }
        }))
    }

    fun duplicatesShortcut(inSelected: Boolean) {
        val menagerie = context?.menagerie ?: return

        val first = expandGroups(root.itemGrid.selected)
        val second = if (inSelected) first else expandGroups(menagerie.items)

        val progress =
            ProgressDialog(header = "Finding duplicates", message = "${first.size} against ${second.size} items")
        root.root.add(progress)

        thread(start = true, isDaemon = true, name = "DuplicateFinder") {
            try {
                val pairs = if (settings.duplicate.enableCuda.value) {
                    CUDADuplicateFinder.findDuplicates(
                        first,
                        second,
                        settings.duplicate.confidence.value.toFloat(),
                        100000
                    )
                } else {
                    CPUDuplicateFinder.findDuplicates(
                        first,
                        second,
                        settings.duplicate.confidence.value
                    )
                }
                var new = 0
                pairs.forEach { p ->
                    menagerie.addSimilarity(p)
                    new++
                }

                runOnUIThread {
                    progress.close()
                    root.root.add(InfoStackDialog(header = "Found duplicates", message = "Found $new new duplicate(s)"))
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
                importer.fromWeb(url, folder, tags)
            }
        }

        val folderPath = settings.general.downloadFolder.value
        if (folderPath.isBlank() || !File(folderPath).isDirectory) {
            val dc = DirectoryChooser()
            dc.title = "Download into folder"
            download(dc.showDialog(root.currentWindow))
        } else {
            download(File(folderPath))
        }
    }

    private fun renameGroupShortcut() {
        val group = root.itemGrid.selected.singleOrNull() ?: return
        if (group !is GroupItem) return

        root.root.add(TextInputDialog(header = "Rename group", text = group.title, onAccept = {
            context?.groupRenameEdit(group, it)
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

    fun importFileShortcut() {
        runOnUIThread {
            val fc = FileChooser()
            val dir = settings.general.downloadFolder.value
            fc.initialDirectory = if (dir.isNotEmpty()) File(dir) else null
            if (!fc.initialDirectory.exists() || !fc.initialDirectory.isDirectory) fc.initialDirectory = null
            fc.title = "Import files"
            val files = fc.showOpenMultipleDialog(root.currentWindow)
            if (!files.isNullOrEmpty()) {
                importFilesDialog(files)
            }
        }
    }

    fun importFolderShortcut() {
        runOnUIThread {
            val dc = DirectoryChooser()
            val dir = settings.general.downloadFolder.value
            dc.initialDirectory = if (dir.isNotEmpty()) File(dir) else null
            dc.title = "Import directory"
            val folder = dc.showDialog(root.currentWindow)
            if (folder != null) {
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
                        getFilesRecursively(file).forEach { if (!menagerie.hasFile(it)) importer.fromLocal(it) }
                    } else {
                        importer.fromLocal(file)
                    }
                }
            }
            asGroup = {
                val title = if (files.size == 1 && files.first().isDirectory) files.first().name else "Unnamed Group"
                val group = importer.createGroup(title)

                files.forEach { file ->
                    if (file.isDirectory) {
                        getFilesRecursively(file).forEach { if (!menagerie.hasFile(it)) importer.fromLocal(it, group) }
                    } else {
                        if (!menagerie.hasFile(file)) importer.fromLocal(file, group)
                    }
                }
            }
            dirsAsGroups = {
                for (file in files) {
                    if (file.isDirectory) {
                        val group = importer.createGroup(file.name)
                        getFilesRecursively(file).forEach { if (!menagerie.hasFile(it)) importer.fromLocal(it, group) }
                    } else if (!menagerie.hasFile(file)) {
                        importer.fromLocal(file)
                    }
                }
            }
            if (!scene.window.isFocused) scene.window.requestFocus()
        }
    }

    private fun getFilesRecursively(folder: File): List<File> {
        if (!folder.isDirectory) return listOf(folder)

        val result = mutableListOf<File>()
        folder.listFiles()!!.sortedWith(WindowsExplorerComparator()).forEach {
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
            val fileUtils = FileUtils.getInstance()
            if (fileUtils.hasTrash()) {
                fileUtils.moveToTrash(item.file)
            } else {
                item.file.delete()
            }
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
                sortBy = { // TODO the sortBy is lost if you search again or manually search group contents
                    if (it is FileItem) {
                        it.elementOf?.items?.indexOf(it) ?: it.id
                    } else {
                        it.id
                    }
                }
            )
        )
    }

    override fun stop() {
        VideoItem.releaseThumbnailer()
        root.onClose()
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
            val migrator = DatabaseMigrator(database)

            val load: () -> Unit = {
                runOnUIThread {
                    val progress = ProgressDialog(header = "Loading database", message = "($dbURL)")
                    root.root.add(progress)

                    thread(name = "Menagerie Loader", start = true) {
                        try {
                            val menagerie = database.loadMenagerie { msg -> runOnUIThread { progress.message = msg } }
                            val importer = Importer(menagerie, settings)
                            runOnUIThread { progress.close() }

                            after?.invoke(MenagerieContext(menagerie, importer, database, settings))
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
                            migrator.migrate()
                            runOnUIThread { progress.close() }

                            load()
                        } catch (e: Exception) {
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

            if (migrator.needsMigration()) {
                if (migrator.canMigrate()) {
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
                    if (database.version < MenagerieDatabase.MINIMUM_DATABASE_VERSION) {
                        error(
                            title = "Incompatible database",
                            header = "Database v${database.version} is not supported!",
                            content = "Update to database version 8 with the latest Java Menagerie application\n-OR-\nCreate a new database",
                            owner = stage
                        )
                    } else {
                        error(
                            title = "Incompatible database",
                            header = "Database v${database.version} is not supported",
                            content = "Maximum supported database version for Menagerie v$VERSION is database v${MenagerieDatabase.REQUIRED_DATABASE_VERSION}",
                            owner = stage
                        )
                    }
                    database.close()
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
    autoConsume: Boolean = true,
    handler: (event: KeyEvent) -> Unit
): Shortcut {
    return bindShortcut(
        key,
        ctrl,
        alt,
        shift,
        type,
        desc,
        context,
        autoConsume,
        handler
    ).also { MyApp.shortcuts.add(it) }
}