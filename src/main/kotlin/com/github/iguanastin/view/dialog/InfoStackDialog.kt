package com.github.iguanastin.view.dialog

import com.github.iguanastin.view.bindShortcut
import com.github.iguanastin.view.onActionConsuming
import javafx.scene.control.Button
import javafx.scene.input.KeyCode
import tornadofx.*
import java.awt.Desktop
import java.net.URI

class InfoStackDialog(header: String, message: String, url: String? = null, okText: String = "Ok", onOk: () -> Unit = {}, onClose: () -> Unit = {}) : StackDialog(onClose) {

    private lateinit var okButton: Button

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
            if (url != null) hyperlink("Get the latest release") {
                onActionConsuming {
                    Desktop.getDesktop().browse(URI.create(url))
                }
            }
            borderpane {
                right {
                    okButton = button(okText) {
                        onActionConsuming {
                            close()
                            onOk()
                        }
                    }
                }
            }
        }

        bindShortcut(KeyCode.ENTER) {
            okButton.fire()
        }
    }

    override fun requestFocus() {
        okButton.requestFocus()
    }

}