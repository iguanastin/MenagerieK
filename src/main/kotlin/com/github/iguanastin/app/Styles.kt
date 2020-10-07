package com.github.iguanastin.app

import javafx.scene.Cursor
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import tornadofx.*

class Styles : Stylesheet() {
    companion object {
        val heading by cssclass()
        val fourChoice by cssclass()
        val dialogPane by cssclass()
        val gridView by cssclass()
        val itemGridCell by cssclass()
        val selected by csspseudoclass()
    }

    init {
        root {
            baseColor = Color.web("#3b3f42")!!
            backgroundColor += Color.web("#3b3f42")
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
        listCell {
            and(odd) {
                backgroundColor += Color.web("#3e3e3e")
            }
            backgroundColor += Color.web("#303030")
        }
        gridView {
            minWidth = 525.px
        }
        itemGridCell {
            backgroundColor += Color.web("#606467")
        }
        itemGridCell and selected {
            backgroundColor += Color.web("#4e98a8")
        }
        dialogPane {
            backgroundColor += Color.web("#3b3f42")
        }
    }
}