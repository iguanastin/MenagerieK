package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.view.factories.ItemCellFactory
import com.github.iguanastin.view.runOnUIThread
import javafx.beans.InvalidationListener
import javafx.beans.value.ChangeListener
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.effect.DropShadow
import org.controlsfx.control.GridView
import tornadofx.*

class GroupDisplay : ItemDisplay() {

    private val grid: GridView<Item> = GridView<Item>()
    private val title: Label = Label()

    private val groupTitleListener: ChangeListener<String> = ChangeListener { _, _, newValue ->
        runOnUIThread { title.text = newValue }
    }
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

        grid.cellFactory = ItemCellFactory.factory()
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

        itemProperty.addListener { _, oldValue, newValue ->
            if (oldValue is GroupItem) {
                oldValue.titleProperty.removeListener(groupTitleListener)
                oldValue.items.removeListener(groupItemsListener)
            }
            if (newValue is GroupItem) {
                newValue.titleProperty.addListener(groupTitleListener)
                newValue.items.addListener(groupItemsListener)
            }

            runOnUIThread {
                title.text = if (newValue is GroupItem) newValue.title else null
                grid.items.apply {
                    clear()
                    if (newValue is GroupItem) addAll(newValue.items)
                }
            }
        }
    }

    override fun canDisplay(item: Item): Boolean {
        return item is GroupItem
    }

}