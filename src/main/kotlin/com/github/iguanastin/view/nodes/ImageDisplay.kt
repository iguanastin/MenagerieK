package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.menagerie.model.ImageItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.view.image
import com.github.iguanastin.view.nodes.image.PanZoomImageView
import com.github.iguanastin.view.nodes.image.panzoomimageview
import javafx.event.EventTarget
import javafx.scene.image.Image
import tornadofx.*

class ImageDisplay : ItemDisplay() {

    private lateinit var imageView: PanZoomImageView

    val trueImage: Image?
        get() = imageView.trueImage


    init {
        center = stackpane {
            imageView = panzoomimageview {
                applyScaleAsync = true
            }
        }

        imageView.trueImageProperty.bind(itemProperty.map { if (it is ImageItem) image(it.file, true) else null })
    }

    override fun canDisplay(item: Item?): Boolean {
        return item is ImageItem
    }

}

fun EventTarget.imagedisplay(op: ImageDisplay.() -> Unit = {}) = ImageDisplay().attachTo(this, op)