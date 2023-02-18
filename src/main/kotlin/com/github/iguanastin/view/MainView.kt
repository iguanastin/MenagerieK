package com.github.iguanastin.view

import com.github.iguanastin.app.MyApp
import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.bindVisibleShortcut
import com.github.iguanastin.app.context.MenagerieContext
import com.github.iguanastin.app.menagerie.model.*
import com.github.iguanastin.app.menagerie.search.FilterParseException
import com.github.iguanastin.app.menagerie.search.MenagerieSearch
import com.github.iguanastin.app.menagerie.search.SearchHistory
import com.github.iguanastin.app.menagerie.search.filters.ElementOfFilter
import com.github.iguanastin.app.menagerie.search.filters.FilterFactory
import com.github.iguanastin.app.utils.*
import com.github.iguanastin.view.dialog.DuplicateResolverDialog
import com.github.iguanastin.view.dialog.ImportNotification
import com.github.iguanastin.view.dialog.ImportQueueDialog
import com.github.iguanastin.view.dialog.TagSearchDialog
import com.github.iguanastin.view.factories.ItemCellFactory
import com.github.iguanastin.view.factories.TagCellFactory
import com.github.iguanastin.view.nodes.*
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.SetChangeListener
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import mu.KotlinLogging
import tornadofx.*
import java.io.PrintWriter
import java.io.StringWriter

private val log = KotlinLogging.logger {}

class MainView : View("Menagerie") {

    lateinit var itemDisplay: MultiTypeItemDisplay
    lateinit var itemGrid: MultiSelectGridView<Item>
    lateinit var tagView: ListView<Tag>
    lateinit var dragOverlay: BorderPane
    lateinit var applyTagEdit: Button
    lateinit var editTags: TextField
    lateinit var editTagsPane: BorderPane
    lateinit var searchModifiersHbox: HBox

    private lateinit var searchTextField: TextField
    private lateinit var backButton: Button
    private lateinit var descendingToggle: ToggleButton
    private lateinit var openGroupsToggle: ToggleButton
    private lateinit var shuffleToggle: ToggleButton
    private lateinit var selectedCountLabel: Label
    private lateinit var searchButton: Button
    private lateinit var importsButton: Button
    private lateinit var similarButton: Button

    private val searchProperty: ObjectProperty<MenagerieSearch?> = SimpleObjectProperty(null)
    private val currentSearch: MenagerieSearch?
        get() = searchProperty.get()

    // Should probably be in MyApp
    val imports: ObservableList<ImportNotification> = observableListOf()

    // Should probably be in MyApp
    val similar: ObservableList<SimilarPair<Item>> = observableListOf()

    // Should probably be in MyApp
    private val history: ObservableList<SearchHistory> = observableListOf()

    private val myApp = (app as MyApp)

    var context: MenagerieContext? = null
        set(value) {
            field = value

            // Bi-directionally bind main video mute and repeat to prefs
            value?.apply {
                itemDisplay.videoDisplay.isMuted = prefs.hidden.videoMute.value
                itemDisplay.videoDisplay.muteProperty.addListener { _, _, new ->
                    if (prefs.hidden.videoMute.value != new) prefs.hidden.videoMute.value = new
                }
                prefs.hidden.videoMute.changeListeners.add {
                    if (itemDisplay.videoDisplay.isMuted != it) itemDisplay.videoDisplay.isMuted = it
                }

                itemDisplay.videoDisplay.isRepeat = prefs.hidden.videoRepeat.value
                itemDisplay.videoDisplay.repeatProperty.addListener { _, _, new ->
                    if (prefs.hidden.videoRepeat.value != new) prefs.hidden.videoRepeat.value = new
                }
                prefs.hidden.videoRepeat.changeListeners.add {
                    if (itemDisplay.videoDisplay.isRepeat != it) itemDisplay.videoDisplay.isRepeat = it
                }
            }
        }

    override val root = topenabledstackpane {
        focusingstackpane {
            borderpane {
                center {
                    itemDisplay = multitypeitemdisplay {
                        paddingLeft = 5
                        paddingTop = 5
                        paddingBottom = 5
                    }
                }
                left {
                    borderpane {
                        center {
                            tagView = listview {
                                isFocusTraversable = false
                                cellFactory = TagCellFactory().apply {
                                    onTagClick = {
                                        robotSearch(
                                            text = it.name,
                                            descending = true,
                                            expandGroups = false,
                                            shuffled = false
                                        )
                                    }
                                }
                                maxWidth = 200.0
                                minWidth = 200.0
                            }
                        }
                    }
                }
                right {
                    borderpane {
                        padding = insets(4)
                        top {
                            vbox(5.0) {
                                padding = insets(5.0)
                                hbox(5.0) {
                                    backButton = button("\uD83E\uDC44") {
                                        isDisable = true
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
                                                graphic = ImageView(
                                                    MainView::class.java.getResource("/imgs/descending.png")
                                                        ?.toExternalForm()
                                                )
                                                tooltip("Toggle descending")
                                            }
                                            openGroupsToggle = togglebutton(selectFirst = false) {
                                                graphic = ImageView(
                                                    MainView::class.java.getResource("/imgs/opengroups.png")
                                                        ?.toExternalForm()
                                                )
                                                tooltip("Toggle show group elements")
                                            }
                                            shuffleToggle = togglebutton(selectFirst = false) {
                                                graphic = ImageView(
                                                    MainView::class.java.getResource("/imgs/shuffle.png")
                                                        ?.toExternalForm()
                                                )
                                                tooltip("Toggle shuffle results")
                                            }
                                        }
                                    }
                                    right {
                                        hbox(5.0) {
                                            alignment = Pos.CENTER_LEFT
                                            selectedCountLabel = label("0/0")
                                            searchButton = button("Search")
                                        }
                                    }
                                }
                            }
                        }
                        center {
                            itemGrid = multiselectgridview {
                                addClass(Styles.mainItemGridView)
                                addClass(Styles.itemGrid)
                            }
                        }
                        bottom {
                            borderpane {
                                left {
                                    importsButton = button("Imports: 0")
                                }
                                right {
                                    similarButton = button("Similar: 0")
                                }
                            }
                        }
                    }
                }
            }

            focuses = itemGrid

            editTagsPane = borderpane {
                isPickOnBounds = false
                padding = insets(50)

                addEventFilter(KeyEvent.KEY_PRESSED) { event ->
                    if (event.code == KeyCode.ESCAPE) hide()
                }

                bottom {
                    hbox(10) {
                        addClass(Styles.dialogPane)
                        isPickOnBounds = false
                        padding = insets(10)
                        editTags = textfield {
                            hboxConstraints {
                                hGrow = Priority.ALWAYS
                            }
                            promptText = "Edit tags..."
                        }
                        applyTagEdit = button("Ok")
                    }
                }

                hide()
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
        initHistoryListener()
        initSearchListener()
        initSearchFieldAutoComplete()
        initEditTagsAutoComplete()
        initSelectedItemCounterListeners()
        initImportsButton()
        initSimilarButton()

        Platform.runLater { itemGrid.requestFocus() }
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

            onEnd = {
                myApp.openHelpDialog()
            }

            start()
        }
    }

    private fun initSimilarButton() {
        similar.addListener(ListChangeListener { change ->
            while (change.next()) {
                runOnUIThread {
                    val count = change.list.size
                    similarButton.text = "Similar: $count"
                    if (count > 0) {
                        if (!similarButton.hasClass(Styles.blueBase)) similarButton.addClass(Styles.blueBase)
                    } else {
                        similarButton.removeClass(Styles.blueBase)
                    }
                    similarButton.applyCss()
                }
            }
        })

        similarButton.onActionConsuming { openSimilarDialog() }
    }

    fun openSimilarDialog() {
        this.add(DuplicateResolverDialog(similar, myApp.context).apply {
            onClose = {
                if (similar.isNotEmpty()) {
                    val menagerie = similar.first().obj1.menagerie
                    similar.removeIf { menagerie.hasNonDupe(it) }
                }
            }
        })
    }

    private fun initImportsButton() {
        importsButton.onActionConsuming { root.add(ImportQueueDialog(imports)) }

        val finishedListener: (observable: Observable) -> Unit = { _ ->
            updateImportsButton()
        }
        imports.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { it.finishedProperty.removeListener(finishedListener) }
                change.addedSubList.forEach { it.finishedProperty.addListener(finishedListener) }

                updateImportsButton()
            }
        })
    }

    private fun updateImportsButton() {
        runOnUIThread {
            val count = imports.count { !it.isFinished }
            importsButton.text = "Imports: $count"
            if (count > 0) {
                if (!importsButton.hasClass(Styles.blueBase)) importsButton.addClass(Styles.blueBase)
            } else {
                importsButton.removeClass(Styles.blueBase)
            }
            importsButton.applyCss()
        }
    }

    private fun initSelectedItemCounterListeners() {
        val listener = InvalidationListener {
            selectedCountLabel.text = "${itemGrid.selected.size}/${itemGrid.items.size}"
        }
        itemGrid.items.addListener(listener)
        itemGrid.selected.addListener(listener)
    }

    private fun initEditTagsAutoComplete() {
        editTags.bindAutoComplete { predict ->
            val result = mutableListOf<Tag>()
            var word = predict.lowercase()
            val exclude = word.startsWith('-')
            if (exclude) word = word.substring(1)

            if (exclude) {
                itemGrid.selected.forEach { item ->
                    item.tags.forEach { tag ->
                        if (tag.name.startsWith(word) && tag !in result) result.add(tag)
                    }
                }
            } else {
                currentSearch?.menagerie?.tags?.forEach { tag ->
                    if (tag.name.startsWith(word)) result.add(tag)
                }
            }
            result.sortByDescending { it.frequency }

            result.subList(0, 8.coerceAtMost(result.size)).map { if (exclude) "-${it.name}" else it.name }
        }
    }

    private fun initSearchFieldAutoComplete() {
        searchTextField.bindAutoComplete { predict ->
            var word = predict.lowercase()
            val exclude = word.startsWith('-')
            if (exclude) word = word.substring(1)

            val tags = mutableListOf<Tag>()
            currentSearch?.menagerie?.tags?.forEach { tag ->
                if (tag.name.startsWith(word)) tags.add(tag)
            }
            tags.sortByDescending { it.frequency }

            val result = mutableListOf<String>()
            tags.forEach { result.add(if (exclude) "-${it.name}" else it.name) }

            FilterFactory.filterPrefixes.forEach { if (it.startsWith(word)) result.add(it) }

            return@bindAutoComplete result.subList(0, 8.coerceAtMost(result.size))
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
        val view = currentSearch ?: return
        val text = searchTextField.text.trim()
        val filters = try {
            FilterFactory.parseFilters(text, view.menagerie, !openGroupsToggle.isSelected)
        } catch (e: FilterParseException) {
            log.severe {
                val sw = StringWriter()
                sw.append("Likely because no tag with given name exists\n")
                val pw = PrintWriter(sw)
                e.printStackTrace(pw)
                pw.close()
                return@severe sw.toString()
            }
            information(
                "Error while parsing filters",
                e.message,
                ButtonType.OK,
                owner = currentWindow
            ) // TODO: better error message?
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

    private fun initHistoryListener() {
        history.onChange { change ->
            while (change.next()) {
                backButton.isDisable = change.list.isEmpty()
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
                    onActionConsuming { itemGrid.selected.firstOrNull()?.pasteTagsFromClipboard(myApp.context) }
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
                    dupesGroup.isVisible = itemGrid.selected.size > 0
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
        editTags.bindShortcut(KeyCode.ENTER) { applyTagEdit.fire() }
        editTags.bindShortcut(KeyCode.ENTER, ctrl = true) { applyTagEdit.fire() }
    }

    private fun initRootKeyPressedListener() {
        root.bindShortcut(KeyCode.ESCAPE) {
            itemGrid.requestFocus()
        }
    }

    fun displayTagsDialog() {
        var dialog: TagSearchDialog? = null
        dialog = TagSearchDialog(currentSearch?.menagerie ?: return) {
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
        editTags.requestFocus()
    }

    private fun initTagsListener() {
        // Listener is attached to currently displayed/previewed item
        @Suppress("RemoveExplicitTypeArguments")
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