package com.github.iguanastin.view.dialog

import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import tornadofx.*

class ConfirmStackDialog(header: String, message: String, confirmText: String = "Ok", cancelText: String = "Cancel", onConfirm: () -> Unit = {}, onCancel: () -> Unit = {}, onClose: () -> Unit = {}): StackDialog(onClose) {

    private lateinit var cancelButton: Button
    private lateinit var confirmButton: Button

    init {
        root.graphic = vbox(10) {
            padding = insets(25.0)
            minWidth = 300.0

            label(header) {
                style(true) {
                    fontSize = 18.px
                }
            }
            label(message) {
                isWrapText = true
            }
            borderpane {
                right {
                    hbox(10) {
                        confirmButton = button(confirmText) {
                            onAction = EventHandler { event ->
                                onConfirm()
                                close()
                                event.consume()
                            }
                        }
                        cancelButton = button(cancelText) {
                            onAction = EventHandler { event ->
                                onCancel()
                                close()
                                event.consume()
                            }
                        }
                    }
                }
            }
        }

        addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.ENTER) {
                confirmButton.fire()
                event.consume()
            }
        }
    }

    override fun requestFocus() {
        cancelButton.requestFocus()
    }

}