package com.github.iguanastin.app.menagerie.view

import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.ItemChangeBase
import com.github.iguanastin.app.menagerie.model.Menagerie
import com.github.iguanastin.view.runOnUIThread
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import tornadofx.*
import kotlin.collections.sortBy
import kotlin.random.Random

class MenagerieView(val menagerie: Menagerie, val searchString: String = "", val descending: Boolean = true, val shuffle: Boolean = false, val filters: Iterable<ViewFilter>, val sortBy: (Item) -> Int? = { it.id }) {

    var items: ObservableList<Item>? = null
        private set

    private val shuffleRand = Random
    private val shuffleMap: MutableMap<Item, Int> = mutableMapOf()

    private val itemChangeListener: (ItemChangeBase) -> Unit = { change ->
        val items = items
        if (items != null) {
            if (accepts(change.item)) {
                if (change.item !in items) runOnUIThread {
                    items.add(change.item)
                    if (shuffle) shuffleMap.computeIfAbsent(change.item) { shuffleRand.nextInt() }
                    sortItems()
                }
            } else {
                runOnUIThread { items.remove(change.item) }
            }
        }
    }
    private val menagerieItemsListener: ListChangeListener<Item> = ListChangeListener { change ->
        if (items == null) return@ListChangeListener
        runOnUIThread {
            while (change.next()) {
                items?.removeAll(change.removed)
                change.removed.forEach { item -> item.changeListeners.remove(itemChangeListener) }

                val temp = mutableListOf<Item>()
                change.addedSubList.forEach { item ->
                    item.changeListeners.add(itemChangeListener)

                    if (accepts(item)) temp.add(item)
                }
                items?.addAll(temp)
                sortItems()
            }
        }
    }


    fun attachTo(list: ObservableList<Item>) {
        close()

        menagerie.items.addListener(menagerieItemsListener)
        val temp = mutableListOf<Item>()
        menagerie.items.forEach {
            it.changeListeners.add(itemChangeListener)

            if (accepts(it)) temp.add(it)
            if (shuffle) shuffleMap.computeIfAbsent(it) { shuffleRand.nextInt() }
        }

        items = list.apply {
            clear()
            addAll(temp)
        }
        sortItems()
    }

    private fun sortItems() {
        if (shuffle) {
            items?.sortBy { shuffleMap[it] }
        } else {
            if (descending) {
                items?.sortByDescending(sortBy)
            } else {
                items?.sortBy(sortBy)
            }
        }
    }

    fun accepts(item: Item): Boolean {
        for (filter in filters) {
            if (!filter.accepts(item)) {
                return false
            }
        }

        return true
    }

    fun close() {
        menagerie.items.removeListener(menagerieItemsListener)
        menagerie.items.forEach { it.changeListeners.remove(itemChangeListener) }
        items = null
    }

}