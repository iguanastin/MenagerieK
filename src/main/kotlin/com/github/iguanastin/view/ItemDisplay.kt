package com.github.iguanastin.view

import com.github.iguanastin.app.menagerie.GroupItem
import com.github.iguanastin.app.menagerie.ImageItem
import com.github.iguanastin.app.menagerie.Item
import javafx.application.Platform
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventTarget
import javafx.scene.layout.StackPane
import mu.KotlinLogging
import tornadofx.*

private val log = KotlinLogging.logger {}

class ItemDisplay : StackPane() {

    private val imageDisplay: PanZoomImageView = panzoomimageview()
    private val groupDisplay: GroupPreview = grouppreview()


    val itemProperty: ObjectProperty<Item?> = SimpleObjectProperty(null)
    var item: Item?
        get() {
            return itemProperty.get()
        }
        set(value) {
            itemProperty.set(value)
        }


    init {
        itemProperty.addListener(ChangeListener { _, _, newValue ->
            runOnUIThread {
                display(newValue)
            }
        })
    }


    private fun display(item: Item?) {
        imageDisplay.apply {
            image = null
            hide()
        }
        groupDisplay.apply {
            group = null
            hide()
        }

        when (item) {
            is GroupItem -> {
                groupDisplay.apply {
                    group = item
                    show()
                }
            }
            is ImageItem -> {
                imageDisplay.apply {
                    image = image(item.file, true)
                    show()
                    Platform.runLater { fitImageToView() } // TODO this nightmare still doesn't work
                }
            }
            null -> {
                // Do nothing
            }
            else -> {
                log.info("Can't display item of unknown type: $item")
            }
        }
    }

}

fun EventTarget.itemdisplay(op: ItemDisplay.() -> Unit = {}) = ItemDisplay().attachTo(this, op)