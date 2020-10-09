package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import com.github.iguanastin.view.dialog.fourChooser
import javafx.application.Platform
import javafx.event.EventTarget
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.image.Image
import javafx.scene.layout.BorderPane
import javafx.stage.Window
import org.controlsfx.control.GridView
import tornadofx.*
import java.io.File
import java.util.concurrent.CountDownLatch


fun fourchooser(root: View) {
    var bp: BorderPane? = null
    bp = root.borderpane {
        addClass(Styles.dialogPane)
        center {
            fourChooser {
                // TODO finish this
                onLeft = "Left" to { println("left") }
                onRight = "Right" to { println("right") }
                onTop = "Top" to { println("top") }
                onBottom = "Bottom" to { println("bottom") }
                onCancel = { println("cancel") }
                onClose = {
                    bp?.removeFromParent()
                    println("close")
                }
            }
        }
    }
}

inline fun <T> EventTarget.gridView(op: GridView<T>.() -> Unit = {}) = GridView<T>().attachTo(this, op)

fun confirm(header: String, content: String = "", confirmButton: ButtonType = ButtonType.OK, cancelButton: ButtonType = ButtonType.CANCEL, owner: Window? = null, title: String? = null, confirmed: () -> Unit, canceled: () -> Unit) {
    alert(Alert.AlertType.CONFIRMATION, header, content, confirmButton, cancelButton, owner = owner, title = title) {
        when (it) {
            confirmButton -> confirmed()
            cancelButton -> canceled()
        }
    }
}

fun image(file: File, backgroundLoading: Boolean = false): Image {
    return Image(file.toURI().toString(), backgroundLoading)
}

fun Image.blockUntilLoaded(): Image {
    val cdl = CountDownLatch(1)
    val listener = ChangeListener<Number> { _, _, newValue ->
        if (isError || newValue == 1.0) cdl.countDown()
    }
    progressProperty().addListener(listener)
    if (isBackgroundLoading && progress != 1.0 && !isError) cdl.await()
    progressProperty().removeListener(listener)

    return this
}

fun runOnUIThread(op: () -> Unit) {
    if (Platform.isFxApplicationThread()) op()
    else Platform.runLater(op)
}