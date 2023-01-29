package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.Styles
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.stage.Popup
import javafx.stage.PopupWindow
import javafx.stage.Screen
import tornadofx.*

fun TextField.bindAutoComplete(provider: (String) -> List<String>) {

    val selectedReplacement = SimpleIntegerProperty(-1)
    val popupGap = 10
    var above = false
    val textMeasurer: Text = Text().also { Scene(Group(it)) }
    val vbox: VBox
    val popup = Popup().apply {
        addStylesheet(Styles::class)
        anchorLocation = PopupWindow.AnchorLocation.CONTENT_TOP_LEFT
        isAutoFix = false
        vbox = vbox(5.0) {
            padding = insets(5.0)
            addClass(Styles.dialogPane)
        }
        content.add(vbox)
        scene.window.focusedProperty().addListener { _, _, focused ->
            if (!focused) this.hide()
        }
    }

    selectedReplacement.addListener { _, _, newValue ->
        updateSelected(vbox, newValue.toInt(), above)
    }

    addEventFilter(KeyEvent.KEY_PRESSED) { event ->
        if (!popup.isShowing) return@addEventFilter

        val sel = selectedReplacement.get()

        if ((event.isShortcutDown || sel >= 0) && !event.isShiftDown && !event.isAltDown) {
            if (event.code == KeyCode.ENTER || event.code == KeyCode.SPACE) {
                if (event.code == KeyCode.SPACE) event.consume() // Allow enter to filter down

                val toReplace = getPreviousWord(text, caretPosition)
                val word = if (sel < 0) {
                    if (above) (vbox.children.last() as Label).text else (vbox.children.first() as Label).text
                } else {
                    (vbox.children[sel] as Label).text
                }
                val toReplaceStart = caretPosition - toReplace.length
                text = text.substring(0, toReplaceStart) + word + " " + text.substring(caretPosition)
                positionCaret(toReplaceStart + word.length + 1)
            }
        }

        if (event.code == KeyCode.UP) {
            event.consume()
            if (sel < 0) {
                selectedReplacement.set(vbox.children.size - 1)
            } else {
                if (sel == 0) {
                    selectedReplacement.set(vbox.children.size - 1)
                } else {
                    selectedReplacement.set(sel - 1)
                }
            }
        } else if (event.code == KeyCode.DOWN) {
            event.consume()
            if (sel < 0) {
                selectedReplacement.set(0)
            } else {
                if (sel == vbox.children.size - 1) {
                    selectedReplacement.set(0)
                } else {
                    selectedReplacement.set(sel + 1)
                }
            }
        }
    }

    caretPositionProperty().addListener { _, _, index ->
        selectedReplacement.set(-1)
        popup.hide()

        if (selection.length == 0) {
            val text = text
            val word = getPreviousWord(text, index.toInt())

            if (word.isNotBlank()) {
                val predictions = provider(word)
                if (predictions.isNotEmpty()) {
                    vbox.children.clear()
                    for (i in 0 until 8.coerceAtMost(predictions.size)) {
                        if (above) {
                            vbox.children.add(0, Label(predictions[i]))
                        } else {
                            vbox.children.add(Label(predictions[i]))
                        }
                    }
                    vbox.applyCss()
                    vbox.layout()
                    updateSelected(vbox, selectedReplacement.get(), above)

                    val bounds = (Screen.getScreensForRectangle(layoutX, layoutY, width, height).getOrNull(0)
                            ?: Screen.getPrimary()).visualBounds
                    val pos = localToScreen(layoutBounds.minX, layoutBounds.minY)

                    val x = pos.x + textMeasurer.apply {
                        this.text = this@bindAutoComplete.text.substring(0, index.toInt() - word.length)
                        this.applyCss()
                    }.layoutBounds.width

                    if (above) {
                        popup.show(this, x, pos.y - popupGap)
                        if (popup.y < bounds.minY) {
                            popup.hide()
                            above = false
                            popup.anchorLocation = PopupWindow.AnchorLocation.CONTENT_TOP_LEFT
                            vbox.children.reverse()
                            popup.show(this, x, pos.y + height + popupGap)
                        }
                    } else {
                        popup.show(this, x, pos.y + height + popupGap)
                        if (popup.y + popup.height > bounds.maxY) {
                            popup.hide()
                            above = true
                            popup.anchorLocation = PopupWindow.AnchorLocation.CONTENT_BOTTOM_LEFT
                            vbox.children.reverse()
                            popup.show(this, x, pos.y - popupGap)
                        }
                    }
                }
            }
        }
    }
}

private fun getPreviousWord(text: String, caret: Int): String {
    var word = text.substring(0, caret)
    for (i in caret - 1 downTo 0) {
        if (word[i].isWhitespace()) {
            word = word.substring(i + 1)
            break
        }
    }
    return word
}

private fun updateSelected(vbox: VBox, selected: Int, above: Boolean) {
    vbox.children.forEachIndexed { i, node ->
        node.removeClass(Styles.selected, Styles.autocompleteSoftSelected)
        if (selected < 0) {
            if ((above && i == vbox.children.size - 1) || (!above && i == 0)) {
                node.addClass(Styles.autocompleteSoftSelected)
            }
        } else if (i == selected) {
            node.addClass(Styles.selected)
        }
    }
}