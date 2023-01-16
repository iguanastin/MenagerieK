package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.view.factories.TagCellFactory
import com.github.iguanastin.view.runOnUIThread
import javafx.collections.ObservableList
import javafx.geometry.Pos
import javafx.scene.control.ListView
import javafx.scene.layout.Priority
import tornadofx.*

class TagSearchDialog(val tags: ObservableList<Tag>, onClose: () -> Unit = {}) : StackDialog(onClose) {

    private lateinit var tagList: ListView<Tag>

    private enum class OrderBy {
        Name,
        Frequency,
        Color,
        ID
    }

    private var search: String = ""
    private var order: OrderBy = OrderBy.Name
    private var descending: Boolean = false

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
                    textfield {
                        hgrow = Priority.ALWAYS
                        promptText = "Any"

                        text = search
                        textProperty().addListener { _, _, newValue ->
                            search = newValue.toLowerCase()
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
                    cellFactory = TagCellFactory.factory
                    vgrow = Priority.ALWAYS
                }
            }
        }

        runOnUIThread { filterTags() }
    }

    private fun filterTags() {
        val filtered = mutableListOf<Tag>()

        tags.forEach {
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

}