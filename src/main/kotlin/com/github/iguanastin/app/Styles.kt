package com.github.iguanastin.app

import javafx.scene.Cursor
import javafx.scene.effect.DropShadow
import javafx.scene.layout.BorderStrokeStyle
import javafx.scene.paint.Color
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
        val tagListCell by cssclass()
        val tourOutline by cssclass()
        val tourText by cssclass()
        val bastardTourParentPane by cssclass()
        val helpHeader by cssclass()
        val infoLabel by cssclass()

        val blueBase by csspseudoclass()
        val selected by csspseudoclass()
        val autocompleteSoftSelected by csspseudoclass()
        val importFinished by csspseudoclass()

        val baseColor = c("#3b3f42")
    }

    init {
        root {
            baseColor = Styles.baseColor
            backgroundColor += Styles.baseColor
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
            and(autocompleteSoftSelected) {
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
            and(importFinished) {
                backgroundColor += c("#242424")
            }
        }

        tagListCell {
            and(hover) {
                backgroundColor += c("#194070")
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
            backgroundRadius += box(3.px)
        }

        dialogPane {
            backgroundColor += Styles.baseColor
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
            and(blueBase) {
                baseColor = c("#4e98a8")
            }
        }

        tourOutline {
            backgroundColor += Color.TRANSPARENT
            borderColor += box(Color.WHITE)
            borderStyle += BorderStrokeStyle.DASHED
            borderRadius += box(5.px)
            borderWidth += box(5.px)
        }

        tourText {
            backgroundColor += c(0, 0, 0, 0.75)
            backgroundRadius += box(5.px)
        }

        bastardTourParentPane {
            backgroundColor += Color.TRANSPARENT
        }

        helpHeader {
            fontWeight = FontWeight.BOLD
            fontSize = 1.5.em
        }

        infoLabel {
            cursor = Cursor.HAND
            and(hover) {
                backgroundColor += c(0, 0, 0, 0.25)
            }
            effect = DropShadow(5.0, Color.BLACK).apply { spread = 0.5 }
        }
    }
}