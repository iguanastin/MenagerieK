package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.import.RemoteImportJob
import com.github.iguanastin.view.runOnUIThread
import javafx.beans.InvalidationListener
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.layout.Priority
import javafx.util.Callback
import tornadofx.*

class ImportQueueDialog(val imports: ObservableList<ImportNotification> = observableListOf()) : StackDialog() {

    private lateinit var list: ListView<ImportNotification>

    private val notificationCellFactory = Callback<ListView<ImportNotification>, ListCell<ImportNotification>> {
        object : ListCell<ImportNotification>() {
            private lateinit var statusLabel: Label
            private lateinit var sourceLabel: Label
            private lateinit var progressBar: ProgressBar
            private lateinit var cancelButton: Button

            private val statusListener = ChangeListener<String> { _, _, newValue ->
                runOnUIThread { statusLabel.text = newValue }
            }
            private val progressListener = ChangeListener<Number> { _, _, newValue ->
                runOnUIThread { progressBar.progress = newValue.toDouble() }
            }
            private val finishedListener = ChangeListener<Boolean> { _, _, newValue ->
                runOnUIThread {
                    if (newValue) {
                        if (!hasClass(Styles.finished)) addClass(Styles.finished)
                    } else {
                        removeClass(Styles.finished)
                    }
                    cancelButton.isVisible = !newValue
                    progressBar.isVisible = !newValue
                }
            }

            init {
                graphic = vbox(5.0) {
                    padding = insets(5.0)

                    statusLabel = label { style { textFill = c("white") } }
                    sourceLabel = label { style { textFill = c("grey") } }
                    hbox(5.0) {
                        progressBar = progressbar {
                            hgrow = Priority.ALWAYS
                        }
                        cancelButton = button("Cancel") {
                            onAction = EventHandler { event ->
                                event.consume()
                                item?.job?.cancel()
                                item?.isFinished = true
                            }
                        }
                    }
                }

                prefWidth = 0.0
            }

            override fun updateItem(item: ImportNotification?, empty: Boolean) {
                getItem()?.statusProperty?.removeListener(statusListener)
                getItem()?.progressProperty?.removeListener(progressListener)
                getItem()?.finishedProperty?.removeListener(finishedListener)
                statusLabel.text = ""
                sourceLabel.text = ""
                progressBar.progress = 0.0

                super.updateItem(item, empty)

                if (item?.isFinished == true) {
                    if (!hasClass(Styles.finished)) addClass(Styles.finished)
                } else {
                    removeClass(Styles.finished)
                }
                cancelButton.isVisible = !empty && item?.isFinished == false
                progressBar.isVisible = !empty && item?.isFinished == false

                if (item != null) {
                    statusLabel.text = item.status
                    item.statusProperty.addListener(statusListener)
                    progressBar.progress = item.progress
                    item.progressProperty.addListener(progressListener)
                    item.finishedProperty.addListener(finishedListener)
                    sourceLabel.text = if (item.job is RemoteImportJob) {
                        item.job.url
                    } else {
                        item.job.file.absolutePath
                    }
                }
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

        val isFinishedListener = InvalidationListener { _, ->
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