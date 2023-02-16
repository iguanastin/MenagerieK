package com.github.iguanastin.app.utils

import com.github.iguanastin.app.context.MenagerieContext
import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Tag
import javafx.beans.property.BooleanProperty
import mu.KotlinLogging
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

private val log = KotlinLogging.logger {}


fun <E> MutableList<E>.addSorted(e: E, sortWith: (E, E) -> Int) {
    var put = 0
    forEachIndexed { index, element ->
        if (sortWith(e, element) > 0) {
            put = index + 1
            return@forEachIndexed
        }
    }
    add(put, e)
}

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

fun bytesToPrettyString(bytes: Long): String {
    return if (bytes > 1024 * 1024 * 1024) String.format(
        "%.2fGB",
        bytes / 1024.0 / 1024 / 1024
    ) else if (bytes > 1024 * 1024) String.format("%.2fMB", bytes / 1024.0 / 1024) else if (bytes > 1024) String.format(
        "%.2fKB",
        bytes / 1024.0
    ) else String.format("%dB", bytes)
}

fun Item.copyTagsToClipboard() {
    val tagString = tags.joinToString(separator = ",") { tag -> tag.name }
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(tagString), null)
    log.info("Copied tags to clipboard: $tagString")
}

fun Item.pasteTagsFromClipboard(context: MenagerieContext?) {
    val tagString: String = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String
    val add = mutableListOf<Tag>()
    for (t in tagString.split(",")) {
        if (t.isEmpty()) continue
        val tag = this.menagerie.getOrMakeTag(t)
        add.add(tag)
    }
    context?.tagEdit(listOf(this), add)
    log.info("Pasted tags from clipboard: ${add.joinToString { it.name }}")
}

fun BooleanProperty.toggle() {
    value = !value
}