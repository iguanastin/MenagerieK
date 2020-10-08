package com.github.iguanastin.app

import com.github.iguanastin.app.menagerie.FileItem
import com.github.iguanastin.app.menagerie.Item
import com.github.iguanastin.app.menagerie.Menagerie
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import com.github.iguanastin.app.menagerie.import.ImportJob
import com.github.iguanastin.app.menagerie.import.MenagerieImporter
import com.github.iguanastin.app.menagerie.import.RemoteImportJob
import com.github.iguanastin.view.MainView
import com.github.iguanastin.view.runOnUIThread
import javafx.application.Platform
import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.scene.control.ButtonType
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.TransferMode
import javafx.stage.Screen
import javafx.stage.Stage
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.io.IOException
import java.util.prefs.Preferences
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

class MyApp : App(MainView::class, Styles::class) {

    private val uiPrefs: Preferences = Preferences.userRoot().node("com/github/iguanastin/MenagerieK/ui")
    private val contextPrefs: Preferences = Preferences.userRoot().node("com/github/iguanastin/MenagerieK/context")

    private val dbURL = contextPrefs.get("db_url", "~/test-sfw-v9")
    private val dbUser = contextPrefs.get("db_user", "sa")
    private val dbPass = contextPrefs.get("db_pass", "")

    private lateinit var manager: MenagerieDatabase
    private lateinit var menagerie: Menagerie
    private lateinit var importer: MenagerieImporter

    private lateinit var root: MainView


    override fun start(stage: Stage) {
        super.start(stage)
        root = find(primaryView) as MainView

        log.info("Starting app")

        initMainStageProperties(stage)

        loadMenagerie(stage) { manager, menagerie, importer ->
            manager.updateErrorHandlers.add { e ->
                // TODO show error to user
                log.error("Error occurred while updating database", e)
            }
            importer.onError.add { e ->
                // TODO show error to user
                log.error("Error occurred while importing", e)
            }

            initViewControls()
        }
    }

    private fun initViewControls() {
        Platform.runLater {
            root.items.addAll(menagerie.items.reversed())
            if (root.items.isNotEmpty()) root.itemGrid.select(root.items.first())
        }
        menagerie.items.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { runOnUIThread { root.items.remove(it) } }
                change.addedSubList.forEach { item ->
                    runOnUIThread {
                        for (i in root.items.indices) {
                            if (item.id > root.items[i].id) {
                                root.items.add(i, item)
                                break
                            }
                        }
                    }
                }
            }
        })

        root.root.onDragOver = EventHandler { event ->
            if (event.gestureSource == null && (event.dragboard.hasFiles() || event.dragboard.hasUrl())) {
                root.dragOverlay.show()
                event.apply {
                    acceptTransferModes(*TransferMode.ANY)
                    consume()
                }
            }
        }
        root.root.onDragDropped = EventHandler { event ->
            if (event.isAccepted) {
                // TODO improve this
                if (event.dragboard.url != null) {
                    importer.enqueue(RemoteImportJob.intoDirectory(event.dragboard.url, File("D:\\Downloads")))
                }
                if (event.dragboard.files?.isNotEmpty() == true) {
                    event.dragboard.files.forEach { file -> importer.enqueue(ImportJob(file)) }
                }

                event.apply {
                    isDropCompleted = true
                    consume()
                }
            }
        }
        root.root.onDragExited = EventHandler {
            root.dragOverlay.hide()
        }

        root.itemGrid.addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.DELETE) {
                val del: List<Item> = mutableListOf<Item>().apply { addAll(root.itemGrid.selected) }
                if (del.isNotEmpty()) {
                    del.forEach {
                        log.info("Removing item: $it")
                        menagerie.removeItem(it)
                    }

                    if (!event.isShortcutDown) {
                        del.forEach {
                            if (it is FileItem) {
                                try {
                                    log.info("Deleting file: ${it.file}")
                                    it.file.delete()
                                } catch (e: IOException) {
                                    log.error("Exception while deleting file: ${it.file}", e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun stop() {
        super.stop()

        log.info("Stopping app")

        try {
            importer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        manager.closeAndCompress()
    }

    private fun loadMenagerie(stage: Stage, after: ((manager: MenagerieDatabase, menagerie: Menagerie, importer: MenagerieImporter) -> Unit)? = null) {
        try {
            manager = MenagerieDatabase(dbURL, dbUser, dbPass)

            val load: () -> Unit = {
                try {
                    menagerie = manager.loadMenagerie()
                    importer = MenagerieImporter(menagerie)

                    after?.invoke(manager, menagerie, importer)
                } catch (e: MenagerieDatabaseException) {
                    e.printStackTrace()
                    runOnUIThread { error(title = "Error", header = "Failed to read database: $dbURL", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = stage) }
                }
            }
            val migrate: () -> Unit = {
                thread(start = true) {
                    try {
                        manager.migrateDatabase()

                        load()
                    } catch (e: MenagerieDatabaseException) {
                        e.printStackTrace()
                        runOnUIThread { error(title = "Error", header = "Failed to migrate database: $dbURL", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = stage) }
                    }
                }
            }

            if (manager.needsMigration()) {
                if (manager.canMigrate()) {
                    if (manager.version == -1) {
                        confirm(title = "Database initialization", header = "Database needs to be initialized: $dbURL", owner = stage, actionFn = {
                            migrate()
                        })
                    } else {
                        confirm(title = "Database migration", header = "Database ($dbURL) needs to update (v${manager.version} -> v${MenagerieDatabase.REQUIRED_DATABASE_VERSION})", owner = stage, actionFn = {
                            migrate()
                        })
                    }
                } else {
                    error(title = "Incompatible database", header = "Database v${manager.version} is not supported!", content = "Update to database version 8 with the latest Java Menagerie application\n-OR-\nCreate a new database", owner = stage)
                }
            } else {
                thread(start = true) { load() }
            }
        } catch (e: Exception) {
            error(title = "Error", header = "Failed to connect to database: $dbURL", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = stage)
        }
    }

    private fun initMainStageProperties(stage: Stage) {
        // Maximized
        stage.isMaximized = uiPrefs.getBoolean("maximized", false)
        stage.maximizedProperty()?.addListener { _, _, newValue ->
            uiPrefs.putBoolean("maximized", newValue)
        }

        // Width
        stage.width = uiPrefs.getDouble("width", 600.0)
        stage.widthProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble("width", newValue.toDouble())
        }

        // Height
        stage.height = uiPrefs.getDouble("height", 400.0)
        stage.heightProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble("height", newValue.toDouble())
        }

        // Screen position
        val screen = Screen.getPrimary().visualBounds
        // X
        stage.x = uiPrefs.getDouble("x", (screen.maxX + screen.minX) / 2)
        stage.xProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble("x", newValue.toDouble())
        }
        // Y
        stage.y = uiPrefs.getDouble("y", (screen.maxY + screen.minY) / 2)
        stage.yProperty()?.addListener { _, _, newValue ->
            uiPrefs.putDouble("y", newValue.toDouble())
        }
    }

}