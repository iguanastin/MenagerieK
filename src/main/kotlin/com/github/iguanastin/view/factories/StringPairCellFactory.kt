package com.github.iguanastin.view.factories

import com.github.iguanastin.app.Styles
import com.github.iguanastin.view.onActionConsuming
import javafx.application.Platform
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.util.Callback
import tornadofx.*

class StringPairCellFactory(private val firstPrompt: String?, private val secondPrompt: String?): Callback<ListView<Pair<String, String>>, ListCell<Pair<String, String>>?> {

    override fun call(listView: ListView<Pair<String, String>>?): ListCell<Pair<String, String>> {
        return object : ListCell<Pair<String, String>>() {
            private val firstField: TextField
            private lateinit var secondField: TextField
            private lateinit var editApplyButton: Button

            init {
                editableProperty().bind(itemProperty().isNotNull)
                graphic = HBox(5.0).apply {
                    hgrow = Priority.ALWAYS
                    firstField = textfield {
                        hgrow = Priority.ALWAYS
                        textFormatter = TextFormatter<String> { change ->
                            if (change.text == " ") change.text = ""
                            return@TextFormatter change
                        }
                        onActionConsuming { secondField.requestFocus() }
                        promptText = firstPrompt
                        editableWhen(editingProperty())
                        visibleWhen(itemProperty().isNotNull)
                    }
                    secondField = textfield {
                        hgrow = Priority.ALWAYS
                        textFormatter = TextFormatter<String> { change ->
                            if (change.text == " ") change.text = ""
                            return@TextFormatter change
                        }
                        onActionConsuming { editApplyButton.fire() }
                        promptText = secondPrompt
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
                        onActionConsuming {
                            if (isEditing) {
                                commitEdit(Pair(firstField.text, secondField.text))
                            } else {
                                startEdit()
                            }
                        }
                    }
                    button("x") {
                        visibleWhen(itemProperty().isNotNull)
                        onActionConsuming {
                            cancelEdit()
                            listView?.items?.remove(item)
                        }
                    }
                }

                editingProperty().addListener { _, _, new -> if (new) Platform.runLater { firstField.requestFocus() } }
            }

            override fun updateItem(item: Pair<String, String>?, empty: Boolean) {
                super.updateItem(item, empty)

                firstField.text = item?.first
                secondField.text = item?.second
            }
        }
    }
}