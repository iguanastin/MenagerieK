package com.github.iguanastin.app.menagerie.model

import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.image.Image
import tornadofx.*

class GroupItem(id: Int, added: Long, menagerie: Menagerie, title: String = "") : Item(id, added, menagerie) {

    val titleProperty = SimpleStringProperty(title)
    var title: String
        get() = titleProperty.get()
        set(value) = titleProperty.set(value)

    private val _items: ObservableList<FileItem> = FXCollections.observableArrayList()
    val items: ObservableList<FileItem> = _items.asUnmodifiable()

    init {
        titleProperty.addListener { _, old, new ->
            val change = GroupItemChange(this, title = Change(old, new))
            changeListeners.forEach { listener -> listener(change) }
        }

        _items.addListener(InvalidationListener {
            val change = GroupItemChange(this, items = _items)
            changeListeners.forEach { listener -> listener(change) }
        })
    }


    fun addItem(item: FileItem, index: Int? = null): Boolean {
        if (!_items.contains(item)) {
            if (item.elementOf != null) {
                item.elementOf?.removeItem(item)
            }
            item.elementOf = this

            if (index == null) _items.add(item)
            else _items.add(index, item)

            if (index == 0 || _items.size == 1) invalidateThumbnail()

            return true
        }

        return false
    }

    override fun loadThumbnail(): Image {
        if (items.isNotEmpty()) {
            return items.first().loadThumbnail()
        }

        return super.loadThumbnail()
    }

    override fun getThumbnailExtension(): String {
        return items.firstOrNull()?.getThumbnailExtension() ?: super.getThumbnailExtension()
    }

    fun moveItem(item: FileItem, index: Int) {
        if (index == 0 || _items.indexOf(item) == 0) invalidateThumbnail()
        _items.move(item, index)
    }

    fun removeItem(item: FileItem): Boolean {
        if (item in items) item.elementOf = null
        if (_items.indexOf(item) == 0) invalidateThumbnail()
        return _items.remove(item)
    }

    fun clearItems() {
        items.forEach { it.elementOf = null }
        _items.clear()
        invalidateThumbnail()
    }

    override fun invalidate() {
        clearItems()
        super.invalidate()
    }

    override fun replace(with: Item, replaceTags: Boolean): Boolean {
        return false
    }

}