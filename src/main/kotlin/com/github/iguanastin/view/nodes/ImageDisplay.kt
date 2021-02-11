package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.menagerie.model.ImageItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.view.image
import com.github.iguanastin.view.nodes.image.panzoomimageview

class ImageDisplay: ItemDisplay() {

    private val imageView = panzoomimageview {
        applyScaleAsync = true
    }

    init {
        center = imageView

        itemProperty.addListener { _, _, new ->
            imageView.trueImage = if (new is ImageItem) image(new.file, true) else null
        }
    }

    override fun canDisplay(item: Item): Boolean {
        return item is ImageItem
    }

}