package com.github.iguanastin.app

import javafx.scene.Cursor
import javafx.scene.effect.DropShadow
import javafx.scene.layout.BorderStrokeStyle
import javafx.scene.text.FontWeight
import javafx.scene.text.TextAlignment
import tornadofx.*

class Styles : Stylesheet() {
    companion object {
        val heading by cssclass()
        val fourChoice by cssclass()
        val dialogPane by cssclass()
        val itemGridView by cssclass()
        val itemGridCell by cssclass()
        val transparentOverlay by cssclass()
        val dragDropDialog by cssclass()
        val multiSelectButton by cssclass()

        val selected by csspseudoclass()
        val softSelected by csspseudoclass()
        val finished by csspseudoclass()
    }

    init {
        root {
            baseColor = c("#3b3f42")
            backgroundColor += c("#3b3f42")
        }
        label {
            and(heading) {
                padding = box(10.px)
                fontSize = 20.px
                fontWeight = FontWeight.BOLD
            }
            and(fourChoice) {
                and(hover) {
                    cursor = Cursor.HAND
                }
                padding = box(25.px)
                backgroundRadius += box(10.px)
                backgroundColor += c(0.25, 0.25, 0.25, 0.75)
            }
            and(selected) {
                backgroundColor += c("#4e98a8")
            }
            and(softSelected) {
                backgroundColor += c("#00565e")
            }
        }
        listView {
            backgroundColor += c("#303030")
        }
        listCell {
            backgroundColor += c("#303030")
            and(odd) {
                backgroundColor += c("#3e3e3e")
            }
            and(finished) {
                backgroundColor += c("#242424")
            }
        }
        itemGridView {
            minWidth = 525.px
        }
        itemGridCell {
            backgroundColor += c("#606467")
            and(selected) {
                backgroundColor += c("#4e98a8")
            }
        }
        dialogPane {
            backgroundColor += c("#3b3f42")
            effect = DropShadow(50.0, c("black")).apply { spread = 0.25 }
        }
        transparentOverlay {
            backgroundColor += c(0, 0, 0, 0.33)
        }
        dragDropDialog {
            fontSize = 24.px
            wrapText = true
            padding = box(25.px)
            textAlignment = TextAlignment.CENTER

            backgroundColor += c(0, 0, 0, 0.66)
            backgroundRadius += box(25.px)

            borderRadius += box(25.px)
            borderStyle += BorderStrokeStyle.DASHED
            borderColor += box(c("grey"))
            borderWidth += box(2.px)
        }
        textField {
            textFill = c("white")
            backgroundColor += c("#242424")
        }
        button {
            padding = box(5.px, 10.px)
            and(multiSelectButton) {
                prefHeight = 100.px
                padding = box(25.px)
            }
        }
    }
}