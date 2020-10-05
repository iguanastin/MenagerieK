package com.github.iguanastin.app

import com.github.iguanastin.app.menagerie.ImageItem
import com.github.iguanastin.app.menagerie.Menagerie
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import com.github.iguanastin.app.menagerie.import.MenagerieImporter
import com.github.iguanastin.view.MainView
import com.github.iguanastin.view.runOnUIThread
import javafx.scene.control.ButtonType
import javafx.stage.Stage
import tornadofx.*
import java.util.prefs.Preferences
import kotlin.concurrent.thread

class MyApp : App(MainView::class, Styles::class) {

    private val prefs: Preferences = Preferences.userRoot().node("com/github/iguanastin/MenagerieK/MyApp")

    private val dbURL = prefs.get("db_url", "~/test-sfw")
    private val dbUser = prefs.get("db_user", "sa")
    private val dbPass = prefs.get("db_pass", "")

    private lateinit var manager: MenagerieDatabase
    private lateinit var menagerie: Menagerie
    private lateinit var importer: MenagerieImporter

    private lateinit var root: MainView


    override fun start(stage: Stage) {
        super.start(stage)
        root = find(primaryView) as MainView

        attemptLoadMenagerie(stage)
    }

    override fun stop() {
        super.stop()

        importer.close()

        println("Defragging...")
        manager.closeAndCompress()
    }

    private fun attemptLoadMenagerie(stage: Stage, after: ((menagerie: Menagerie) -> Unit)? = null) {
        try {
            manager = MenagerieDatabase(dbURL, dbUser, dbPass)

            val load: () -> Unit = {
                try {
                    menagerie = manager.loadMenagerie()
                    importer = MenagerieImporter(menagerie)
                    runOnUIThread {
                        root.setItems(menagerie.items.reversed())
                    }

                    after?.invoke(menagerie)
                } catch (e: MenagerieDatabaseException) {
                    e.printStackTrace()
                    runOnUIThread { error(title = "Error", header = "Failed to read database", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = stage) }
                }
            }
            val migrate: () -> Unit = {
                thread(start = true) {
                    try {
                        manager.migrateDatabase()

                        load()
                    } catch (e: MenagerieDatabaseException) {
                        e.printStackTrace()
                        runOnUIThread { error(title = "Error", header = "Failed to migrate database", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = stage) }
                    }
                }
            }

            if (manager.needsMigration()) {
                if (manager.canMigrate()) {
                    if (manager.version == -1) {
                        confirm(title = "Database initialization", header = "Database needs to be initialized", owner = stage, actionFn = {
                            migrate()
                        })
                    } else {
                        confirm(title = "Database migration", header = "Database needs to update (v${manager.version} -> v${MenagerieDatabase.REQUIRED_DATABASE_VERSION})", owner = stage, actionFn = {
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
            error(title = "Error", header = "Failed to connect to database", content = e.localizedMessage, buttons = arrayOf(ButtonType.OK), owner = stage)
        }
    }

}