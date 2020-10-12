package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.app.menagerie.view.ElementOfFilter
import com.github.iguanastin.app.menagerie.view.MenagerieView
import com.github.iguanastin.app.menagerie.view.TagFilter
import com.github.iguanastin.app.menagerie.view.ViewFilter
import com.github.iguanastin.view.nodes.*
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.property.ObjectProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import mu.KotlinLogging
import tornadofx.*
import java.lang.NumberFormatException

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
                                    button("Something")
                                }
                                right {
                                    button("Else")
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

        Platform.runLater { itemGrid.requestFocus() }
    }

    private fun initSearchListener() {
        searchTextField.onAction = EventHandler { event ->
            val view = viewProperty.get() ?: return@EventHandler
            event.consume()
            val text = searchTextField.text.trim()
            val filters = mutableListOf<ViewFilter>()

            for (str in text.split(Regex("\\s+"))) {
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
                                // TODO throw error and stop search. Display error to user?
                            }
                        } catch (e: NumberFormatException) {
                            // TODO throw error and stop search. Display error to user?
                        }
                    }
                } else {
                    val tag = view.menagerie.getTag(word) ?: continue
                    filters.add(TagFilter(tag, exclude))
                }
            }

            navigateForward(MenagerieView(view.menagerie, searchTextField.text, true, filters)) // TODO descending toggle?
            itemGrid.requestFocus()
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
        searchTextField.text = back.view.searchString
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
            navigateForward(MenagerieView(item.menagerie, filter.toString(), false, listOf(filter)))
        } else {
            // TODO some action for activating other items?
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
        viewProperty.addListener { _, oldValue, newValue ->
            oldValue?.close()
            itemGrid.items.clear()

            newValue?.attachTo(itemGrid.items)
            searchTextField.text = newValue?.searchString ?: ""
            if (itemGrid.items.isNotEmpty()) itemGrid.select(itemGrid.items.first())
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
        editTags.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.ENTER) applyTagEdit.fire()
        }
        applyTagEdit.onAction = EventHandler { event ->
            for (name in editTags.text.trim().split(Regex("\\s+"))) {
                val menagerie = itemGrid.selected[0].menagerie
                if (name.startsWith('-')) {
                    val tag: Tag = menagerie.getTag(name.substring(1)) ?: continue
                    itemGrid.selected.forEach { it.removeTag(tag) }
                } else {
                    var tag: Tag? = menagerie.getTag(name)
                    if (tag == null) {
                        tag = Tag(menagerie.reserveTagID(), name)
                        menagerie.addTag(tag)
                    }
                    itemGrid.selected.forEach { it.addTag(tag) }
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
                }
            } else if (!event.isShortcutDown && !event.isAltDown && !event.isShiftDown) {
                if (event.code == KeyCode.ESCAPE) {
                    event.consume()
                    itemGrid.requestFocus()
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