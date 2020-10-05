package com.github.iguanastin.view

import com.github.iguanastin.app.menagerie.Tag
import javafx.beans.value.ChangeListener
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.paint.Color
import javafx.util.Callback
import tornadofx.*

object TagCellFactory {

    val factory = Callback<ListView<Tag>, ListCell<Tag>> {
        object : ListCell<Tag>() {

            private lateinit var nameLabel: Label
            private lateinit var freqLabel: Label

            val colorListener: ChangeListener<String> = ChangeListener { _, _, newValue ->
                runOnUIThread {
                    val color = Color.web(newValue ?: "white")
                    nameLabel.textFill = color
                    freqLabel.textFill = color
                }
            }
            val freqListener: ChangeListener<Number> = tornadofx.ChangeListener { _, _, newValue ->
                runOnUIThread {
                    freqLabel.text = "(${newValue})"
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
            }

            override fun updateItem(item: Tag?, empty: Boolean) {
                getItem()?.color?.removeListener(colorListener)
                getItem()?.frequency?.removeListener(freqListener)

                super.updateItem(item, empty)

                item?.color?.addListener(colorListener)
                item?.frequency?.removeListener(freqListener)

                val color = Color.web(item?.color?.value ?: "white")
                nameLabel.apply {
                    text = item?.name
                    textFill = color
                }
                freqLabel.apply {
                    text = if (item != null) {
                        "${item.frequency.value}"
                    } else {
                        null
                    }
                    textFill = color
                }
            }

        }
    }

}