package com.github.iguanastin.view

import com.github.iguanastin.app.menagerie.GroupItem
import com.github.iguanastin.app.menagerie.Item
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.effect.DropShadow
import javafx.scene.layout.BorderPane
import org.controlsfx.control.GridView
import tornadofx.*

class GroupPreview : BorderPane() {

    val groupProperty: ObjectProperty<GroupItem?> = SimpleObjectProperty(null)
    var group: GroupItem?
        get() {
            return groupProperty.get()
        }
        set(value) {
            groupProperty.set(value)
        }

    private val grid: GridView<Item> = GridView<Item>()
    private val title: Label = Label()

    private val groupTitleListener: ChangeListener<String> = ChangeListener { _, _, newValue ->
        title.text = newValue
    }

    init {
        isMouseTransparent = true
        padding = insets(100)

        grid.cellFactory = ItemCellFactory.factory
        grid.apply {
            cellWidth = ItemCellFactory.SIZE
            cellHeight = ItemCellFactory.SIZE
            horizontalCellSpacing = 4.0
            verticalCellSpacing = 4.0
        }
        center = grid

        top = title
        title.apply {
            borderpaneConstraints { alignment = Pos.CENTER }
            isWrapText = true
            effect = DropShadow(5.0, c("black")).apply { spread = 0.5 }
            style {
                fontSize = 24.px
            }
        }

        groupProperty.addListener { _, oldValue, newValue ->
            oldValue?.titleProperty?.removeListener(groupTitleListener)
            newValue?.titleProperty?.addListener(groupTitleListener)

            runOnUIThread {
                title.text = newValue?.title
                grid.items.apply {
                    clear()
                    if (newValue != null) addAll(newValue.items)
                }
            }
        }
    }
}

fun EventTarget.grouppreview(op: GroupPreview.() -> Unit = {}) = GroupPreview().attachTo(this, op)