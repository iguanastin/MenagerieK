package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.model.Menagerie
import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.view.factories.TagCellFactory
import com.github.iguanastin.view.runOnUIThread
import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Priority
import mu.KotlinLogging
import tornadofx.*
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

class TagSearchDialog(val menagerie: Menagerie, onClose: () -> Unit = {}, onClick: (Tag) -> Unit = {}) :
    StackDialog(onClose) {

    private lateinit var tagList: ListView<Tag>
    private lateinit var searchTextField: TextField

    private enum class OrderBy {
        Name,
        Frequency,
        Color,
        ID
    }

    private var search: String = ""
    private var order: OrderBy = OrderBy.Name
    private var descending: Boolean = false

    private val tagListener = ListChangeListener<Tag> { change ->
        while (change.next()) {
            filterTags()
        }
    }

    init {
        root.graphic = borderpane {
            prefWidth = 500.0
            padding = insets(5.0)
            prefHeightProperty().bind(this@TagSearchDialog.heightProperty().multiply(0.8))

            top {
                vbox {
                    spacing = 5.0
                    alignment = Pos.CENTER
                    hgrow = Priority.ALWAYS

                    label("Search Tags")
                    searchTextField = textfield {
                        hgrow = Priority.ALWAYS
                        promptText = "Any"

                        text = search
                        textProperty().addListener { _, _, newValue ->
                            search = newValue.lowercase()
                            filterTags()
                        }
                    }
                    hbox {
                        spacing = 5.0

                        alignment = Pos.CENTER
                        label("Order by: ")
                        choicebox<OrderBy> {
                            items.addAll(OrderBy.Name, OrderBy.Frequency, OrderBy.Color, OrderBy.ID)
                            value = order
                            valueProperty().addListener { _, _, newValue ->
                                order = newValue
                                filterTags()
                            }
                        }
                        checkbox("Descending") {
                            isSelected = descending
                            selectedProperty().addListener { _, _, newValue ->
                                descending = newValue
                                filterTags()
                            }
                        }
                    }
                }
            }
            center {
                tagList = listview {
                    addClass(Styles.focusableTagList)
                    vgrow = Priority.ALWAYS
                    cellFactory = TagCellFactory().apply {
                        onTagClick = onClick
                    }
                    addEventFilter(KeyEvent.KEY_PRESSED) { event ->
                        if (event.code == KeyCode.ESCAPE) {
                            event.consume()
                            close()
                        }
                    }
                }
            }
            bottom {
                button("Purge") {
                    borderpaneConstraints { alignment = Pos.CENTER }
                    tooltip("Purge temporary and unused tags")
                    onAction = EventHandler { event ->
                        event.consume()
                        val progress = ProgressDialog(
                            "Purging tags",
                            "Purging unused and temporary tags"
                        ).also { this@TagSearchDialog.parent.add(it) }

                        thread(start = true, isDaemon = true, name = "Tag purger thread") {
                            log.info("Purging unused/temporary tags")
                            val toDelete = mutableListOf<Tag>()
                            menagerie.tags.forEach { tag ->
                                if (tag.temporary || tag.frequency == 0) toDelete.add(tag)
                            }

                            var t = System.currentTimeMillis()
                            val size = toDelete.size
                            toDelete.forEachIndexed { i, tag ->
                                menagerie.items.forEach { item -> item.removeTag(tag) }
                                menagerie.removeTag(tag)

                                if (System.currentTimeMillis() - t >= 100) {
                                    runOnUIThread { progress.progress = i.toDouble() / size.toDouble() }
                                    t = System.currentTimeMillis()
                                }
                            }

                            log.info("Purged ${toDelete.size} unused/temporary tags")
                            log.debug { "Purged unused/temporary tags" + toDelete.joinToString(", ") }

                            runOnUIThread {
                                progress.close()

                                this@TagSearchDialog.parent.add(
                                    InfoStackDialog(
                                        "Purged tags",
                                        "Purged ${toDelete.size} tags"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        menagerie.tags.addListener(tagListener)

        runOnUIThread { filterTags() }
    }

    private fun filterTags() {
        val filtered = mutableListOf<Tag>()

        menagerie.tags.forEach {
            if (search.isBlank() || it.name.contains(search)) filtered.add(it)
        }

        if (descending) {
            when (order) {
                OrderBy.Name -> filtered.sortByDescending { it.name }
                OrderBy.Frequency -> filtered.sortByDescending { it.frequency }
                OrderBy.Color -> filtered.sortByDescending { it.color }
                OrderBy.ID -> filtered.sortByDescending { it.id }
            }
        } else {
            when (order) {
                OrderBy.Name -> filtered.sortBy { it.name }
                OrderBy.Frequency -> filtered.sortBy { it.frequency }
                OrderBy.Color -> filtered.sortBy { it.color }
                OrderBy.ID -> filtered.sortBy { it.id }
            }
        }

        runOnUIThread {
            tagList.items.apply {
                clear()
                addAll(filtered)
            }
        }
    }

    override fun requestFocus() {
        searchTextField.requestFocus()
    }

    override fun close() {
        menagerie.tags.removeListener(tagListener)
        super.close()
    }

}