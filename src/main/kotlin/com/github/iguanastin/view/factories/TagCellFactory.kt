package com.github.iguanastin.view.factories

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.view.nodes.TagColorPicker
import com.github.iguanastin.view.runOnUIThread
import javafx.beans.value.ChangeListener
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.util.Callback
import tornadofx.*

open class TagCellFactory(var onTagClick: ((Tag) -> Unit)? = null) :
    Callback<ListView<Tag>, ListCell<Tag>> {

    override fun call(listView: ListView<Tag>?): ListCell<Tag> {
        return object : ListCell<Tag>() {

            private lateinit var nameLabel: Label
            private lateinit var freqLabel: Label

            val colorListener: ChangeListener<String> = ChangeListener { _, _, newValue ->
                runOnUIThread {
                    val color = c(newValue ?: "white")
                    nameLabel.textFill = color
                    freqLabel.textFill = color
                }
            }
            val freqListener: ChangeListener<Number> = ChangeListener { _, _, newValue ->
                runOnUIThread {
                    freqLabel.text = "$newValue"
                }
            }

            init {
                addClass(Styles.tagListCell)
                graphic = borderpane {
                    left {
                        nameLabel = label()
                    }
                    right {
                        freqLabel = label()
                    }

                    addEventHandler(MouseEvent.MOUSE_CLICKED) { event ->
                        if (item != null && event.button == MouseButton.PRIMARY) {
                            event.consume()
                            if (item.temporary) {
                                promoteTemporaryTag(item)
                            } else {
                                onTagClick?.invoke(item)
                            }
                        }
                    }
                }

                contextmenu {
                    item("", graphic = TagColorPicker { color ->
                        hide()
                        item?.color =
                            color // TODO: Make this a reversible edit, if it's not too much hassle to get a context
                    })

                    disableWhen(itemProperty().isNull)
                }
            }

            override fun updateItem(item: Tag?, empty: Boolean) {
                getItem()?.colorProperty?.removeListener(colorListener)
                getItem()?.frequencyProperty?.removeListener(freqListener)

                super.updateItem(item, empty)

                item?.colorProperty?.addListener(colorListener)
                item?.frequencyProperty?.addListener(freqListener)

                if (item?.temporary == true) {
                    addClass(Styles.tempTag)
                } else {
                    removeClass(Styles.tempTag)
                }

                val color = c(item?.color ?: "white")
                nameLabel.apply {
                    text = item?.name
                    textFill = color
                }
                freqLabel.apply {
                    text = if (item != null) {
                        if (item.temporary) "TEMP" else "${item.frequency}"
                    } else {
                        null
                    }
                    textFill = color
                }
            }

        }
    }

    private fun promoteTemporaryTag(tag: Tag) {
        tag.menagerie.removeTag(tag)

        val newTag = Tag(tag.menagerie.reserveTagID(), tag.name, tag.menagerie, tag.color)
        tag.menagerie.addTag(newTag)

        tag.menagerie.items.forEach {
            if (tag in it.tags) {
                it.removeTag(tag)
                it.addTag(newTag)
            }
        }
    }

}