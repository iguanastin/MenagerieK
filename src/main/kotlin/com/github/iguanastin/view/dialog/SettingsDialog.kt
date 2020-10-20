package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.MyApp
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
import java.util.prefs.Preferences

class SettingsDialog(private val prefs: Preferences) : StackDialog() {

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

                                label("Database") { style { fontWeight = FontWeight.BOLD } }
                                gridpane {
                                    vgap = 5.0
                                    paddingLeft = 10.0
                                    row {
                                        label("Path/URL: ")
                                        databaseUrlTextField = textfield(prefs.get("db_url", "")) {
                                            promptText = MyApp.defaultDatabaseUrl
                                        }
                                    }
                                    row {
                                        label("Username: ")
                                        databaseUserTextField = textfield(prefs.get("db_user", "")) {
                                            promptText = MyApp.defaultDatabaseUser
                                        }
                                    }
                                    row {
                                        label("Password: ")
                                        databasePasswordTextField = textfield(prefs.get("db_pass", "")) {
                                            promptText = MyApp.defaultDatabasePassword
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

                                    downloadsTextField = textfield(prefs.get("downloads", MyApp.defaultDownloadsPath)) {
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
                                        confidenceTextField = textfield(prefs.getDouble("confidence", MyApp.defaultConfidence).toString()) {
                                            promptText = "0.95"
                                            filterInput { it.controlNewText.isDouble() && it.controlNewText.toDouble() in 0.9..1.0 }
                                        }
                                    }
                                    row {
                                        label("CUDA hardware acceleration: ")
                                        cudaToggle = checkbox {
                                            isSelected = prefs.getBoolean("cuda", MyApp.defaultCUDAEnabled)
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
        if (databaseUrlTextField.text.isNullOrBlank()) {
            prefs.remove("db_url")
        } else {
            prefs.put("db_url", databaseUrlTextField.text)
        }

        if (databaseUserTextField.text.isNullOrBlank()) {
            prefs.remove("db_user")
        } else {
            prefs.put("db_user", databaseUserTextField.text)
        }

        if (databasePasswordTextField.text.isNullOrBlank()) {
            prefs.remove("db_pass")
        } else {
            prefs.put("db_pass", databasePasswordTextField.text)
        }

        if (downloadsTextField.text.isNullOrBlank()) {
            prefs.remove("downloads")
        } else {
            prefs.put("downloads", downloadsTextField.text)
        }

        prefs.putBoolean("cuda", cudaToggle.isSelected)

        if (confidenceTextField.text.isNullOrBlank()) {
            prefs.remove("confidence")
        } else {
            prefs.putDouble("confidence", confidenceTextField.text.toDouble())
        }
    }

    override fun requestFocus() {
        cancelButton.requestFocus()
    }

}