package com.github.iguanastin.app.context

import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Tag

class TagEdit(items: List<Item>, add: List<Tag> = emptyList(), remove: List<Tag> = emptyList()) : Edit() {

    val items: List<Item> = ArrayList(items)
    val add: List<Tag> = ArrayList(add)
    val remove: List<Tag> = ArrayList(remove)

    private val addedHistory = mutableMapOf<Tag, MutableSet<Item>>()
    private val removedHistory = mutableMapOf<Tag, MutableSet<Item>>()


    override fun _perform(): Boolean {
        for (item in items) {
            for (tag in remove) {
                if (item.removeTag(tag)) removedHistory.computeIfAbsent(tag) { mutableSetOf() }.add(item)
            }
            for (tag in add) {
                if (item.addTag(tag)) addedHistory.computeIfAbsent(tag) { mutableSetOf() }.add(item)
            }
        }

        return true
    }

    override fun _undo(): Boolean {
        for (tag in addedHistory.keys) {
            addedHistory[tag]?.forEach { item ->
                item.removeTag(tag)
            }
        }
        for (tag in removedHistory.keys) {
            removedHistory[tag]?.forEach { item ->
                item.addTag(tag)
            }
        }

        return true
    }

    override fun toString(): String {
        return addedHistory.entries.joinToString("\n") { "'${it.key.name}' added to ${it.value.size} items" } + "\n\n" + removedHistory.entries.joinToString(
            "\n"
        ) { "'${it.key.name}' removed from ${it.value.size} items" }
    }

}