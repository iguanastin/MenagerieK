package com.github.iguanastin.app

import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Item


fun <T> Pair<T, T>.contains(item: T): Boolean {
    return first == item || second == item
}

fun expandGroups(items: List<Item>, includeGroupItems: Boolean = false): List<Item> {
    val result = mutableListOf<Item>()

    for (item in items) {
        if (item is GroupItem) {
            result.addAll(item.items)
            if (includeGroupItems) result.add(item)
        } else {
            result.add(item)
        }
    }

    return result
}