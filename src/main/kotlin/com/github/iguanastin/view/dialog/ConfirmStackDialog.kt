package com.github.iguanastin.view.dialog

import com.github.iguanastin.view.nodes.TopEnabledStackPane
import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import tornadofx.*

class ConfirmStackDialog(header: String, message: String, confirmText: String = "Ok", cancelText: String = "Cancel", var onConfirm: () -> Unit = {}, var onCancel: () -> Unit = {}, onClose: () -> Unit = {}): StackDialog(onClose) {

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
                                close()
                                event.consume()
                                onConfirm()
                            }
                        }
                        cancelButton = button(cancelText) {
                            onAction = EventHandler { event ->
                                close()
                                event.consume()
                                onCancel()
                            }
                        }
                    }
                }
            }
        }

        addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.ENTER) {
                event.consume()
                confirmButton.fire()
            }
        }
    }

    override fun requestFocus() {
        cancelButton.requestFocus()
    }

}

fun TopEnabledStackPane.confirm(header: String, message: String, confirmText: String = "Ok", cancelText: String = "Cancel", op: ConfirmStackDialog.() -> Unit = {}) = ConfirmStackDialog(header, message, confirmText, cancelText).attachTo(this, op)
