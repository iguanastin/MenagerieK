package com.github.iguanastin.app.menagerie.search

import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.ItemChangeBase
import com.github.iguanastin.app.menagerie.model.Menagerie
import com.github.iguanastin.app.menagerie.search.filters.SearchFilter
import com.github.iguanastin.app.utils.addSorted
import com.github.iguanastin.app.utils.clearAndAddAll
import com.github.iguanastin.view.runOnUIThread
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import kotlin.random.Random

class MenagerieSearch(val menagerie: Menagerie, val searchString: String = "", val descending: Boolean = true, val shuffle: Boolean = false, val filters: Iterable<SearchFilter>, val sortBy: (Item) -> Int = { it.id }) {

    var items: ObservableList<Item>? = null
        private set

    private val shuffleRand = Random
    private val shuffleMap: MutableMap<Item, Int> = mutableMapOf()

    private val sortWith = { i1: Item, i2: Item ->
        if (shuffle) shuffleMap[i1]!!.compareTo(shuffleMap[i2]!!)
        else if (descending) sortBy(i2).compareTo(sortBy(i1))
        else sortBy(i1).compareTo(sortBy(i2))
    }

    private val itemChangeListener: (ItemChangeBase) -> Unit = { change ->
        val items = items
        if (items != null) {
            if (accepts(change.item)) {
                if (change.item !in items) runOnUIThread {
                    if (shuffle) shuffleMap.computeIfAbsent(change.item) { shuffleRand.nextInt() }
                    items.addSorted(change.item, sortWith)
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
                items?.removeAll(change.removed.toSet())
                change.removed.forEach { item -> item.changeListeners.remove(itemChangeListener) }

                val temp = mutableListOf<Item>()
                change.addedSubList.forEach { item ->
                    item.changeListeners.add(itemChangeListener)

                    if (accepts(item)) temp.add(item)
                }
                temp.forEach { items?.addSorted(it, sortWith) }
            }
        }
    }

    fun bindTo(list: ObservableList<Item>) {
        close()

        menagerie.items.addListener(menagerieItemsListener)
        val temp = mutableListOf<Item>()
        menagerie.items.forEach {
            it.changeListeners.add(itemChangeListener)

            if (accepts(it)) temp.add(it)
            if (shuffle) shuffleMap.computeIfAbsent(it) { shuffleRand.nextInt() }
        }

        temp.sortWith(Comparator(sortWith))
        items = list.apply { clearAndAddAll(temp) }
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