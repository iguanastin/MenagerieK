package com.github.iguanastin.view

import javafx.event.EventTarget
import javafx.scene.Node
import javafx.scene.layout.StackPane
import tornadofx.*

class FocusingStackPane : StackPane() {

    var focuses: Node? = null


    override fun requestFocus() {
        if (focuses != null) {
            focuses?.requestFocus()
        } else {
            super.requestFocus()
        }
    }

}

fun EventTarget.focusingstackpane(op: FocusingStackPane.() -> Unit): FocusingStackPane = FocusingStackPane().attachTo(this, op)