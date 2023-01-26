package com.github.iguanastin.app.utils

import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Tag
import mu.KotlinLogging
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

private val log = KotlinLogging.logger {}

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
    return if (bytes > 1024 * 1024 * 1024) String.format("%.2fGB", bytes / 1024.0 / 1024 / 1024) else if (bytes > 1024 * 1024) String.format("%.2fMB", bytes / 1024.0 / 1024) else if (bytes > 1024) String.format("%.2fKB", bytes / 1024.0) else String.format("%dB", bytes)
}

fun Item.copyTagsToClipboard() {
    val tagString = tags.joinToString(separator = ",") { tag -> tag.name }
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(tagString), null)
    log.info("Copied tags to clipboard: $tagString")
}

fun Item.pasteTagsFromClipboard() {
    val tagString: String = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String
    val added = mutableListOf<Tag>()
    for (t in tagString.split(",")) {
        if (t.isEmpty()) continue
        val tag = this.menagerie.getTag(t) ?: Tag(menagerie.reserveTagID(), t).also { menagerie.addTag(it) }
        addTag(tag)
        added.add(tag)
    }
    log.info("Pasted tags from clipboard: ${added.joinToString { it.name }}")
}