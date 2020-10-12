package com.github.iguanastin.view

import com.github.iguanastin.app.*
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.app.menagerie.view.MenagerieView
import com.github.iguanastin.view.nodes.*
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import tornadofx.*


class MainView : View("Menagerie") {

    lateinit var itemDisplay: ItemDisplay
    lateinit var itemGrid: MultiSelectGridView<Item>
    lateinit var tagView: ListView<Tag>
    lateinit var dragOverlay: BorderPane

    private lateinit var editTagsPane: BorderPane
    private lateinit var editTags: TextField
    private lateinit var applyTagEdit: Button

    val viewProperty: ObjectProperty<MenagerieView?> = SimpleObjectProperty(null)
    var view: MenagerieView?
        get() = viewProperty.get()
        set(value) = viewProperty.set(value)

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
                            textfield()
                        }
                        center {
                            itemGrid = multiselectgridview {
                                addClass(Styles.itemGridView)
                                cellWidth = ItemCellFactory.SIZE
                                cellHeight = ItemCellFactory.SIZE
                                horizontalCellSpacing = 4.0
                                verticalCellSpacing = 4.0
                                cellFactory = ItemCellFactory.factory
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
        initViewPropertyListener()
        initDisplayLastSelectedListener()
        initEditTagsDialog()
        initTagsListener()
        initRootKeyPressedListener()

        Platform.runLater { itemGrid.requestFocus() }
    }

    private fun initViewPropertyListener() {
        viewProperty.addListener { _, oldValue, newValue ->
            oldValue?.close()
            itemGrid.items.clear()

            newValue?.attachTo(itemGrid.items)
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
        root.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (event.isShortcutDown && !event.isAltDown && !event.isShiftDown) {
                if (event.code == KeyCode.E) {
                    editTagsPane.show()
                    editTags.requestFocus()
                }
            }
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