package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.import.RemoteImportJob
import com.github.iguanastin.view.onActionConsuming
import com.github.iguanastin.view.runOnUIThread
import javafx.beans.InvalidationListener
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.scene.control.Button
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.ProgressBar
import javafx.scene.layout.Priority
import javafx.util.Callback
import tornadofx.*

class ImportQueueDialog(val imports: ObservableList<ImportNotification> = observableListOf()) : StackDialog() {

    private lateinit var list: ListView<ImportNotification>

    private val notificationCellFactory = Callback<ListView<ImportNotification>, ListCell<ImportNotification>> {
        object : ListCell<ImportNotification>() {
            private lateinit var progressBar: ProgressBar
            private lateinit var cancelButton: Button

            private val finishedListener = ChangeListener { _, _, newValue ->
                runOnUIThread { updateFinished(newValue == true) }
            }

            init {
                graphic = vbox(5.0) {
                    padding = insets(5.0)

                    // Status label
                    label {
                        addClass(Styles.importCellStatus)
                        textProperty().bind(itemProperty().flatMap { it.statusProperty })
                    }

                    // Source label
                    label {
                        addClass(Styles.importCellSource)
                        textProperty().bind(itemProperty().map { if (it.job is RemoteImportJob) it.job.url else it?.job?.file?.path })
                    }

                    hbox(5.0) {
                        progressBar = progressbar {
                            hgrow = Priority.ALWAYS
                            progressProperty().bind(itemProperty().flatMap { it.progressProperty })
                        }
                        cancelButton = button("Cancel") {
                            onActionConsuming {
                                item?.job?.cancel()
                                item?.isFinished = true
                            }
                        }
                    }
                }

                prefWidth = 0.0
            }

            override fun updateItem(item: ImportNotification?, empty: Boolean) {
                getItem()?.finishedProperty?.removeListener(finishedListener)

                super.updateItem(item, empty)

                updateFinished(item?.isFinished == true)

                item?.finishedProperty?.addListener(finishedListener)
            }

            private fun updateFinished(finished: Boolean) {
                if (finished) {
                    if (!hasClass(Styles.importFinished)) addClass(Styles.importFinished)
                } else {
                    removeClass(Styles.importFinished)
                }
                cancelButton.isVisible = item != null && !finished
                progressBar.isVisible = item != null && !finished
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
                    button("Cancel All")
                }
                center {
                    button("Duplicates")
                }
                right {
                    togglebutton("Pause", selectFirst = false)
                }
            }
        }

        val isFinishedListener = InvalidationListener { _ ->
            runOnUIThread { imports.sortBy { it.isFinished } }
        }
        imports.forEach { it.finishedProperty.addListener(isFinishedListener) }
        imports.sortBy { it.isFinished }
        imports.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { it.finishedProperty.removeListener(isFinishedListener) }
                change.addedSubList.forEach { it.finishedProperty.addListener(isFinishedListener) }
            }
        })

        list.items = imports
    }

}