package com.github.iguanastin.app.menagerie.view

import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.ItemChangeBase
import com.github.iguanastin.app.menagerie.model.Menagerie
import com.github.iguanastin.view.runOnUIThread
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import tornadofx.*
import kotlin.collections.sortBy

class MenagerieView(val menagerie: Menagerie, val descending: Boolean = true, vararg val filters: ViewFilter) {

    var items: ObservableList<Item>? = null
        private set

    private val itemChangeListener: (ItemChangeBase) -> Unit = { change ->
        val temp = mutableListOf<Item>()
        if (items != null && !accepts(change.item)) {
            temp.add(change.item)
        }
        runOnUIThread { items?.removeAll(temp) }
    }
    private val menagerieItemsListener: ListChangeListener<Item> = ListChangeListener { change ->
        if (items == null) return@ListChangeListener
        runOnUIThread {
            while (change.next()) {
                items?.removeAll(change.removed)

                val temp = mutableListOf<Item>()
                change.addedSubList.forEach { item ->
                    if (accepts(item)) temp.add(item)
                }
                items?.addAll(temp)
                sortItems()
            }
        }
    }
    private val itemsListener: ListChangeListener<Item> = ListChangeListener { change ->
        while (change.next()) {
            change.removed.forEach { item -> item.changeListeners.remove(itemChangeListener) }
            change.addedSubList.forEach { item -> item.changeListeners.add(itemChangeListener) }
        }
    }


    fun attachTo(list: ObservableList<Item>?) {
        close()

        items = list?.apply {
            clear()
            addListener(itemsListener)
        }
        menagerie.items.addListener(menagerieItemsListener)
        val temp = mutableListOf<Item>()
        menagerie.items.forEach {
            it.changeListeners.add(itemChangeListener)
            if (accepts(it)) temp.add(it)
        }
        items?.addAll(temp)
        sortItems()
    }

    private fun sortItems() {
        if (descending) {
            items?.sortByDescending { it.id }
        } else {
            items?.sortBy { it.id }
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
        items?.removeListener(itemsListener)
        items = null
    }

}