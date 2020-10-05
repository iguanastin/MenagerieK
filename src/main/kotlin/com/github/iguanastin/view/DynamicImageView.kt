/*
 MIT License

 Copyright (c) 2019. Austin Thompson

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */
package com.github.iguanastin.view

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
        val I = image ?: return minWidth(height)
        return I.width
    }

    override fun maxWidth(height: Double): Double {
        return 16384.0
    }

    override fun minHeight(width: Double): Double {
        return 40.0
    }

    override fun prefHeight(width: Double): Double {
        val I = image ?: return minHeight(width)
        return I.height
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