package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.menagerie.model.Item
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.layout.BorderPane

abstract class ItemDisplay: BorderPane() {

    val itemProperty: ObjectProperty<Item?> = SimpleObjectProperty(null)
    var item: Item?
        get() {
            return itemProperty.get()
        }
        set(value) {
            itemProperty.set(value)
        }

    abstract fun canDisplay(item: Item?): Boolean

}