package com.github.iguanastin.view

import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.view.nodes.TagColorPicker
import javafx.beans.value.ChangeListener
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.util.Callback
import tornadofx.*

object TagCellFactory {

    val factory = Callback<ListView<Tag>, ListCell<Tag>> {
        object : ListCell<Tag>() {

            private lateinit var nameLabel: Label
            private lateinit var freqLabel: Label

            val colorListener: ChangeListener<String> = ChangeListener { _, _, newValue ->
                runOnUIThread {
                    val color = c(newValue ?: "white")
                    nameLabel.textFill = color
                    freqLabel.textFill = color
                }
            }
            val freqListener: ChangeListener<Number> = tornadofx.ChangeListener { _, _, newValue ->
                runOnUIThread {
                    freqLabel.text = "$newValue"
                }
            }

            init {
                graphic = borderpane {
                    left {
                        nameLabel = label()
                    }
                    right {
                        freqLabel = label()
                    }
                }

                contextmenu {
                    item("", graphic = TagColorPicker { color ->
                        hide()
                        item.color = color
                    })
                }
            }

            override fun updateItem(item: Tag?, empty: Boolean) {
                getItem()?.colorProperty?.removeListener(colorListener)
                getItem()?.frequencyProperty?.removeListener(freqListener)

                super.updateItem(item, empty)

                item?.colorProperty?.addListener(colorListener)
                item?.frequencyProperty?.addListener(freqListener)

                val color = c(item?.color ?: "white")
                nameLabel.apply {
                    text = item?.name
                    textFill = color
                }
                freqLabel.apply {
                    text = if (item != null) {
                        "${item.frequency}"
                    } else {
                        null
                    }
                    textFill = color
                }
            }

        }
    }

}