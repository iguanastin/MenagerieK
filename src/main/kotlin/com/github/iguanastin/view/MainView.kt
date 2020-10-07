package com.github.iguanastin.view

import com.github.iguanastin.app.*
import com.github.iguanastin.app.menagerie.*
import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.property.ObjectProperty
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.scene.control.ListView
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.stage.Screen
import javafx.stage.Stage
import org.controlsfx.control.GridView
import tornadofx.*
import java.util.prefs.Preferences

class MainView : View("Menagerie") {

    private lateinit var itemDisplay: ItemDisplay
    private lateinit var itemGrid: GridView<Item>
    private lateinit var tagView: ListView<Tag>

    val itemsProperty: ObjectProperty<ObservableList<Item>>
        get() = itemGrid.itemsProperty()
    var items: ObservableList<Item>
        get() = itemGrid.items
        set(value) {
            itemGrid.items = value
        }

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


    override val root = stackpane {
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
                            cellFactory = TagCellFactory.factory
                            maxWidth = 200.0
                            minWidth = 200.0
                        }
                    }
                    bottom {
                        textfield {
                            promptText = "Edit tags..."
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
                            addClass(Styles.gridView)
                            cellWidth = Item.thumbnailWidth + ItemCellFactory.PADDING * 2
                            cellHeight = Item.thumbnailHeight + ItemCellFactory.PADDING * 2
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
    }

    init {
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