package com.github.iguanastin.view.dialog

import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import tornadofx.*

class InfoStackDialog(header: String, message: String, okText: String = "Ok", onOk: () -> Unit = {}, onClose: () -> Unit = {}) : StackDialog(onClose) {

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
            borderpane {
                right {
                    okButton = button(okText) {
                        onAction = EventHandler { event ->
                            close()
                            event.consume()
                            onOk()
                        }
                    }
                }
            }
        }

        addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.ENTER) {
                event.consume()
                okButton.fire()
            }
        }
    }

    override fun requestFocus() {
        okButton.requestFocus()
    }

}