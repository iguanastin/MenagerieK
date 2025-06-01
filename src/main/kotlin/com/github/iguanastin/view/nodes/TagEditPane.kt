package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.context.MenagerieContext
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.view.bindShortcut
import com.github.iguanastin.view.onActionConsuming
import javafx.event.EventTarget
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import tornadofx.*

class TagEditPane(var selectedItems: List<Item>? = null, var context: MenagerieContext? = null): BorderPane() {

    lateinit var applyTagEdit: Button
    lateinit var editTags: TextField

    init {
        isPickOnBounds = false
        padding = insets(50)

        bottom {
            hbox(10) {
                addClass(Styles.dialogPane)
                isPickOnBounds = false
                padding = insets(10)
                editTags = textfield {
                    hboxConstraints {
                        hGrow = Priority.ALWAYS
                    }
                    promptText = "Edit tags..."
                }
                applyTagEdit = button("Ok") {
                    onActionConsuming { applyEdit() }
                }
            }
        }

        editTags.bindShortcut(KeyCode.ENTER) { applyTagEdit.fire() }
        editTags.bindShortcut(KeyCode.ENTER, ctrl = true) { applyTagEdit.fire() }

        initEditTagsAutoComplete()
    }

    private fun applyEdit() {
        val items = selectedItems
        if (items?.isEmpty() ?: return) return
        val context = context ?: return

        val tagsToAdd = mutableListOf<Tag>()
        val tagsToRemove = mutableListOf<Tag>()

        for (name in editTags.text.trim().split(Regex("\\s+"))) {
            if (name.isBlank()) continue // Ignore empty and blank additions

            if (name.startsWith('-')) {
                if (name.length == 1) continue // Ignore a '-' by itself
                val tag: Tag = context.menagerie.getTag(name.substring(1))
                    ?: continue // Ignore tags that don't exist
                tagsToRemove.add(tag)
            } else {
                tagsToAdd.add(context.menagerie.getOrMakeTag(name))
            }
        }

        if (tagsToAdd.isEmpty() && tagsToRemove.isEmpty()) return

        // Apply tag edits
        context.tagEdit(items, tagsToAdd, tagsToRemove)

        hide()
    }

    private fun initEditTagsAutoComplete() {
        editTags.bindAutoComplete { predict ->
            var word = predict.lowercase()
            val exclude = word.startsWith('-')
            if (exclude) word = word.substring(1)
            val maxResults = 8

            val result: List<Tag> = if (exclude) {
                selectedItems?.flatMap { item -> item.tags }?.toSet() ?: emptySet()
            } else {
                context?.menagerie?.tags ?: emptySet()
            }.filter { tag -> tag.name.startsWith(word) }.sortedByDescending { it.frequency }

            return@bindAutoComplete result.subList(0, maxResults.coerceAtMost(result.size))
                .map { if (exclude) "-${it.name}" else it.name }
        }
    }

    override fun requestFocus() {
        editTags.requestFocus()
    }

}

fun EventTarget.tageditpane(op: TagEditPane.() -> Unit): TagEditPane = TagEditPane().attachTo(this, op)