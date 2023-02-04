package com.github.iguanastin.view.dialog

import com.github.iguanastin.view.bindShortcut
import com.github.iguanastin.view.nodes.TopEnabledStackPane
import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.input.KeyCode
import tornadofx.*
import java.awt.Desktop
import java.net.URI

class ConfirmStackDialog(
    header: String,
    message: String,
    url: String? = null,
    confirmText: String = "Ok",
    cancelText: String = "Cancel",
    var onConfirm: () -> Unit = {},
    var onCancel: () -> Unit = {},
    onClose: () -> Unit = {}
) : StackDialog(onClose) {

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
            if (url != null) hyperlink(url) {
                onAction = EventHandler { event ->
                    event.consume()
                    Desktop.getDesktop().browse(URI.create(url))
                }
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

        bindShortcut(KeyCode.ENTER) {
            confirmButton.fire()
        }
    }

    override fun requestFocus() {
        cancelButton.requestFocus()
    }

}

fun TopEnabledStackPane.confirm(
    header: String,
    message: String,
    url: String? = null,
    confirmText: String = "Ok",
    cancelText: String = "Cancel",
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    onClose: () -> Unit = {},
    op: ConfirmStackDialog.() -> Unit = {}
) = ConfirmStackDialog(header = header, message = message, confirmText = confirmText, cancelText = cancelText).attachTo(this, op)
