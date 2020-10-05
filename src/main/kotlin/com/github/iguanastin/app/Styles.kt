package com.github.iguanastin.app

import javafx.scene.Cursor
import javafx.scene.layout.BorderPane
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import org.controlsfx.control.GridView
import tornadofx.*

class Styles : Stylesheet() {
    companion object {
        val heading by cssclass()
        val fourChoice by cssclass()
        val dialogPane by cssclass()
        val gridView by cssclass()
        val gridCell by cssclass()
    }

    init {
        root {
            baseColor = Color.color(0.1, 0.1, 0.1)!!
        }
        label and heading {
            padding = box(10.px)
            fontSize = 20.px
            fontWeight = FontWeight.BOLD
        }
        label and fourChoice {
            and(hover) {
                cursor = Cursor.HAND
            }
            padding = box(25.px)
            backgroundRadius += box(10.px)
            backgroundColor += Color.color(0.25, 0.25, 0.25, 0.75)!!
        }
        dialogPane {
            backgroundColor += Color.color(0.0, 0.0, 0.0, 0.5)
        }
        gridView {
            minWidth = 525.px
        }
        gridCell {
            backgroundColor += Color.GREY
        }
    }
}