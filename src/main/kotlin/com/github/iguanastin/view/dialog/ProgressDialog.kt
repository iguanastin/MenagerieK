package com.github.iguanastin.view.dialog

import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import tornadofx.*

class ProgressDialog(header: String = "Progress", message: String = "", progress: Double = -1.0) : StackDialog() {

    private lateinit var headerLabel: Label
    private lateinit var messageLabel: Label
    private lateinit var progressBar: ProgressBar

    var header: String
        get() = headerLabel.text
        set(value) {
            headerLabel.text = value
        }

    var message: String
        get() = messageLabel.text
        set(value) {
            messageLabel.text = value
        }

    var progress: Double
        get() = progressBar.progress
        set(value) {
            progressBar.progress = value
        }

    init {
        root.graphic = vbox(10) {
            padding = insets(25.0)
            minWidth = 300.0
            alignment = Pos.CENTER

            headerLabel = label(header) {
                style(true) {
                    fontSize = 18.px
                }
            }
            messageLabel = label(message)
            progressBar = progressbar(progress) {
                prefWidth = 200.0
            }
        }
    }

}