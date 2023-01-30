package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.settings.TagColorRule
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.util.Callback
import tornadofx.*

class TagColorRuleCellFactory: Callback<ListView<TagColorRule>, ListCell<TagColorRule>?> {

    override fun call(listView: ListView<TagColorRule>?): ListCell<TagColorRule> {
        return object : ListCell<TagColorRule>() {
            private val regexField: TextField
            private lateinit var colorField: TextField
            private lateinit var editApplyButton: Button

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
                        onAction = EventHandler { event ->
                            event.consume()
                            colorField.requestFocus()
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
                        onAction = EventHandler { event ->
                            event.consume()
                            editApplyButton.fire()
                        }
                        promptText = "Color to apply"
                        editableWhen(editingProperty())
                        visibleWhen(itemProperty().isNotNull)
                    }
                    editApplyButton = button {
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
                            listView?.items?.remove(item)
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
}