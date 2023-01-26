package com.github.iguanastin.view.nodes.image

import javafx.event.EventTarget
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import tornadofx.*


open class DynamicImageView : ImageView {
    constructor() : super()
    constructor(url: String) : super(url)
    constructor(img: Image) : super(img)

    override fun minWidth(height: Double): Double {
        return 40.0
    }

    override fun prefWidth(height: Double): Double {
        val img = image ?: return minWidth(height)
        return img.width
    }

    override fun maxWidth(height: Double): Double {
        return 16384.0
    }

    override fun minHeight(width: Double): Double {
        return 40.0
    }

    override fun prefHeight(width: Double): Double {
        val img = image ?: return minHeight(width)
        return img.height
    }

    override fun maxHeight(width: Double): Double {
        return 16384.0
    }

    override fun isResizable(): Boolean {
        return true
    }

    override fun resize(width: Double, height: Double) {
        if (image == null) {
            fitWidth = width
            fitHeight = height
        } else {
            var scale = 1.0
            if (scale * image.width > width) scale = width / image.width
            if (scale * image.height > height) scale = height / image.height
            fitWidth = image.width * scale
            fitHeight = image.height * scale
        }
    }
}

fun EventTarget.dynamicImageView(url: String, op: DynamicImageView.() -> Unit = {}) = DynamicImageView(url).attachTo(this, op)
fun EventTarget.dynamicImageView(op: DynamicImageView.() -> Unit = {}) = DynamicImageView().attachTo(this, op)