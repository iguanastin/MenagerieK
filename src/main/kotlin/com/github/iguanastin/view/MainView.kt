package com.github.iguanastin.view

import com.github.iguanastin.app.MyApp
import com.github.iguanastin.app.PatchNotes
import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.bindVisibleShortcut
import com.github.iguanastin.app.context.MenagerieContext
import com.github.iguanastin.app.menagerie.import.Import
import com.github.iguanastin.app.menagerie.model.*
import com.github.iguanastin.app.menagerie.search.FilterParseException
import com.github.iguanastin.app.menagerie.search.MenagerieSearch
import com.github.iguanastin.app.menagerie.search.SearchHistory
import com.github.iguanastin.app.menagerie.search.filters.ElementOfFilter
import com.github.iguanastin.app.menagerie.search.filters.FilterFactory
import com.github.iguanastin.app.utils.*
import com.github.iguanastin.view.dialog.*
import com.github.iguanastin.view.factories.ItemCellFactory
import com.github.iguanastin.view.factories.TagCellFactory
import com.github.iguanastin.view.nodes.*
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.SetChangeListener
import javafx.event.EventHandler
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import mu.KotlinLogging
import tornadofx.*

private val myLog = KotlinLogging.logger {}

class MainView : View("Menagerie - v${MyApp.VERSION}") {

    lateinit var itemGrid: MultiSelectGridView<Item>
    lateinit var dragOverlay: BorderPane

    private lateinit var itemDisplay: MultiTypeItemDisplay
    private lateinit var tagView: ListView<Tag>
    private lateinit var editTagsPane: TagEditPane
    private lateinit var searchModifiersHbox: HBox
    private lateinit var searchTextField: TextField
    private lateinit var backButton: Button
    private lateinit var descendingToggle: ToggleButton
    private lateinit var openGroupsToggle: ToggleButton
    private lateinit var shuffleToggle: ToggleButton
    private lateinit var selectedCountLabel: Label
    private lateinit var searchButton: Button
    private lateinit var importsButton: Button
    private lateinit var similarButton: Button

    private val searchProperty = objectProperty<MenagerieSearch>()
    private val search: MenagerieSearch?
        get() = searchProperty.get()

    // Should probably be in MyApp
    private val history: ObservableList<SearchHistory> = observableListOf()

    private val imports: ObservableList<Import> = observableListOf()

    private val myApp = (app as MyApp)

    var context: MenagerieContext? = null
        set(value) {
            field = value

            if (value != null) onContextAdded(value)
        }

    private val shuffleIconURL = MainView::class.java.getResource("/imgs/shuffle.png")?.toExternalForm()
    private val groupsIconURL = MainView::class.java.getResource("/imgs/opengroups.png")?.toExternalForm()
    private val orderIconURL = MainView::class.java.getResource("/imgs/descending.png")?.toExternalForm()

    override val root = topenabledstackpane {
        focusingstackpane {
            borderpane {
//                top {
//                    initMenuBar() // Steals focus when alt tabbing, still
//                }
                center {
                    itemDisplay = multitypeitemdisplay { paddingLeft = 5; paddingTop = 5; paddingBottom = 5 }
                }
                left {
                    tagView = listview {
                        isFocusTraversable = false
                        cellFactory = TagCellFactory().apply {
                            onTagClick = {
                                robotSearch(text = it.name, descending = true, expandGroups = false, shuffled = false)
                            }
                        }
                        maxWidth = 200.0
                        minWidth = 200.0
                    }
                }
                right {
                    borderpane {
                        padding = insets(4)
                        top {
                            initSearchPane()
                        }
                        center {
                            itemGrid = multiselectgridview {
                                addClass(Styles.mainItemGridView)
                            }
                        }
                        bottom {
                            borderpane {
                                left { importsButton = button("Imports: 0") }
                                right { similarButton = button("Similar: 0") }
                            }
                        }
                    }
                }
            }

            focuses = itemGrid

            editTagsPane = tageditpane {
                addEventFilter(KeyEvent.KEY_PRESSED) { event ->
                    if (event.code == KeyCode.ESCAPE) hide()
                }

                hide()

                Platform.runLater { selectedItems = itemGrid.selected }
            }

            dragOverlay = borderpane {
                hide()
                addClass(Styles.transparentOverlay)
                center {
                    label("Drop here to import\n\nFiles, Folders, Urls") {
                        addClass(Styles.dragDropDialog)
                    }
                }
            }
        }
    }

    init {
        initItemGrid()
        initViewListener()
        initDisplayLastSelectedListener()
        initEditTagsDialog()
        initTagsListener()
        initRootKeyPressedListener()
        initSearchListener()
        initSearchFieldAutoComplete()
        initVideoDisplaySettingsBindings()
    }

    private fun EventTarget.initSearchPane(op: VBox.() -> Unit = {}): VBox {
        return vbox(5.0) {
            padding = insets(5.0)
            hbox(5.0) {
                backButton = button("\uD83E\uDC44") {
                    disableWhen(history.sizeProperty.isEqualTo(0))
                    onActionConsuming { navigateBack() }
                }
                searchTextField = textfield {
                    hgrow = Priority.ALWAYS
                    promptText = "Search"
                }
            }
            borderpane {
                left {
                    searchModifiersHbox = hbox(5.0) {
                        alignment = Pos.CENTER_LEFT
                        descendingToggle = togglebutton(selectFirst = true) {
                            graphic = ImageView(orderIconURL)
                            tooltip("Toggle descending")
                        }
                        openGroupsToggle = togglebutton(selectFirst = false) {
                            graphic = ImageView(groupsIconURL)
                            tooltip("Toggle show group elements")
                        }
                        shuffleToggle = togglebutton(selectFirst = false) {
                            graphic = ImageView(shuffleIconURL)
                            tooltip("Toggle shuffle results")
                        }
                    }
                }
                right {
                    hbox(5.0) {
                        alignment = Pos.CENTER_LEFT
                        selectedCountLabel = label("0/0") {
                            Platform.runLater {
                                textProperty().bind(
                                    itemGrid.selected.sizeProperty.asString().concat("/")
                                        .concat(itemGrid.items.sizeProperty.asString())
                                )
                            }
                        }
                        searchButton = button("Search")
                    }
                }
            }
        }
    }

    private fun EventTarget.initMenuBar(op: MenuBar.() -> Unit = {}): MenuBar {
        return menubar {
            Platform.runLater {
                // The amount of stupid fixes and workarounds required to make the menubar work is infuriating
                // TODO this may cause problems
                scene.addEventHandler(KeyEvent.KEY_PRESSED, EventHandler { e ->
                    if (e.isAltDown && isDisabled) e.consume()
                })
            }

            val enabled = disabledProperty().not() // Items have to be individually disabled
            menu("_File") {
                item("Import File(s)", "CTRL+I") {
                    onActionConsuming(enabled) { myApp.importFileShortcut() }
                }
                item("Import Folders(s)", "CTRL+SHIFT+I") {
                    onActionConsuming(enabled) { myApp.importFolderShortcut() }
                }
                separator()
                item("_Settings", "CTRL+S") {
                    onActionConsuming(enabled) { root.add(SettingsDialog(myApp.settings)) }
                }
                separator()
                item("Quit", "CTRL+Q") {
                    onActionConsuming(enabled) { Platform.exit() }
                }
            }
            menu("_Edit") {
                item("_Tags", "CTRL+E") {
                    onActionConsuming(enabled) { editTagsPane.apply { show(); requestFocus() } }
                }
                item("_Group", "CTRL+G") {
                    onActionConsuming(enabled) { myApp.groupShortcut() }
                }
                item("_Ungroup", "CTRL+U") {
                    onActionConsuming(enabled) { myApp.ungroupShortcut() }
                }
                separator()
                item("_Undo", "CTRL+Z") {
                    onActionConsuming(enabled) { myApp.undoLastEdit() }
                }
            }
            menu("_Similar") {
                item("In _Menagerie", "CTRL+ALT+D") {
                    onActionConsuming(enabled) { myApp.duplicatesShortcut(false) }
                }
                item("In _Selected", "CTRL+D") {
                    onActionConsuming(enabled) { myApp.duplicatesShortcut(true) }
                }
                item("_Online", "CTRL+SHIFT+F") {
                    onActionConsuming(enabled) { myApp.findOnlineShortcut() }
                }
            }
            menu("_View") {
                item("_Help", "CTRL+H") {
                    onActionConsuming(enabled) { openHelpDialog() }
                }
                item("_Tags", "CTRL+T") {
                    onActionConsuming(enabled) { displayTagsDialog() }
                }
                item("_Similar", "CTRL+SHIFT+D") {
                    onActionConsuming(enabled) { openSimilarDialog() }
                }
                item("_Imports") {
                    onActionConsuming(enabled) { openImportsDialog(context ?: return@onActionConsuming) }
                }
            }
        }
    }

    fun startTour() {
        Tour(currentStage!!).apply {
            addStop(
                TourStop(
                    null,
                    "Welcome to Menagerie!\n\n Use the left and right arrow keys to take a tour, or press Escape to exit the tour. You can always restart the tour from the help menu (Ctrl+H)"
                )
            )
            addStop(
                TourStop(
                    null, "To get started, import some files by:\n\n" +
                            "Dragging and dropping files/folders from the file explorer, or the web\n" +
                            "or, pressing Ctrl+I to import individual files\n" +
                            "or, pressing Ctrl+Shift+I to import all files in a folder"
                )
            )
            addStop(
                TourStop(
                    backButton,
                    "Navigate back to previous search"
                )
            )
            addStop(
                TourStop(
                    searchTextField,
                    "Filter by space-separated tags and types. Exclude search terms by prepending with a \"-\".\n\n" +
                            "E.g:\n" +
                            "    warm_color background person -is:video\n\n" +
                            "While typing a search term, press Ctrl+Space to quickly autocomplete the tag name/term",
                    TourStop.TextPos.BOTTOM_RIGHT
                )
            )
            addStop(
                TourStop(
                    searchModifiersHbox,
                    "Toggle descending order, whether to exclude group elements from search, and shuffle results"
                )
            )
            addStop(
                TourStop(
                    selectedCountLabel,
                    "Number of items selected/number of items in search",
                    TourStop.TextPos.BOTTOM_LEFT
                )
            )
            addStop(
                TourStop(
                    searchButton,
                    "Apply filters and order/etc.",
                    TourStop.TextPos.BOTTOM_LEFT
                )
            )
            addStop(
                TourStop(
                    itemGrid,
                    "The filtered results will appear here, where you can select and modify them with the context menu, or by using keyboard shortcuts\n\n" +
                            "Select one/multiple items and right click to see actions to perform on those items",
                    TourStop.TextPos.LEFT
                )
            )
            addStop(
                TourStop(
                    importsButton,
                    "Imported files will be queued here as they are added",
                    TourStop.TextPos.TOP_LEFT
                )
            )
            addStop(
                TourStop(
                    similarButton,
                    "As files are imported, they are checked to see if they are similar or duplicates of files that have already been imported\n\n" +
                            "These cases are cleared when the app closes, so be sure to resolve them first",
                    TourStop.TextPos.TOP_LEFT
                )
            )
            addStop(
                TourStop(
                    itemDisplay,
                    "When you select an item, it will be displayed here\n\n" +
                            "Images can be dragged and zoomed; click once to reset zoom or zoom to 1:1 pixel scale\n\n" +
                            "Click the info text in the bottom corner to toggle more details about the item",
                    TourStop.TextPos.RIGHT
                )
            )
            addStop(
                TourStop(
                    tagView,
                    "Tags of the current item are listed here, where you can change their color by right clicking them",
                    TourStop.TextPos.RIGHT
                )
            )
            addStop(
                TourStop(
                    null,
                    "That's it\n\nMake sure to check out the help menu (Ctrl+H) for general help, app information, and keyboard shortcuts\n\nEnjoy!"
                )
            )

            onEnd = { openHelpDialog() }

            start()
        }
    }

    private fun onContextAdded(context: MenagerieContext) {
        initSimilarButton(context)
        initImportsButton(context)

        editTagsPane.context =
            context // TODO make context an observable property so that I don't have to remember to pass it here?
    }

    private fun initVideoDisplaySettingsBindings() {
        // Bi-directionally bind main video mute and repeat to settings
        val settings = myApp.settings
        itemDisplay.videoDisplay.isMuted = settings.hidden.videoMute.value
        itemDisplay.videoDisplay.muteProperty.addListener { _, _, new ->
            if (settings.hidden.videoMute.value != new) settings.hidden.videoMute.value = new
        }
        settings.hidden.videoMute.changeListeners.add {
            if (itemDisplay.videoDisplay.isMuted != it) itemDisplay.videoDisplay.isMuted = it
        }

        itemDisplay.videoDisplay.isRepeat = settings.hidden.videoRepeat.value
        itemDisplay.videoDisplay.repeatProperty.addListener { _, _, new ->
            if (settings.hidden.videoRepeat.value != new) settings.hidden.videoRepeat.value = new
        }
        settings.hidden.videoRepeat.changeListeners.add {
            if (itemDisplay.videoDisplay.isRepeat != it) itemDisplay.videoDisplay.isRepeat = it
        }
    }

    fun openHelpDialog() {
        root.helpDialog().apply {
            onRequestPatchNotes =
                { root.add(InfoStackDialog("Patch Notes v${MyApp.VERSION}", PatchNotes.get(MyApp.VERSION))) }
            onRequestTour = { startTour() }
        }
    }

    private fun initSimilarButton(context: MenagerieContext) {
        runOnUIThread { updateSimilarButtonState() }
        context.menagerie.similarPairs.addListener(ListChangeListener { change ->
            while (change.next()) {
                runOnUIThread { updateSimilarButtonState() }
            }
        })

        similarButton.onActionConsuming { openSimilarDialog() }
    }

    private fun updateSimilarButtonState() {
        val count = context?.menagerie?.similarPairs?.size ?: 0
        similarButton.text = "Similar: $count"
        similarButton.toggleClass(Styles.blueBase, count > 0)
    }

    fun openSimilarDialog() {
        val pairs = context?.menagerie?.similarPairs ?: return
        add(DuplicateResolverDialog(pairs, context).apply {
            onClose = { context!!.menagerie.purgeSimilarNonDupes() }
        })
    }

    private fun initImportsButton(context: MenagerieContext) {
        importsButton.onActionConsuming { openImportsDialog(context) }

        context.menagerie.imports.addListener(ListChangeListener { change ->
            while (change.next()) change.addedSubList.forEach { imports.add(it) }
        })

        imports.addAll(context.menagerie.imports)

        val update = ChangeListener { _, _, new -> if (new in Import.finishedStates) updateImportsButton() }
        imports.forEach { it.status.addListener(update) }
        imports.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.addedSubList.forEach { it.status.addListener(update) }
                updateImportsButton()
            }
        })
        updateImportsButton()
    }

    private fun openImportsDialog(context: MenagerieContext) {
        root.add(ImportQueueDialog(imports, context))
    }

    private fun updateImportsButton() {
        runOnUIThread {
            val count = imports.count { it.isReadyOrRunning() }
            importsButton.text = "Imports: $count"
            importsButton.toggleClass(Styles.blueBase, count > 0)
        }
    }

    private fun initSearchFieldAutoComplete() {
        searchTextField.bindAutoComplete { predict ->
            var word = predict.lowercase()
            val exclude = word.startsWith('-')
            if (exclude) word = word.substring(1)
            val maxResults = 8

            val result = mutableListOf<String>()

            val search = search
            if (search != null) {
                result.addAll(search.menagerie.tags.sortedByDescending { it.frequency }
                    .filter { tag -> tag.name.startsWith(word) }
                    .map { it.name })
            }

            FilterFactory.filterPrefixes.forEach { if (it.startsWith(word)) result.add(it) }

            return@bindAutoComplete result.subList(0, maxResults.coerceAtMost(result.size))
                .map { if (exclude) "-$it" else it }
        }
    }

    private fun robotSearch(
        text: String = "",
        descending: Boolean? = null,
        expandGroups: Boolean? = null,
        shuffled: Boolean? = null
    ) {
        searchTextField.text = text
        if (descending != null) descendingToggle.isSelected = descending
        if (expandGroups != null) openGroupsToggle.isSelected = expandGroups
        if (shuffled != null) shuffleToggle.isSelected = shuffled
        applySearch()
    }

    private fun applySearch() {
        val view = search ?: return
        val text = searchTextField.text.trim()
        val filters = try {
            FilterFactory.parseFilters(text, view.menagerie, !openGroupsToggle.isSelected)
        } catch (e: FilterParseException) {
            myLog.warn(e.message)
            information(
                "Error while parsing filters",
                e.message,
                ButtonType.OK,
                owner = currentWindow
            )
            return
        }

        navigateForward(
            MenagerieSearch(
                view.menagerie,
                searchTextField.text,
                descendingToggle.isSelected,
                shuffleToggle.isSelected,
                filters
            )
        )
        runOnUIThread { itemGrid.requestFocus() }
    }

    private fun initSearchListener() {
        searchButton.onActionConsuming { applySearch() }

        searchTextField.apply {
            onAction = searchButton.onAction

            bindVisibleShortcut(KeyCode.G, ctrl = true, desc = "Toggle expand groups", context = "Search Field") {
                openGroupsToggle.isSelected = !openGroupsToggle.isSelected
            }
            bindVisibleShortcut(KeyCode.D, ctrl = true, desc = "Toggle sort descending", context = "Search Field") {
                descendingToggle.isSelected = !descendingToggle.isSelected
            }
            bindVisibleShortcut(KeyCode.S, ctrl = true, desc = "Toggle shuffle results", context = "Search Field") {
                shuffleToggle.isSelected = !shuffleToggle.isSelected
            }
        }
    }

    fun navigateBack() {
        val back = history.removeLastOrNull() ?: return
        searchProperty.set(back.view)

        itemGrid.selected.clearAndAddAll(back.selected)
        if (back.lastSelected == null) {
            itemGrid.lastSelectedIndex = -1
        } else {
            itemGrid.ensureVisible(back.lastSelected)
            itemGrid.lastSelectedIndex = itemGrid.items.indexOf(back.lastSelected)
        }
    }

    fun navigateForward(view: MenagerieSearch) {
        val old = searchProperty.get()
        if (old != null) history.add(
            SearchHistory(
                old,
                itemGrid.selected.toList(),
                itemGrid.items.getOrNull(itemGrid.lastSelectedIndex)
            )
        )
        searchProperty.set(view)
    }

    private fun initItemGrid() {
        itemGrid.bindVisibleShortcut(
            KeyCode.M,
            desc = "Mute video player",
            context = "Main Screen",
            autoConsume = false
        ) { event ->
            if (itemDisplay.item is VideoItem) {
                event.consume()
                itemDisplay.videoDisplay.muteProperty.toggle()
            }
        }
        itemGrid.bindVisibleShortcut(
            KeyCode.SPACE,
            desc = "Pause video player",
            context = "Main Screen",
            autoConsume = false
        ) { event ->
            if (itemDisplay.item is VideoItem) {
                event.consume()
                itemDisplay.videoDisplay.pausedProperty.toggle()
            }
        }

        itemGrid.items.addListener(ListChangeListener { change ->
            while (change.next()) {
                if (change.addedSize == 1 && itemGrid.items[0] == change.addedSubList[0] && itemGrid.selected.size == 1 && itemGrid.items.indexOf(
                        itemGrid.selected[0]
                    ) == 1
                ) {
                    itemGrid.select(itemGrid.items.first())
                }
            }
        })

        itemGrid.cellFactory = ItemCellFactory.factory {
            addEventHandler(MouseEvent.MOUSE_CLICKED) { event ->
                if (event.button == MouseButton.PRIMARY && event.clickCount == 2) {
                    event.consume()
                    myApp.onItemAction(item)
                }
            }

            addEventHandler(MouseEvent.DRAG_DETECTED) { event ->
                if (item in itemGrid.selected) {
                    event.consume()
                    val db = startDragAndDrop(*TransferMode.ANY)
                    if (item is ImageItem && (item as ImageItem).file.extension == "gif") { // TODO: Temporary solution, GIF thumbnails should be made compatible instead of dealing with this case like this
                        db.dragView = Item.defaultThumbnail
                    } else {
                        val thumb = item.getThumbnail()
                        thumb.want(db) {
                            db.dragView = it.image
                            thumb.unWant(db)
                        }
                    }
                    db.putFiles(expandGroups(itemGrid.selected).filterIsInstance<FileItem>().map { it.file }
                        .toMutableList())
                } else {
                    itemGrid.selected.clear()
                }
            }

            contextmenu {
                item("Edit tags") {
                    onActionConsuming { showEditTagsPane() }
                }
                val syncGroupTags = item("Sync tags") {
                    onActionConsuming {
                        val i = itemGrid.selected.firstOrNull()
                        if (itemGrid.selected.size == 1 && i is GroupItem) {
                            i.items.forEach { e ->
                                e.tags.forEach { i.addTag(it) }
                            }
                        }
                    }
                }
                item("Copy Tags") {
                    onActionConsuming { itemGrid.selected.firstOrNull()?.copyTagsToClipboard() }
                }
                item("Paste Tags") {
                    onActionConsuming { itemGrid.selected.firstOrNull()?.pasteTagsFromClipboard(context) }
                }
                separator()
                val open = item("Open") {
                    onActionConsuming {
                        myApp.onItemAction(itemGrid.selected.singleOrNull() ?: return@onActionConsuming)
                    }
                }
                val showInExplorer = item("Show in Explorer") {
                    onActionConsuming {
                        val i = itemGrid.selected.firstOrNull()
                        if (itemGrid.selected.size == 1 && i is FileItem) {
                            Runtime.getRuntime().exec("explorer.exe /select,${i.file.absolutePath}")
                        }
                    }
                }
                separator()
                val goToGroup = item("Go to Group") {
                    onActionConsuming {
                        val i = itemGrid.selected.firstOrNull()
                        if (itemGrid.selected.size == 1 && i is FileItem && i.elementOf != null) {
                            openGroupsToggle.isSelected = false
                            shuffleToggle.isSelected = false

                            navigateForward(
                                MenagerieSearch(
                                    i.menagerie, "", descendingToggle.isSelected, shuffleToggle.isSelected, listOf(
                                        ElementOfFilter(null, true)
                                    )
                                )
                            )
                            itemGrid.select(i.elementOf!!)
                        }
                    }
                }
                val removeFromGroup = item("Remove from Group") {
                    onActionConsuming {
                        val i = itemGrid.selected.firstOrNull()
                        if (itemGrid.selected.size == 1 && i is FileItem && i.elementOf != null) {
                            i.elementOf!!.removeItem(i)
                        }
                    }
                }
                val ungroup = item("Ungroup") {
                    onActionConsuming { myApp.ungroupShortcut() }
                }
                val group = item("Group") {
                    onActionConsuming { myApp.groupShortcut() }
                }
                val dupesGroup = menu("Duplicates") {
                    item("Find in Menagerie") {
                        onActionConsuming { myApp.duplicatesShortcut(false) }
                    }
                    item("Find in selected") {
                        onActionConsuming { myApp.duplicatesShortcut(true) }
                    }
                    item("Find online") {
                        onActionConsuming { myApp.findOnlineShortcut() }
                    }
                }

                onShown = EventHandler { event ->
                    event.consume()
                    val onlyOne = itemGrid.selected.size == 1
                    val first = itemGrid.selected.first()

                    syncGroupTags.isVisible = onlyOne && first is GroupItem
                    showInExplorer.isVisible = onlyOne && first is FileItem
                    goToGroup.isVisible = onlyOne && first is FileItem && first.elementOf != null
                    removeFromGroup.isVisible = goToGroup.isVisible
                    ungroup.isVisible = onlyOne && first is GroupItem
                    group.isVisible = !onlyOne
                    dupesGroup.isVisible = itemGrid.selected.isNotEmpty()
                    open.isVisible = onlyOne
                }
                itemGrid.selected.addListener(InvalidationListener {
                    hide()
                })
            }
        }
    }

    private fun initViewListener() {
        searchProperty.addListener { _, oldValue, newValue ->
            oldValue?.close()
            itemGrid.items.clear()

            if (newValue != null) {
                newValue.bindTo(itemGrid.items)
                if (itemGrid.items.isNotEmpty()) itemGrid.select(itemGrid.items.first())

                searchTextField.text = newValue.searchString

                descendingToggle.isSelected = newValue.descending

                openGroupsToggle.isSelected = true
                for (filter in newValue.filters) {
                    if (filter is ElementOfFilter && filter.group == null && filter.exclude) {
                        openGroupsToggle.isSelected = false
                        break
                    }
                }

                shuffleToggle.isSelected = newValue.shuffle
            }
        }
    }

    private fun initDisplayLastSelectedListener() {
        itemGrid.selected.addListener(InvalidationListener {
            itemDisplay.item = itemGrid.selected.lastOrNull()
        })
    }

    private fun initEditTagsDialog() {
        editTagsPane.visibleProperty().addListener(ChangeListener { _, _, newValue ->
            if (!newValue) itemGrid.requestFocus()
        })
    }

    private fun initRootKeyPressedListener() {
        root.bindShortcut(KeyCode.ESCAPE) {
            itemGrid.requestFocus()
        }
    }

    fun displayTagsDialog() {
        var dialog: TagSearchDialog? = null
        dialog = TagSearchDialog(search?.menagerie ?: return) {
            dialog?.close()
            robotSearch(it.name)
        }
        root.add(dialog)
    }

    fun focusSearchField() {
        searchTextField.selectAll()
        searchTextField.requestFocus()
    }

    fun showEditTagsPane() {
        editTagsPane.show()
        editTagsPane.requestFocus()
    }

    private fun initTagsListener() {
        // Listener is attached to currently displayed/previewed item
        val displayTagChangeListener = SetChangeListener<Tag> { change ->
            runOnUIThread {
                if (change.wasRemoved()) tagView.items.remove(change.elementRemoved)
                if (change.wasAdded()) tagView.items.addSorted(change.elementAdded, MyApp.displayTagSorter)
            }
        }

        // Update display tags and tag listeners when displayed item changes
        itemDisplay.itemProperty.addListener(ChangeListener { _, old, new ->
            old?.tags?.removeListener(displayTagChangeListener)
            new?.tags?.addListener(displayTagChangeListener)

            runOnUIThread {
                tagView.items.clear()
                if (new != null) tagView.items.addAll(new.tags.sortedWith(MyApp.displayTagSorter))
            }
        })
    }

    fun onClose() {
        itemDisplay.release()
    }

}