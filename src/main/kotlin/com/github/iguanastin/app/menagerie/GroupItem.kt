package com.github.iguanastin.app.menagerie

import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import tornadofx.*

class GroupItem(id: Int, added: Long, menagerie: Menagerie, title: String = "") : Item(id, added, menagerie) {

    val titleProperty = SimpleStringProperty(title)
    var title: String
        get() = titleProperty.get()
        set(value) = titleProperty.set(value)

    private val _items: ObservableList<FileItem> = FXCollections.observableArrayList()
    val items: ObservableList<FileItem> = _items.asUnmodifiable()


    fun addItem(item: FileItem, index: Int? = null): Boolean {
        if (!_items.contains(item)) {
            if (index == null) _items.add(item)
            else _items.add(index, item)
            return true
        } else {
            return false
        }
    }

    fun moveItem(item: FileItem, index: Int) {
        _items.move(item, index)
    }

    fun removeItem(item: Item): Boolean {
        return _items.remove(item)
    }

}