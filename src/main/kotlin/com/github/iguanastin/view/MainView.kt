package com.github.iguanastin.view

import com.github.iguanastin.app.*
import com.github.iguanastin.app.menagerie.*
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import com.github.iguanastin.app.menagerie.import.RemoteImportJob
import javafx.application.Platform
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.scene.control.ButtonType
import javafx.scene.control.ListView
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.stage.Screen
import org.controlsfx.control.GridView
import tornadofx.*
import java.io.File
import java.util.prefs.Preferences
import kotlin.concurrent.thread

class MainView : View("Menagerie") {

    private val prefs: Preferences = Preferences.userRoot().node("com/github/iguanastin/MenagerieK/MainView")

    private lateinit var preview: PanZoomImageView
    private lateinit var itemGrid: GridView<Item>
    private lateinit var tagView: ListView<Tag>


    override val root = stackpane {
        borderpane {
            center {
                preview = panZoomImageView()
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
//        itemGrid.selectionModel.selectedItemProperty().addListener { _, _, item ->
//            if (item != null) {
//                tags.apply {
//                    clear()
//                    addAll(item.tags)
//                    sortBy { tag: Tag -> tag.name }
//                }
//
//                if (item is FileItem) {
//                    preview.apply {
//                        image = Image(item.file.toURI().toString())
//                        fitImageToView()
//                    }
//                } else {
//                    preview.image = null
//                }
//            }
//        }

        // TODO remove this
        preview.onMouseClicked = EventHandler {
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

    fun preview(item: Item) {
        when (item) {
            is ImageItem -> {
                preview.image = image(item.file, true)
                preview.fitImageToView()
            }
            else -> {
                println("${item.id}: $item")
                // TODO previews for other item types
            }
        }
        tagView.items.bind(item.tags) { it }
    }

    fun setItems(items: List<Item>) {
        itemGrid.items.apply {
            clear()
            addAll(items)
            if (items.isNotEmpty()) preview(items[0])
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