package com.github.iguanastin.view.dialog

import com.github.iguanastin.view.nodes.TopEnabledStackPane
import com.github.iguanastin.view.onActionConsuming
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

            vbox(10.0) {
                button("Individually") {
                    maxWidth = Double.MAX_VALUE
                    style { padding = box(10.px) }
                    onActionConsuming {
                        close()
                        individually()
                    }
                }
                button("As group") {
                    maxWidth = Double.MAX_VALUE
                    style { padding = box(10.px) }
                    onActionConsuming {
                        close()
                        asGroup()
                    }
                }
                if (files.size > 1) {
                    for (file in files) {
                        if (file.isDirectory) {
                            button("Folders as groups") {
                                maxWidth = Double.MAX_VALUE
                                style { padding = box(10.px) }
                                onActionConsuming {
                                    close()
                                    dirsAsGroups()
                                }
                            }
                            break
                        }
                    }
                }
            }

            hbox {
                alignment = Pos.CENTER_RIGHT
                button("Cancel") {
                    onActionConsuming {
                        close()
                        onCancel()
                    }
                }
            }
        }
    }

}

fun TopEnabledStackPane.importdialog(files: List<File>, op: ImportDialog.() -> Unit = {}) = ImportDialog(files).attachTo(this, op)