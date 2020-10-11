package com.github.iguanastin.view.dialog

import com.github.iguanastin.view.TopEnabledStackPane
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.layout.VBox
import tornadofx.*
import java.io.File

class ImportDialog(files: List<File>, var individually: () -> Unit = {}, var asGroup: () -> Unit = {}, var dirsAsGroups: () -> Unit = {}, var onCancel: () -> Unit = {}): StackDialog() {

    init {
        root.graphic = VBox(10.0).apply {
            padding = insets(25.0)
            minWidth = 300.0
            alignment = Pos.CENTER

            hbox {
                alignment = Pos.CENTER_LEFT
                label("Import") {
                    style {
                        fontSize = 18.px
                    }
                }
            }

            button("Individually") {
                onAction = EventHandler { event ->
                    event.consume()
                    close()
                    individually()
                }
            }
            button("As group") {
                onAction = EventHandler { event ->
                    event.consume()
                    close()
                    asGroup()
                }
            }
            for (file in files) {
                if (file.isDirectory) {
                    button("Folders as groups") {
                        onAction = EventHandler { event ->
                            event.consume()
                            close()
                            dirsAsGroups()
                        }
                    }
                    break
                }
            }

            hbox {
                alignment = Pos.CENTER_RIGHT
                button("Cancel") {
                    onAction = EventHandler { event ->
                        event.consume()
                        close()
                        onCancel()
                    }
                }
            }
        }
    }

}

fun TopEnabledStackPane.importdialog(files: List<File>, op: ImportDialog.() -> Unit = {}) = ImportDialog(files).attachTo(this, op)