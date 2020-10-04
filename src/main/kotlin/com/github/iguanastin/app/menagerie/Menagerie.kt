package com.github.iguanastin.app.menagerie

import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import tornadofx.*

class Menagerie() {

    private val _tags: ObservableList<Tag> = FXCollections.observableArrayList()
    val tags: ObservableList<Tag> = _tags.asUnmodifiable()
    private val tagIdMap: MutableMap<Int, Tag> = mutableMapOf()

    private val _items: ObservableList<Item> = FXCollections.observableArrayList()
    val items: ObservableList<Item> = _items.asUnmodifiable()
    private val itemIdMap: MutableMap<Int, Item> = mutableMapOf()

    private val _knownNonDupes: ObservableList<Pair<Item, Item>> = FXCollections.observableArrayList()
    val knownNonDupes: ObservableList<Pair<Item, Item>> = _knownNonDupes.asUnmodifiable()


    init {
        items.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { itemIdMap.remove(it.id) }
                change.addedSubList.forEach { itemIdMap[it.id] = it }
            }
        })
        tags.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach { tagIdMap.remove(it.id) }
                change.addedSubList.forEach { tagIdMap[it.id] = it }
            }
        })
    }



    fun getItem(id: Int): Item? {
        return itemIdMap[id]
    }

    fun addItem(item: Item): Boolean {
        return if (item.id !in itemIdMap.keys) {
            _items.add(item)
            true
        } else {
            false
        }
    }

    fun removeItem(item: Item): Boolean { return _items.remove(item) }

    fun getTag(id: Int): Tag? {
        return tagIdMap[id]
    }

    fun getTag(name: String): Tag? {
        for (tag in tagIdMap.values) {
            if (tag.name == name) return tag
        }
        return null
    }

    fun addTag(tag: Tag): Boolean {
        for (t in _tags) {
            if (t.id == tag.id || t.name == tag.name) return false
        }

        _tags.add(tag)
        return true
    }

    fun removeTag(tag: Tag): Boolean { return _tags.remove(tag) }

    fun hasDupe(dupe: Pair<Item, Item>): Boolean {
        return dupe in _knownNonDupes || dupe.second to dupe.first in _knownNonDupes
    }

    fun addNonDupe(dupe: Pair<Item, Item>): Boolean {
        return if (!hasDupe(dupe)) {
            _knownNonDupes.add(dupe)
            true
        } else {
            false
        }
    }

    fun removeNonDupe(dupe: Pair<Item, Item>): Boolean { return _knownNonDupes.remove(dupe) || _knownNonDupes.remove(dupe.second to dupe.first) }

}