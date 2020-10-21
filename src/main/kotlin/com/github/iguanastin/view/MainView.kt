package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.expandGroups
import com.github.iguanastin.app.menagerie.model.*
import com.github.iguanastin.app.menagerie.view.*
import com.github.iguanastin.view.dialog.DuplicateResolverDialog
import com.github.iguanastin.view.dialog.ImportNotification
import com.github.iguanastin.view.dialog.ImportQueueDialog
import com.github.iguanastin.view.nodes.*
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.stage.Popup
import javafx.stage.PopupWindow
import mu.KotlinLogging
import tornadofx.*
import java.awt.Desktop
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

class MainView : View("Menagerie") {

    lateinit var itemDisplay: ItemDisplay
    lateinit var itemGrid: MultiSelectGridView<Item>
    lateinit var tagView: ListView<Tag>
    lateinit var dragOverlay: BorderPane

    private lateinit var editTagsPane: BorderPane
    private lateinit var editTags: TextField
    private lateinit var applyTagEdit: Button
    private lateinit var searchTextField: TextField
    private lateinit var backButton: Button
    private lateinit var descendingToggle: ToggleButton
    private lateinit var openGroupsToggle: ToggleButton
    private lateinit var shuffleToggle: ToggleButton
    private lateinit var selectedCountLabel: Label
    private lateinit var searchButton: Button
    private lateinit var importsButton: Button
    private lateinit var similarButton: Button

    private val _viewProperty: ObjectProperty<MenagerieView?> = SimpleObjectProperty(null)
    val viewProperty: ReadOnlyObjectProperty<MenagerieView?> = _viewProperty

    val displayingProperty: ObjectProperty<Item?>
        get() = itemDisplay.itemProperty
    var displaying: Item?
        get() = itemDisplay.item
        set(value) {
            itemDisplay.item = value
        }

    val tagsProperty: ObjectProperty<ObservableList<Tag>>
        get() = tagView.itemsProperty()
    var tags: ObservableList<Tag>
        get() = tagView.items
        set(value) {
            tagView.items = value
        }

    val imports: ObservableList<ImportNotification> = observableListOf()

    val similar: ObservableList<SimilarPair<Item>> = observableListOf()

    val history: ObservableList<ViewHistory> = observableListOf()

    override val root = topenabledstackpane {
        focusingstackpane {
            borderpane {
                center {
                    itemDisplay = itemdisplay {
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
                                cellFactory = TagCellFactory.factory
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
                                        onAction = EventHandler { event ->
                                            event.consume()
                                            navigateBack()
                                        }
                                    }
                                    searchTextField = textfield {
                                        hgrow = Priority.ALWAYS
                                        promptText = "Search"
                                    }
                                }
                                borderpane {
                                    left {
                                        hbox(5.0) {
                                            alignment = Pos.CENTER_LEFT
                                            descendingToggle = togglebutton(selectFirst = true) {
                                                graphic = ImageView(MainView::class.java.getResource("/imgs/descending.png").toExternalForm())
                                            }
                                            openGroupsToggle = togglebutton(selectFirst = false) {
                                                graphic = ImageView(MainView::class.java.getResource("/imgs/opengroups.png").toExternalForm())
                                            }
                                            shuffleToggle = togglebutton(selectFirst = false) {
                                                graphic = ImageView(MainView::class.java.getResource("/imgs/shuffle.png").toExternalForm())
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
                                addClass(Styles.itemGridView)
                                cellWidth = ItemCellFactory.SIZE
                                cellHeight = ItemCellFactory.SIZE
                                horizontalCellSpacing = 4.0
                                verticalCellSpacing = 4.0
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
                hide()
                padding = insets(50)
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

        similarButton.onAction = EventHandler { event ->
            event.consume()
            this.add(DuplicateResolverDialog(similar).apply {
                onClose = {
                    if (similar.isNotEmpty()) {
                        val menagerie = similar.first().obj1.menagerie
                        similar.removeIf { menagerie.hasNonDupe(it) }
                    }
                }
            })
        }
    }

    private fun initImportsButton() {
        importsButton.onAction = EventHandler { event ->
            event.consume()
            root.add(ImportQueueDialog(imports))
        }

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
            var word = predict.toLowerCase()
            val exclude = word.startsWith('-')
            if (exclude) word = word.substring(1)

            if (exclude) {
                itemGrid.selected.forEach { item ->
                    item.tags.forEach { tag ->
                        if (tag.name.startsWith(word) && tag !in result) result.add(tag)
                    }
                }
            } else {
                viewProperty.get()?.menagerie?.tags?.forEach { tag ->
                    if (tag.name.startsWith(word)) result.add(tag)
                }
            }
            result.sortByDescending { it.frequency }

            result.subList(0, 8.coerceAtMost(result.size)).map { if (exclude) "-${it.name}" else it.name }
        }
    }

    private fun initSearchFieldAutoComplete() {
        searchTextField.bindAutoComplete { predict ->
            val result = mutableListOf<Tag>()
            var word = predict.toLowerCase()
            val exclude = word.startsWith('-')
            if (exclude) word = word.substring(1)

            viewProperty.get()?.menagerie?.tags?.forEach { tag ->
                if (tag.name.startsWith(word)) result.add(tag)
            }
            result.sortByDescending { it.frequency }

            result.subList(0, 8.coerceAtMost(result.size)).map { if (exclude) "-${it.name}" else it.name }
        }
    }

    private fun displaySearchError(error: String) {
        val popup = Popup().apply {
            anchorLocation = PopupWindow.AnchorLocation.CONTENT_TOP_LEFT
            content.add(label(error) {
                addClass(Styles.dialogPane)
                style {
                    fontSize = 18.px
                    textFill = c("red")
                }
                padding = insets(10.0)
            })
        }
        val bounds = searchTextField.localToScreen(searchTextField.layoutBounds)
        popup.show(searchTextField, bounds.minX, bounds.maxY + 20.0)
        thread(start = true) {
            Thread.sleep(5000)
            runOnUIThread { popup.hide() }
        }
    }

    private fun initSearchListener() {
        searchButton.onAction = EventHandler { event ->
            val view = viewProperty.get() ?: return@EventHandler
            event.consume()
            val text = searchTextField.text.trim()
            val filters = mutableListOf<ViewFilter>()

            for (str in text.split(Regex("\\s+"))) {
                if (str.isBlank()) continue
                val exclude = str.startsWith('-')
                val word = if (exclude) str.substring(1) else str

                if (word.matches(Regex("(in:any)|(in:[0-9]+)", RegexOption.IGNORE_CASE))) {
                    // TODO refactor this to be more modular. Each filter type parses its own string?
                    val parameter = word.substring(3)
                    if (parameter.equals("any", true)) {
                        filters.add(ElementOfFilter(null, exclude))
                    } else {
                        try {
                            val item = view.menagerie.getItem(parameter.toInt())
                            if (item is GroupItem) {
                                filters.add(ElementOfFilter(item, exclude))
                            } else {
                                displaySearchError("No group in \"$word\" with ID: ${parameter.toInt()}")
                                return@EventHandler
                            }
                        } catch (e: NumberFormatException) {
                            displaySearchError("Parameter in \"$word\" is not a number: $parameter")
                            return@EventHandler
                        }
                    }
                } else if (word.toLowerCase() in arrayOf("is:group", "is:image", "is:video", "is:file")) {
                    filters.add(IsTypeFilter(when (word.toLowerCase()) {
                        "is:group" -> IsTypeFilter.Type.Group
                        "is:image" -> IsTypeFilter.Type.Image
                        "is:video" -> IsTypeFilter.Type.Video
                        "is:file" -> IsTypeFilter.Type.File
                        else -> throw IllegalArgumentException("No such type: \"$word\"")
                    }, exclude))
                } else {
                    val tag = view.menagerie.getTag(word)
                    if (tag == null) {
                        displaySearchError("No such tag: \"$word\"")
                        return@EventHandler
                    }
                    filters.add(TagFilter(tag, exclude))
                }
            }

            if (!openGroupsToggle.isSelected) filters.add(ElementOfFilter(null, true))

            navigateForward(MenagerieView(view.menagerie, searchTextField.text, descendingToggle.isSelected, shuffleToggle.isSelected, filters))
            itemGrid.requestFocus()
        }
        searchTextField.onAction = searchButton.onAction

        searchTextField.addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (event.isShortcutDown && !event.isShiftDown && !event.isAltDown) {
                if (event.code == KeyCode.G) {
                    event.consume()
                    openGroupsToggle.isSelected = !openGroupsToggle.isSelected
                } else if (event.code == KeyCode.D) {
                    event.consume()
                    descendingToggle.isSelected = !descendingToggle.isSelected
                } else if (event.code == KeyCode.S) {
                    event.consume()
                    shuffleToggle.isSelected = !shuffleToggle.isSelected
                }
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
        _viewProperty.set(back.view)

        itemGrid.selected.apply {
            clear()
            addAll(back.selected)
        }
    }

    fun navigateForward(view: MenagerieView) {
        val old = _viewProperty.get()
        if (old != null) history.add(ViewHistory(old, itemGrid.selected.toList()))
        _viewProperty.set(view)
    }

    private fun onItemAction(item: Item) {
        if (item is GroupItem) {
            val filter = ElementOfFilter(item, false)
            navigateForward(MenagerieView(item.menagerie, filter.toString(), false, false, listOf(filter), {
                if (it is FileItem) {
                    it.elementOf?.items?.indexOf(it)
                } else {
                    it.id
                }
            }))
        } else if (item is FileItem) {
            Desktop.getDesktop().open(item.file)
        }
    }

    private fun initItemGrid() {
        itemGrid.cellFactory = ItemCellFactory.factory {
            addEventHandler(MouseEvent.MOUSE_CLICKED) { event ->
                if (event.button == MouseButton.PRIMARY && event.clickCount == 2) {
                    event.consume()
                    onItemAction(item)
                }
            }

            addEventHandler(MouseEvent.DRAG_DETECTED) { event ->
                event.consume()
                val db = startDragAndDrop(*TransferMode.ANY)
                item.getThumbnail().want(db) { db.dragView = it.image }
                db.putFiles(expandGroups(itemGrid.selected).filter { it is FileItem }.map { (it as FileItem).file }.toMutableList())
            }
        }
        itemGrid.addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.ENTER && !event.isShortcutDown && !event.isShiftDown) {
                if (itemGrid.selected.size == 1) {
                    event.consume()
                    onItemAction(itemGrid.selected.first())
                }
            }
            if (event.code == KeyCode.BACK_SPACE && !event.isShortcutDown && !event.isShiftDown) {
                event.consume()
                navigateBack()
            }
        }
    }

    private fun initViewListener() {
        _viewProperty.addListener { _, oldValue, newValue ->
            oldValue?.close()
            itemGrid.items.clear()

            if (newValue != null) {
                newValue.attachTo(itemGrid.items)
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
            displaying = itemGrid.selected.lastOrNull()
        })
    }

    private fun initEditTagsDialog() {
        editTagsPane.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.ESCAPE) editTagsPane.hide()
        }
        editTagsPane.visibleProperty().addListener(ChangeListener { _, _, newValue ->
            if (!newValue) itemGrid.requestFocus()
        })
        editTags.addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (!event.isAltDown && !event.isShiftDown && event.code == KeyCode.ENTER) {
                applyTagEdit.fire()
            }
        }
        applyTagEdit.onAction = EventHandler { event ->
            for (name in editTags.text.trim().split(Regex("\\s+"))) {
                val menagerie = itemGrid.selected[0].menagerie
                if (name.startsWith('-')) {
                    val tag: Tag = menagerie.getTag(name.substring(1)) ?: continue
                    ArrayList(itemGrid.selected).forEach { it.removeTag(tag) }
                } else {
                    var tag: Tag? = menagerie.getTag(name)
                    if (tag == null) {
                        tag = Tag(menagerie.reserveTagID(), name)
                        menagerie.addTag(tag)
                    }
                    ArrayList(itemGrid.selected).forEach { it.addTag(tag) }
                }
            }
            editTagsPane.hide()
            event.consume()
        }
    }

    private fun initRootKeyPressedListener() {
        root.addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (event.isShortcutDown && !event.isAltDown && !event.isShiftDown) {
                if (event.code == KeyCode.E) {
                    event.consume()
                    editTagsPane.show()
                    editTags.requestFocus()
                } else if (event.code == KeyCode.F) {
                    event.consume()
                    searchTextField.selectAll()
                    searchTextField.requestFocus()
                } else if (event.code == KeyCode.N) {
                    event.consume()
                    importsButton.fire()
                }
            } else if (!event.isShortcutDown && !event.isAltDown && !event.isShiftDown) {
                if (event.code == KeyCode.ESCAPE) {
                    event.consume()
                    itemGrid.requestFocus()
                }
            } else if (event.isShortcutDown && event.isShiftDown && !event.isAltDown) {
                if (event.code == KeyCode.D) {
                    event.consume()
                    similarButton.fire()
                }
            }
        }
        root.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (event.isShortcutDown && !event.isAltDown && !event.isShiftDown) {
                if (event.code == KeyCode.Q) {
                    Platform.exit()
                }
            }
        }
    }

    private fun initTagsListener() {
        val displayTagsListener = InvalidationListener {
            tags.apply {
                clear()
                val item = displaying
                if (item != null) addAll(item.tags.sortedBy { it.name })
            }
        }
        displayingProperty.addListener(ChangeListener { _, old, new ->
            old?.tags?.removeListener(displayTagsListener)
            new?.tags?.addListener(displayTagsListener)
            tags.apply {
                clear()
                if (new != null) addAll(new.tags.sortedBy { it.name })
            }
        })
    }

}