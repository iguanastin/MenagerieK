package com.github.iguanastin.view

import com.github.iguanastin.app.*
import com.github.iguanastin.app.menagerie.*
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.scene.control.ListView
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.stage.Screen
import org.controlsfx.control.GridView
import tornadofx.*
import java.util.prefs.Preferences

class MainView : View("Menagerie") {

    @Volatile
    private var _queuedDisplay: Item? = null

    @Volatile
    private var _queuedItems: List<Item>? = null


    private val prefs: Preferences = Preferences.userRoot().node("com/github/iguanastin/MenagerieK/MainView")

    private lateinit var imageDisplay: PanZoomImageView
    private lateinit var groupDisplay: GroupPreview
    private lateinit var itemGrid: GridView<Item>
    private lateinit var tagView: ListView<Tag>


    override val root = stackpane {
        borderpane {
            center {
                stackpane {
                    imageDisplay = panzoomimageview()
                    groupDisplay = grouppreview()
                }
            }
            left {
                tagView = listview {
                    cellFactory = TagCellFactory.factory
                    maxWidth = 200.0
                    minWidth = 200.0
                }
            }
            right {
                borderpane {
                    padding = insets(4)
                    top {
                        textfield()
                    }
                    center {
                        itemGrid = gridView {
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
        initViewControls()
    }

    private fun initViewControls() {
        // TODO remove this
        imageDisplay.onMouseClicked = EventHandler {
            if (it.button != MouseButton.SECONDARY) return@EventHandler

            // TODO extract this into a utility function?
            var bp: BorderPane? = null
            bp = root.borderpane {
                addClass(Styles.dialogPane)
                center {
                    fourChooser {
                        onLeft = "Left" to { println("left") }
                        onRight = "Right" to { println("right") }
                        onTop = "Top" to { println("top") }
                        onBottom = "Bottom" to { println("bottom") }
                        onCancel = { println("cancel") }
                        onClose = {
                            bp?.removeFromParent()
                            println("close")
                        }
                    }
                }
            }
        }
    }

    /**
     * Sets the next item to display
     *
     * If called from off the main JavaFX thread, an update will be queued. Once the JavaFX thread processes the event, only the latest change will be applied
     */
    fun display(item: Item) {
        this._queuedDisplay = item

        runOnUIThread {
            val newItem = this@MainView._queuedDisplay ?: return@runOnUIThread
            this@MainView._queuedDisplay = null

            groupDisplay.group = null
            groupDisplay.hide()
            imageDisplay.image = null
            imageDisplay.hide()

            when (newItem) {
                is ImageItem -> {
                    imageDisplay.image = image(newItem.file, true)
                    imageDisplay.show()
                }
                is GroupItem -> {
                    groupDisplay.group = item as GroupItem
                    groupDisplay.show()
                }
                else -> {
                    println("${newItem.id}: $newItem")
                    // TODO previews for other item types
                }
            }
            tagView.items.bind(newItem.tags) { it }
            Platform.runLater { imageDisplay.fitImageToView() } // TODO this nightmare still doesn't work
        }
    }

    /**
     * Sets the next list of items to display
     *
     * If called from off the main JavaFX thread, an update will be queued. Once the JavaFX thread processes the event, only the latest change will be applied
     */
    fun setItems(items: List<Item>) {
        this._queuedItems = items

        runOnUIThread {
            val newItems = this@MainView._queuedItems ?: return@runOnUIThread
            this@MainView._queuedItems = null

            itemGrid.items.apply {
                clear()
                addAll(newItems)
                if (newItems.isNotEmpty()) display(newItems[0])
                // TODO select first item instead of explicitly displaying
            }
        }
    }

    private fun initStageProperties() {
        val stage = currentStage

        if (stage != null) {
            // Maximized
            stage.isMaximized = prefs.getBoolean("maximized", false)
            stage.maximizedProperty()?.addListener { _, _, newValue ->
                prefs.putBoolean("maximized", newValue)
            }

            // Width
            stage.width = prefs.getDouble("width", 600.0)
            stage.widthProperty()?.addListener { _, _, newValue ->
                prefs.putDouble("width", newValue.toDouble())
            }

            // Height
            stage.height = prefs.getDouble("height", 400.0)
            stage.heightProperty()?.addListener { _, _, newValue ->
                prefs.putDouble("height", newValue.toDouble())
            }

            // Screen position
            val screen = Screen.getPrimary().visualBounds
            // X
            stage.x = prefs.getDouble("x", (screen.maxX + screen.minX) / 2)
            stage.xProperty()?.addListener { _, _, newValue ->
                prefs.putDouble("x", newValue.toDouble())
            }
            // Y
            stage.y = prefs.getDouble("y", (screen.maxY + screen.minY) / 2)
            stage.yProperty()?.addListener { _, _, newValue ->
                prefs.putDouble("y", newValue.toDouble())
            }
        }
    }

    override fun onDock() {
        initStageProperties()

        root.onKeyPressed = EventHandler {
            if (it.isControlDown && it.code == KeyCode.Q) Platform.exit()
        }
    }

}