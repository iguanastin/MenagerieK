package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.settings.*
import javafx.event.EventHandler
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.text.FontWeight
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Window
import tornadofx.*
import java.io.File

class SettingsDialog(private val prefs: AppSettings) : StackDialog() {

    private lateinit var cancelButton: Button

    private val settingNodeMap: MutableMap<Setting<out Any>, Node> = mutableMapOf()

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

                            initSettingNodes()
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
                            applySettings()
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

    private fun EventTarget.initSettingNodes() {
        prefs.groups.forEach { group ->
            vbox {
                padding = insets(5.0)

                label(group.title) { style { fontWeight = FontWeight.BOLD } }
                gridpane {
                    vgap = 5.0
                    paddingLeft = 10.0
                    constraintsForColumn(1).hgrow = Priority.ALWAYS

                    group.settings.forEach { setting ->
                        row {
                            hgap = 5.0
                            label("${setting.label ?: setting.key}:")

                            val field: Node = createFieldForSetting(setting)
                            settingNodeMap[setting] = field
                        }
                    }
                }
            }
        }
    }

    private fun Pane.createFieldForSetting(setting: Setting<out Any>) =
        when (setting) {
            is StringSetting -> {
                val field = textfield(setting.value) {
                    hgrow = Priority.ALWAYS
                    promptText = setting.default
                }
                if (setting.type in arrayOf(StringSetting.Type.FILE_PATH, StringSetting.Type.FOLDER_PATH)) {
                    initFileBrowseButton(field, setting)
                }

                field
            }
            is IntSetting -> spinner(
                editable = true,
                enableScroll = true,
                initialValue = setting.value,
                min = setting.min,
                max = setting.max
            )
            is DoubleSetting -> spinner(
                editable = true,
                enableScroll = true,
                initialValue = setting.value,
                min = setting.min,
                max = setting.max
            )
            is BoolSetting -> checkbox {
                isSelected = setting.value
            }
            else -> label("[ERROR]")
        }

    private fun Pane.initFileBrowseButton(
        field: TextField,
        setting: StringSetting
    ) {
        button("Browse") {
            onAction = EventHandler { event ->
                event.consume()
                var initial: File? = File(field.text)
                if (initial?.exists() == false || initial?.isDirectory == false) initial =
                    initial.parentFile
                if (initial?.exists() == false || initial?.isDirectory == false) initial = null

                if (setting.type == StringSetting.Type.FILE_PATH) {
                    val file = fileChooser(initial, scene.window) ?: return@EventHandler
                    field.text = file.absolutePath
                } else if (setting.type == StringSetting.Type.FOLDER_PATH) {
                    val folder = directoryChooser(initial, scene.window) ?: return@EventHandler
                    field.text = folder.absolutePath
                }
            }
        }
    }

    private fun applySettings() {
        settingNodeMap.keys.forEach { setting ->
            when (setting) {
                is StringSetting -> setting.value = (settingNodeMap[setting] as TextField).text
                is IntSetting -> setting.value = (settingNodeMap[setting] as Spinner<Int>).value
                is DoubleSetting -> setting.value = (settingNodeMap[setting] as Spinner<Double>).value
                is BoolSetting -> setting.value = (settingNodeMap[setting] as CheckBox).isSelected
            }
        }
    }

    private fun directoryChooser(initialDir: File? = null, window: Window): File? {
        val chooser = DirectoryChooser()
        chooser.initialDirectory = initialDir
        chooser.title = "Choose folder"

        return chooser.showDialog(window)
    }

    private fun fileChooser(initialDir: File? = null, window: Window): File? {
        val chooser = FileChooser()
        chooser.initialDirectory = initialDir
        chooser.title = "Choose file"

        return chooser.showOpenDialog(window)
    }

    override fun requestFocus() {
        cancelButton.requestFocus()
    }

}