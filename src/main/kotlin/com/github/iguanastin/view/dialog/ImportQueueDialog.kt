package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.context.MenagerieContext
import com.github.iguanastin.app.menagerie.import.Import
import com.github.iguanastin.view.onActionConsuming
import com.github.iguanastin.view.runOnUIThread
import javafx.application.Platform
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.scene.control.Button
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.ProgressBar
import javafx.scene.layout.Priority
import javafx.util.Callback
import tornadofx.*

class ImportQueueDialog(val imports: ObservableList<Import>, val context: MenagerieContext) : StackDialog() {

    private lateinit var list: ListView<Import>

    private val notificationCellFactory = Callback<ListView<Import>, ListCell<Import>> {
        object : ListCell<Import>() {
            private lateinit var progressBar: ProgressBar
            private lateinit var cancelButton: Button

            init {
                toggleClass(Styles.importFinished, itemProperty().flatMap { it.status }.map { it in Import.finishedStates })

                graphic = vbox(5.0) {
                    padding = insets(5.0)

                    // Status label
                    label {
                        addClass(Styles.importCellStatus)
                        val update = ChangeListener<Import.Status> { _, _, new -> Platform.runLater { // Note: Needs to be Platform.runLater instead of runOnUIThread
                            text = new.toString()
                            toggleClass(Styles.redText, new == Import.Status.FAILED)
                        } }
                        itemProperty().addListener { _, old, new ->
                            old?.status?.removeListener(update)
                            new?.status?.addListener(update)
                            update.changed(null, null, new?.status?.value ?: return@addListener)
                        }
                    }

                    // Source label
                    label {
                        addClass(Styles.importCellSource)
                        itemProperty().addListener { _, _, new -> runOnUIThread { text = new?.url ?: new?.file?.path } }
                    }

                    borderpane {
                        left {
                            progressBar = progressbar {
                                hgrow = Priority.ALWAYS
                                val update =
                                    ChangeListener<Number> { _, _, new -> runOnUIThread { progress = new.toDouble() } }
                                itemProperty().addListener { _, old, new ->
                                    old?.progress?.removeListener(update)
                                    new?.progress?.addListener(update)
                                    update.changed(null, null, new?.progress?.value ?: -1.0)
                                }
                                visibleWhen(itemProperty().flatMap { it.status }.map { it != Import.Status.READY })
                            }
                        }
                        right {
                            cancelButton = button("Cancel") {
                                onActionConsuming {
                                    item?.cancel()
                                }
                                visibleWhen(itemProperty().flatMap { it.status }.map { it !in Import.finishedStates })
                            }
                        }
                    }
                }

                prefWidth = 0.0
            }
        }
    }

    init {
        root.graphic = vbox(5.0) {
            padding = insets(5.0)
            prefWidth = 500.0
            prefHeightProperty().bind(this@ImportQueueDialog.heightProperty().multiply(0.8))

            label("Imports") { style { fontSize = 18.px } }
            list = listview {
                vgrow = Priority.ALWAYS
                isFocusTraversable = false
                cellFactory = notificationCellFactory
            }
            borderpane {
                left {
                    button("Cancel All") {
                        onActionConsuming { imports.toList().forEach { it.cancel() } }
                    }
                }
                right {
                    togglebutton("Pause", selectFirst = context.importer.paused) {
                        onActionConsuming { context.importer.togglePause() }
                        textProperty().bind(selectedProperty().map { if (it) "Resume" else "Pause" })
                    }
                }
            }
        }

        val statusListener = ChangeListener<Import.Status> { _, _, _ ->
            runOnUIThread { imports.sortBy { !it.isReadyOrRunning() } }
        }
        // TODO should REALLY be only sorting/displaying a copy of the import list
        imports.forEach { it.status.addListener(statusListener) }
        imports.sortBy { !it.isReadyOrRunning() }
        imports.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { it.status.removeListener(statusListener) }
                change.addedSubList.forEach { it.status.addListener(statusListener) }
            }
        })

        list.items = imports
    }

}