package com.github.iguanastin.view.dialog

import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import tornadofx.*

class TextInputDialog(header: String, text: String = "", prompt: String = "", onAccept: (String) -> Unit, onClose: () -> Unit = {}): StackDialog(onClose) {

    private val textField: TextField
    private lateinit var acceptButton: Button

    init {
        root.graphic = VBox(10.0).apply {
            padding = insets(25.0)
            minWidth = 300.0

            label(header) {
                style {
                    fontSize = 18.px
                }
            }
            textField = textfield(text) {
                promptText = prompt
                onAction = EventHandler { event ->
                    event.consume()
                    acceptButton.fire()
                }
            }
            hbox {
                alignment = Pos.CENTER_RIGHT
                acceptButton = button("Ok") {
                    onAction = EventHandler { event ->
                        event.consume()
                        close()
                        onAccept(textField.text)
                    }
                }
            }
        }
    }

    override fun requestFocus() {
        textField.requestFocus()
    }

}