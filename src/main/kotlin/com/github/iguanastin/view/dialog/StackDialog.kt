package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import com.github.iguanastin.view.bindShortcut
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.layout.BorderPane
import tornadofx.*

open class StackDialog(var onClose: () -> Unit = {}): BorderPane() {

    protected val root: Label = Label()

    init {
        center = root

        root.apply {
            addClass(Styles.dialogPane)
        }

        bindShortcut(KeyCode.ESCAPE) {
            close()
        }
    }

    open fun close() {
        removeFromParent()
        onClose()
    }

}