package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.settings.AppSettings
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.TextField
import javafx.scene.layout.Priority
import javafx.scene.text.FontWeight
import javafx.stage.DirectoryChooser
import tornadofx.*
import java.io.File

class SettingsDialog(private val prefs: AppSettings) : StackDialog() {

    private lateinit var cancelButton: Button
    private lateinit var downloadsBrowseButton: Button
    private lateinit var downloadsTextField: TextField
    private lateinit var databaseUrlTextField: TextField
    private lateinit var databaseUserTextField: TextField
    private lateinit var databasePasswordTextField: TextField
    private lateinit var confidenceTextField: TextField
    private lateinit var cudaToggle: CheckBox

    init {
        root.graphic = borderpane {
            minWidth = 600.0
            minHeight = 600.0

            top {
                borderpane {
                    padding = insets(5.0)
                    left {
                        borderpaneConstraints { alignment = Pos.CENTER_LEFT }
                        label("Settings") { style { fontSize = 18.px } }
                    }
                    right {
                        borderpaneConstraints { alignment = Pos.CENTER_RIGHT }
                        button("X") {
                            onAction = EventHandler { event ->
                                event.consume()
                                close()
                            }
                        }
                    }
                }
            }

            center {
                scrollpane {
                    isFitToWidth = true
                    content = anchorpane {
                        vbox(10.0) {
                            anchorpaneConstraints {
                                leftAnchor = 0
                                rightAnchor = 0
                            }

                            vbox(5.0) {
                                padding = insets(5.0)

                                // TODO dynamically generate views for each setting
                                label("Database") { style { fontWeight = FontWeight.BOLD } }
                                gridpane {
                                    vgap = 5.0
                                    paddingLeft = 10.0
                                    row {
                                        label("Path/URL: ")
                                        databaseUrlTextField = textfield(prefs.database.url.value) {
                                            promptText = prefs.database.url.default
                                        }
                                    }
                                    row {
                                        label("Username: ")
                                        databaseUserTextField = textfield(prefs.database.user.value) {
                                            promptText = prefs.database.user.default
                                        }
                                    }
                                    row {
                                        label("Password: ")
                                        databasePasswordTextField = textfield(prefs.database.pass.value) {
                                            promptText = prefs.database.pass.default
                                        }
                                    }
                                }
                            }

                            vbox(5.0) {
                                padding = insets(5.0)

                                label("Downloads directory") { style { fontWeight = FontWeight.BOLD } }
                                hbox(5.0) {
                                    paddingLeft = 10.0
                                    alignment = Pos.CENTER_LEFT

                                    downloadsTextField = textfield(prefs.general.downloadFolder.value) {
                                        hgrow = Priority.ALWAYS
                                        promptText = "Path to default downloads directory"
                                    }
                                    downloadsBrowseButton = button("Browse") {
                                        onAction = EventHandler { event ->
                                            event.consume()
                                            val dc = DirectoryChooser()
                                            dc.title = "Choose default downloads directory"
                                            if (!downloadsTextField.text.isNullOrBlank()) {
                                                val current = File(downloadsTextField.text)
                                                if (current.exists()) dc.initialDirectory = current
                                            }
                                            val dir = dc.showDialog(scene.window) ?: return@EventHandler

                                            downloadsTextField.text = dir.absolutePath
                                        }
                                    }
                                }
                            }

                            vbox(5.0) {
                                padding = insets(5.0)

                                label("Duplicate Finding") { style { fontWeight = FontWeight.BOLD } }
                                gridpane {
                                    paddingLeft = 10.0
                                    alignment = Pos.CENTER_LEFT
                                    vgap = 5.0

                                    row {
                                        label("Confidence: ")
                                        confidenceTextField = textfield(prefs.duplicate.confidence.value.toString()) {
                                            promptText = prefs.duplicate.confidence.default.toString()
                                            filterInput { it.controlNewText.isDouble() && it.controlNewText.toDouble() in 0.9..1.0 }
                                        }
                                    }
                                    row {
                                        label("CUDA hardware acceleration: ")
                                        cudaToggle = checkbox {
                                            isSelected = prefs.duplicate.enableCuda.value
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            bottom {
                hbox(5.0) {
                    padding = insets(5.0)
                    alignment = Pos.CENTER_RIGHT

                    button("Apply") {
                        onAction = EventHandler { event ->
                            event.consume()
                            apply()
                            close()
                        }
                    }
                    cancelButton = button("Cancel") {
                        onAction = EventHandler { event ->
                            event.consume()
                            close()
                        }
                    }
                }
            }
        }
    }

    private fun apply() {
        prefs.database.url.value = databaseUrlTextField.text

        prefs.database.user.value = databaseUserTextField.text

        prefs.database.pass.value = databasePasswordTextField.text

        prefs.general.downloadFolder.value = downloadsTextField.text

        prefs.duplicate.enableCuda.value = cudaToggle.isSelected

        prefs.duplicate.confidence.value = confidenceTextField.text.toDouble()
    }

    override fun requestFocus() {
        cancelButton.requestFocus()
    }

}