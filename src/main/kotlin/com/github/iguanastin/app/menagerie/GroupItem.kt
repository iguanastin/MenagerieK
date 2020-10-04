package com.github.iguanastin.app.menagerie

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import tornadofx.*

class GroupItem : Item {

    var title: String = ""
        set(value) {
            if (value != field) TODO("Unimplemented")
            field = value
        }

    private val _items: ObservableList<Item> = FXCollections.observableArrayList()
    val items: ObservableList<Item> = _items.asUnmodifiable()

    constructor(id: Int, added: Long, title: String = "") : super(id, added) {
        this.title = title
    }


    fun addItem(item: Item, index: Int? = null): Boolean {
        if (!_items.contains(item) && item !is GroupItem) {
            if (index == null) _items.add(item)
            else _items.add(index, item)
            return true
        } else {
            return false
        }
    }

    fun removeItem(item: Item): Boolean {
        return _items.remove(item)
    }

}