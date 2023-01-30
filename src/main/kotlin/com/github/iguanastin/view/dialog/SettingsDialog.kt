package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.settings.*
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.text.FontWeight
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Window
import javafx.util.Callback
import tornadofx.*
import java.io.File

class SettingsDialog(private val prefs: AppSettings) : StackDialog() {

    private lateinit var cancelButton: Button

    private val settingNodeMap: MutableMap<Setting<out Any>, Node> = mutableMapOf()

    private val tagColorizerCellFactory = Callback<ListView<TagColorRule>, ListCell<TagColorRule>?> {
        object : ListCell<TagColorRule>() {
            private val regexField: TextField
            private val colorField: TextField

            init {
                editableProperty().bind(itemProperty().isNotNull)
                graphic = HBox(5.0).apply {
                    hgrow = Priority.ALWAYS
                    regexField = textfield {
                        hgrow = Priority.ALWAYS
                        textFormatter = TextFormatter<String> { change ->
                            if (change.text == " ") change.text = ""
                            return@TextFormatter change
                        }
                        promptText = "Regex of tag name"
                        editableWhen(editingProperty())
                        visibleWhen(itemProperty().isNotNull)
                    }
                    colorField = textfield {
                        hgrow = Priority.ALWAYS
                        textFormatter = TextFormatter<String> { change ->
                            if (change.text == " ") change.text = ""
                            return@TextFormatter change
                        }
                        promptText = "Color to apply"
                        editableWhen(editingProperty())
                        visibleWhen(itemProperty().isNotNull)
                    }
                    button {
                        textProperty().bind(editingProperty().map { editing -> if (editing) "Apply" else "Edit" })
                        visibleWhen(itemProperty().isNotNull)
                        editingProperty().addListener { _, _, new ->
                            if (new) {
                                addClass(Styles.blueBase)
                            } else {
                                removeClass(Styles.blueBase)
                            }
                        }
                        onAction = EventHandler { event ->
                            event.consume()
                            if (isEditing) {
                                commitEdit(TagColorRule(Regex(regexField.text), colorField.text))
                            } else {
                                startEdit()
                            }
                        }
                    }
                    button("x") {
                        visibleWhen(itemProperty().isNotNull)
                        onAction = EventHandler { event ->
                            event.consume()
                            cancelEdit()
                            it.items.remove(item)
                        }
                    }
                }

                editingProperty().addListener { _, _, new -> if (new) Platform.runLater { regexField.requestFocus() } }
            }

            override fun updateItem(item: TagColorRule?, empty: Boolean) {
                super.updateItem(item, empty)

                regexField.text = item?.regex?.pattern
                colorField.text = item?.color
            }
        }
    }

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
            if (group.hidden) return@forEach

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
            is TagColorizerSetting -> {
                val field = listview<TagColorRule> {
                    cellFactory = tagColorizerCellFactory
                    items = observableListOf(setting.value)
                    isEditable = true
                    prefHeight = 100.0
                    placeholder = Label("No tag color rules defined")
                }

                button("+") {
                    onAction = EventHandler { event ->
                        event.consume()
                        val rule = TagColorRule(Regex(""), "")
                        field.items.add(rule)
                        field.edit(field.items.indexOf(rule))
                    }
                }

                field
            }
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
            @Suppress("UNCHECKED_CAST")
            when (setting) {
                is StringSetting -> setting.value = (settingNodeMap[setting] as TextField).text
                is IntSetting -> setting.value = (settingNodeMap[setting] as Spinner<Int>).value
                is DoubleSetting -> setting.value = (settingNodeMap[setting] as Spinner<Double>).value
                is BoolSetting -> setting.value = (settingNodeMap[setting] as CheckBox).isSelected
                is TagColorizerSetting -> setting.value = (settingNodeMap[setting] as ListView<TagColorRule>).items
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