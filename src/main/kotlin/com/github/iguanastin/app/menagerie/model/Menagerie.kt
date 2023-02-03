package com.github.iguanastin.app.menagerie.model

import javafx.collections.*
import tornadofx.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class Menagerie {

    private val _tags: ObservableSet<Tag> = FXCollections.observableSet()
    val tags: ObservableSet<Tag> = _tags.asUnmodifiable()
    private val tagIdMap: MutableMap<Int, Tag> = mutableMapOf()
    private val nextTagID = AtomicInteger(0)

    private val _items: ObservableList<Item> = FXCollections.observableArrayList()
    val items: ObservableList<Item> = _items.asUnmodifiable()
    private val itemIdMap: MutableMap<Int, Item> = mutableMapOf()
    private val nextItemID = AtomicInteger(0)
    private val files: MutableSet<File> = mutableSetOf()

    private val _knownNonDupes: ObservableSet<SimilarPair<Item>> = FXCollections.observableSet()
    val knownNonDupes: ObservableSet<SimilarPair<Item>> = _knownNonDupes.asUnmodifiable()


    init {
        items.addListener(ListChangeListener { change ->
            while (change.next()) {
                change.removed.forEach {
                    itemIdMap.remove(it.id)
                    if (it is FileItem) {
                        files.remove(it.file)
                        it.elementOf?.removeItem(it)
                    }
                    it.invalidate()
                }
                change.addedSubList.forEach {
                    itemIdMap[it.id] = it
                    if (it is FileItem) files.add(it.file)
                }
            }
        })
        tags.addListener(SetChangeListener { change ->
            if (change.wasRemoved()) tagIdMap.remove(change.elementRemoved.id)
            if (change.wasAdded()) tagIdMap[change.elementAdded.id] = change.elementAdded
        })
    }


    fun reserveItemID(): Int {
        return nextItemID.getAndIncrement()
    }

    fun reserveTagID(): Int {
        return nextTagID.getAndIncrement()
    }

    fun getItem(id: Int): Item? {
        return itemIdMap[id]
    }

    fun hasItem(id: Int): Boolean {
        return itemIdMap.containsKey(id)
    }

    fun hasFile(file: File): Boolean {
        return file in files
    }

    fun addItem(item: Item): Boolean {
        return if (item.id !in itemIdMap.keys) {
            if (item is FileItem && item.file in files) {
                false
            } else {
                _items.add(item)
                nextItemID.getAndUpdate { it.coerceAtLeast(item.id + 1) }
                true
            }
        } else {
            false
        }
    }

    fun removeItem(item: Item): Boolean {
        return _items.remove(item)
    }

    fun getTag(id: Int): Tag? {
        return tagIdMap[id]
    }

    fun getTag(name: String): Tag? {
        val lName = name.lowercase()
        for (tag in tagIdMap.values) {
            if (tag.name == lName) return tag
        }
        return null
    }

    fun getOrMakeTag(name: String, temporaryIfNew: Boolean = false): Tag {
        return getTag(name) ?: Tag(reserveTagID(), name, this, temporary = temporaryIfNew).also { addTag(it) }
    }

    fun hasTag(id: Int): Boolean {
        return tagIdMap.containsKey(id)
    }

    fun addTag(tag: Tag): Boolean {
        if (_tags.add(tag)) nextTagID.getAndUpdate { it.coerceAtLeast(tag.id + 1) }
        return true
    }

    fun removeTag(tag: Tag): Boolean {
        // TODO invalidate tag?
        return _tags.remove(tag)
    }

    fun hasNonDupe(dupe: SimilarPair<Item>): Boolean {
        return dupe in _knownNonDupes
    }

    fun addNonDupe(dupe: SimilarPair<Item>): Boolean {
        return if (!hasNonDupe(dupe)) {
            _knownNonDupes.add(dupe)
            true
        } else {
            false
        }
    }

    fun removeNonDupe(dupe: SimilarPair<Item>): Boolean {
        return _knownNonDupes.remove(dupe)
    }

}