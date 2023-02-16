package com.github.iguanastin.view.nodes

import com.github.iguanastin.view.onActionConsuming
import javafx.geometry.Pos
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import tornadofx.*

class TagColorPicker(onChoose: (String?) -> Unit): HBox(5.0) {

    private val defaultColors = arrayOf("#609dff", "cyan", "#22e538", "yellow", "orange", "red", "#ff7ae6", "#bf51ff")

    init {
        alignment = Pos.CENTER

        for (color in defaultColors) {
            button {
                style {
                    baseColor = c(color)
                }
                onActionConsuming { onChoose(color) }
                tooltip(color)
            }
        }

        textfield {
            promptText = "E.g. #ffffff"
            onActionConsuming { onChoose(text) }
        }

        button {
            onActionConsuming { onChoose(null) }
            graphic = ImageView(Image(TagColorPicker::class.java.getResource("/imgs/resetcolor.png")?.toExternalForm()))
            tooltip("Reset")
        }
    }

}