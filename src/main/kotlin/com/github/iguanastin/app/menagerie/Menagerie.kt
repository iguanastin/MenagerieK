package com.github.iguanastin.app.menagerie

import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList

class Menagerie() {

    val tags: ObservableList<Tag> = FXCollections.observableArrayList()
    val items: ObservableList<Item> = FXCollections.observableArrayList()
    val knownNonDupes: ObservableList<Pair<Item, Item>> = FXCollections.observableArrayList()

    private val itemIdMap: MutableMap<Int, Item> = mutableMapOf()
    private val tagIdMap: MutableMap<Int, Tag> = mutableMapOf()


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

    constructor(tags: List<Tag>? = null, items: List<Item>? = null, nonDupes: List<Pair<Item, Item>>? = null): this() {
        if (tags != null) this.tags.addAll(tags)
        if (items != null) this.items.addAll(items)
        if (nonDupes != null) this.knownNonDupes.addAll(nonDupes)
    }


    fun getItem(id: Int): Item? {
        return itemIdMap[id]
    }

    fun getTag(id: Int): Tag? {
        return tagIdMap[id]
    }

}