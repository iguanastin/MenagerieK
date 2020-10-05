package com.github.iguanastin.view

import com.github.iguanastin.app.*
import com.github.iguanastin.app.menagerie.*
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import javafx.application.Platform
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.ButtonType
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.image.Image
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.scene.paint.Color
import javafx.stage.Screen
import org.controlsfx.control.GridView
import tornadofx.*
import java.io.File
import java.sql.SQLException
import java.util.prefs.Preferences
import kotlin.concurrent.thread

class MainView : View("Menagerie") {

    private val prefs: Preferences = Preferences.userRoot().node("com/github/iguanastin/MenagerieK/MainView")

    private val dbURL = "~/test-sfw"
    private val dbUser = "sa"
    private val dbPass = ""

    private lateinit var preview: PanZoomImageView
    private lateinit var itemGrid: GridView<Item>

    private val tags: ObservableList<Tag> = observableListOf()

    private lateinit var manager: MenagerieDatabase
    private lateinit var menagerie: Menagerie


    override val root = stackpane {
        borderpane {
            center {
                preview = panZoomImageView()
            }
            left {
                listview(tags) {
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

    private fun attemptLoadMenagerie() {
        try {
            manager = MenagerieDatabase(dbURL, dbUser, dbPass)

            val load: () -> Unit = {
                try {
                    menagerie = manager.loadMenagerie()
                    itemGrid.items.apply {
                        runOnUIThread {
                            clear()
                            addAll(menagerie.items)
//                            if (isNotEmpty()) itemGrid.selectionModel.select(0)
                        }
                    }
                } catch (e: MenagerieDatabaseException) {
                    e.printStackTrace()
                    runOnUIThread { error(title = "Error", header = "Failed to read database", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = currentWindow) }
                }
            }
            val migrate: () -> Unit = {
                thread(start = true) {
                    try {
                        manager.migrateDatabase()

                        load()
                    } catch (e: MenagerieDatabaseException) {
                        e.printStackTrace()
                        runOnUIThread { error(title = "Error", header = "Failed to migrate database", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = currentWindow) }
                    }
                }
            }

            if (manager.needsMigration()) {
                if (manager.canMigrate()) {
                    if (manager.version == -1) {
                        confirm(title = "Database initialization", header = "Database needs to be initialized", owner = currentWindow, actionFn = {
                            migrate()
                        })
                    } else {
                        confirm(title = "Database migration", header = "Database needs to update (v${manager.version} -> v${MenagerieDatabase.REQUIRED_DATABASE_VERSION})", owner = currentWindow, actionFn = {
                            migrate()
                        })
                    }
                } else {
                    error(title = "Incompatible database", header = "Database v${manager.version} is not supported!", content = "Update to database version 8 with the latest Java Menagerie application\n-OR-\nCreate a new database", owner = currentWindow)
                }
            } else {
                thread(start = true) { load() }
            }
        } catch (e: Exception) {
            error(title = "Error", header = "Failed to connect to database", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = currentWindow)
        }
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

        preview.onMouseClicked = EventHandler {
            if (it.button != MouseButton.SECONDARY) return@EventHandler

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

        Platform.runLater { attemptLoadMenagerie() }
    }

    override fun onUndock() {
        println("Defragging...")
        manager.closeAndCompress()
    }

}