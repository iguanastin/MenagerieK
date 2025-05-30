package com.github.iguanastin.app

import com.github.iguanastin.view.factories.ItemCellFactory
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
        val mainItemGridView by cssclass()
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
        val focusableTagList by cssclass()
        val tempTag by cssclass()
        val videoControls by cssclass()
        val duplicatesTagList by cssclass()
        val itemGrid by cssclass()
        val importCellSource by cssclass()
        val importCellStatus by cssclass()
        val textDisplay by cssclass()
        val redText by cssclass()

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

        focusableTagList {
            tagListCell and focused {
                backgroundColor += c("#194070").darker()
            }
        }

        tagListCell and hover {
            backgroundColor += c("#194070")
            cursor = Cursor.HAND
        }

        mainItemGridView {
            minWidth = 525.px
        }

        itemGridCell {
            backgroundColor += c("#606467")
            backgroundRadius += box(3.px)
        }
        itemGridCell and selected {
            backgroundColor += c("#4e98a8")
        }
        itemGridCell and hover {
            cursor = Cursor.HAND
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
            effect = DropShadow(5.0, Color.BLACK).apply { spread = 0.5 }
            and(hover) {
                backgroundColor += c(0, 0, 0, 0.25)
            }
        }

        tempTag {
            backgroundColor += c("#330e17")
        }
        tempTag and odd {
            backgroundColor += c("#330e17").brighter()
        }

        videoControls {
            backgroundColor += c(0, 0, 0, 0.5)
            backgroundRadius += box(2.px)

            button {
                baseColor = c(0.25, 0.25, 0.25, 0.25)
            }
        }

        duplicatesTagList {
            maxWidth = 200.px
            minWidth = 200.px
            focusTraversable = false
            opacity = 0.75
        }

        itemGrid {
            cellWidth = ItemCellFactory.SIZE.px
            cellHeight = ItemCellFactory.SIZE.px
            horizontalCellSpacing = 4.px
            verticalCellSpacing = 4.px
        }

        importCellSource {
            textFill = c("grey")
        }
        importCellStatus {
            textFill = c("white")
        }

        textDisplay {
            padding = box(25.px)

            textArea {
                textFill = c("white")
                content {
                    backgroundColor = multi(c("#222426"))
                }
            }
        }

        redText {
            textFill = c("red")
        }
    }
}