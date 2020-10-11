package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import tornadofx.*

open class StackDialog(var onClose: () -> Unit = {}): BorderPane() {

    protected val root: Label = Label()

    init {
        center = root

        root.apply {
            addClass(Styles.dialogPane)
        }

        addEventHandler(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.ESCAPE) {
                close()
                event.consume()
            }
        }
    }

    open fun close() {
        removeFromParent()
        onClose()
    }

}