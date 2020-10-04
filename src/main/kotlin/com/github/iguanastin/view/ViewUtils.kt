package com.github.iguanastin.view

import javafx.application.Platform
import javafx.event.EventTarget
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.stage.Window
import org.controlsfx.control.GridView
import tornadofx.*


inline fun <T> EventTarget.gridView(op: GridView<T>.() -> Unit = {}) = GridView<T>().attachTo(this, op)

inline fun confirm(header: String, content: String = "", confirmButton: ButtonType = ButtonType.OK, cancelButton: ButtonType = ButtonType.CANCEL, owner: Window? = null, title: String? = null, confirmed: () -> Unit, canceled: () -> Unit) {
    alert(Alert.AlertType.CONFIRMATION, header, content, confirmButton, cancelButton, owner = owner, title = title) {
        when (it) {
            confirmButton -> confirmed()
            cancelButton -> canceled()
        }
    }
}

fun runOnUIThread(op: () -> Unit) {
    if (Platform.isFxApplicationThread()) op()
    else Platform.runLater(op)
}