package com.github.iguanastin.app.context

import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Tag

class TagEdit(items: List<Item>, add: List<Tag>, remove: List<Tag>) {

    val items: List<Item> = ArrayList(items)
    val add: List<Tag> = ArrayList(add)
    val remove: List<Tag> = ArrayList(remove)

    enum class State {
        NotApplied,
        Applied,
        Undone
    }

    var state = State.NotApplied
        private set

    private val _addedHistory = mutableMapOf<Tag, MutableSet<Item>>()
    val addedHistory: Map<Tag, Set<Item>> = _addedHistory
    private val _removedHistory = mutableMapOf<Tag, MutableSet<Item>>()
    val removedHistory: Map<Tag, Set<Item>> = _removedHistory


    fun applyEdit(): Boolean {
        if (state != State.NotApplied) return false

        for (item in items) {
            for (tag in remove) {
                if (item.removeTag(tag)) _removedHistory.computeIfAbsent(tag) { mutableSetOf() }.add(item)
            }
            for (tag in add) {
                if (item.addTag(tag)) _addedHistory.computeIfAbsent(tag) { mutableSetOf() }.add(item)
            }
        }

        state = State.Applied
        return true
    }

    fun undoEdit(): Boolean {
        if (state != State.Applied) return false

        for (tag in _addedHistory.keys) {
            _addedHistory[tag]?.forEach { item ->
                item.removeTag(tag)
            }
        }
        for (tag in _removedHistory.keys) {
            _removedHistory[tag]?.forEach { item ->
                item.addTag(tag)
            }
        }

        state = State.Undone
        return true
    }

}