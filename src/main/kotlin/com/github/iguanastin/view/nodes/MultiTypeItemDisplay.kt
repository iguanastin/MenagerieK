package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.view.runOnUIThread
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventTarget
import javafx.scene.layout.BorderPane
import mu.KotlinLogging
import tornadofx.*

private val log = KotlinLogging.logger {}

class MultiTypeItemDisplay : BorderPane() {

    val displays: MutableList<ItemDisplay> = mutableListOf(ImageDisplay(), GroupDisplay())


    val itemProperty: ObjectProperty<Item?> = SimpleObjectProperty(null)
    var item: Item?
        get() {
            return itemProperty.get()
        }
        set(value) {
            itemProperty.set(value)
        }


    init {
        itemProperty.addListener(ChangeListener { _, _, newValue ->
            runOnUIThread {
                display(newValue)
            }
        })
    }


    private fun display(item: Item?) {
        // Reset displays
        displays.forEach { it.item = null }
        center = null

        if (item != null) {
            for (display in displays) {
                if (display.canDisplay(item)) {
                    center = display
                    display.item = item
                    break
                }
            }
        }
    }

}

fun EventTarget.multitypeitemdisplay(op: MultiTypeItemDisplay.() -> Unit = {}) = MultiTypeItemDisplay().attachTo(this, op)