package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.ImageItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.view.image
import com.github.iguanastin.view.nodes.image.PanZoomImageView
import com.github.iguanastin.view.nodes.image.panzoomimageview
import com.github.iguanastin.view.runOnUIThread
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventTarget
import javafx.scene.layout.StackPane
import mu.KotlinLogging
import tornadofx.*

private val log = KotlinLogging.logger {}

class ItemDisplay : StackPane() {

    private val imageDisplay: PanZoomImageView = panzoomimageview() {
        applyScaleAsync = true
    }
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

        imageDisplay.imageProperty().addListener(ChangeListener { _, _, newValue ->
            imageDisplay.isVisible = newValue != null
        })

        groupDisplay.groupProperty.addListener(ChangeListener { _, _, newValue ->
            groupDisplay.isVisible = newValue != null
        })
    }


    private fun display(item: Item?) {
        imageDisplay.trueImage = null
        groupDisplay.group = null

        when (item) {
            is GroupItem -> {
                groupDisplay.group = item
            }
            is ImageItem -> {
                imageDisplay.trueImage = image(item.file, true)
                imageDisplay.fitImageToView()
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