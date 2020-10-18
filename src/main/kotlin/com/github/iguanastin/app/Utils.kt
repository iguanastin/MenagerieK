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

fun bytesToPrettyString(bytes: Long): String? {
    return if (bytes > 1024 * 1024 * 1024) String.format("%.2fGB", bytes / 1024.0 / 1024 / 1024) else if (bytes > 1024 * 1024) String.format("%.2fMB", bytes / 1024.0 / 1024) else if (bytes > 1024) String.format("%.2fKB", bytes / 1024.0) else String.format("%dB", bytes)
}