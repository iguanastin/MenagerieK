package com.github.iguanastin.app

import com.github.iguanastin.app.menagerie.Menagerie
import com.github.iguanastin.app.menagerie.database.MenagerieDatabase
import com.github.iguanastin.app.menagerie.database.MenagerieDatabaseException
import com.github.iguanastin.app.menagerie.import.RemoteImportJob
import com.github.iguanastin.view.MainView
import com.github.iguanastin.view.runOnUIThread
import javafx.scene.control.ButtonType
import javafx.stage.Stage
import tornadofx.*
import java.io.File
import kotlin.concurrent.thread

class MyApp : App(MainView::class, Styles::class) {

    private val dbURL = "~/test-sfw"
    private val dbUser = "sa"
    private val dbPass = ""

    private lateinit var manager: MenagerieDatabase
    private lateinit var menagerie: Menagerie

    private lateinit var mainView: MainView


    override fun start(stage: Stage) {
        super.start(stage)
        mainView = find(primaryView) as MainView

        println(RemoteImportJob.intoDirectory("https://pbs.twimg.com/media/EibOkEzWoAMlE_Y.jpg?format=jpg&name=orig", File("D:\\(cjdrfr)\\cj\\New folder\\General")).file)

        attemptLoadMenagerie(stage)
    }

    override fun stop() {
        super.stop()

        println("Defragging...")
        manager.closeAndCompress()
    }

    private fun attemptLoadMenagerie(stage: Stage) {
        try {
            manager = MenagerieDatabase(dbURL, dbUser, dbPass)

            val load: () -> Unit = {
                try {
                    menagerie = manager.loadMenagerie()
                    runOnUIThread {
                        mainView.setItems(menagerie.items)
                    }
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