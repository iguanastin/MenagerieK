package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.view.factories.ItemCellFactory
import com.github.iguanastin.view.runOnUIThread
import javafx.beans.InvalidationListener
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.effect.DropShadow
import org.controlsfx.control.GridView
import tornadofx.*

class GroupDisplay : ItemDisplay() {

    private val grid: GridView<Item> = GridView<Item>()
    private val title: Label = Label()

    private val groupItemsListener: InvalidationListener = InvalidationListener {
        runOnUIThread {
            grid.items.apply {
                clear()
                val group = item
                if (group is GroupItem) addAll(group.items)
            }
        }
    }

    init {
        isPickOnBounds = false
        padding = insets(100)

        grid.apply {
            addClass(Styles.itemGrid)
            cellFactory = ItemCellFactory.factory()
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
            textProperty().bind(itemProperty.flatMap { if (it is GroupItem) it.titleProperty else null })
        }

        itemProperty.addListener { _, oldValue, newValue ->
            if (oldValue is GroupItem) {
                oldValue.items.removeListener(groupItemsListener)
            }
            if (newValue is GroupItem) {
                newValue.items.addListener(groupItemsListener)
            }

            runOnUIThread {
                grid.items.apply {
                    clear()
                    if (newValue is GroupItem) addAll(newValue.items)
                }
            }
        }
    }

    override fun canDisplay(item: Item?): Boolean {
        return item is GroupItem
    }

}

fun EventTarget.groupdisplay(op: GroupDisplay.() -> Unit = {}) = GroupDisplay().attachTo(this, op)