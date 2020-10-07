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

import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.event.EventTarget
import javafx.geometry.Rectangle2D
import javafx.scene.Cursor
import javafx.scene.image.Image
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import tornadofx.*
import java.util.*

/**
 * An ImageView that dynamically fits to the parent node and implements zooming and panning with the mouse.
 */
class PanZoomImageView : DynamicImageView {
    constructor() : super()
    constructor(url: String) : super(url)

    /**
     * Delta offset (pan) from the center of the image
     */
    private var deltaX = 0.0
    private var deltaY = 0.0
    /**
     *
     * @return Current zoom scale
     */
    /**
     * Zoom scale property
     */
    val scale: DoubleProperty = SimpleDoubleProperty(1.0)

    /**
     * Location of the mouse click
     */
    private var clickX = 0.0
    private var clickY = 0.0

    /**
     * Location of the mouse click in the context of image coordinates
     */
    private var clickImageX = 0.0
    private var clickImageY = 0.0

    /**
     * Flag if the mouse was dragged before being released
     */
    private var draggedThisClick = false

    /**
     * Fits the image to the view at 100% scale if possible. If image is larger than view, scales down to fit whole image in view.
     */
    fun fitImageToView() {
        deltaY = 0.0
        deltaX = deltaY
        scale.set(1.0)
        updateViewPort()
        val img = image
        if (img != null) {
            scale.set(getFitScale(img))
            updateViewPort()
        }
    }

    /**
     * Computes the scale an image needs to be zoomed by in order to fit inside the bounds of this node
     *
     * @param img Image to find scale for
     * @return Scale to zoom image by to fit in this node
     */
    private fun getFitScale(img: Image): Double {
        var s = img.width / fitWidth
        if (img.height / fitHeight > s) s = img.height / fitHeight
        if (s < 1) s = 1.0
        return s
    }

    override fun resize(width: Double, height: Double) {
        fitWidth = width
        fitHeight = height
        if (image != null) {
            updateViewPort()
        }
    }

    /**
     * Recalculates image viewport.
     */
    private fun updateViewPort() {
        if (image == null || fitWidth == 0.0 || fitHeight == 0.0) return
        val scale = scale.get()
        val fitWidth = fitWidth * scale
        val fitHeight = fitHeight * scale
        val imageWidth = image.width
        val imageHeight = image.height
        deltaX = Math.max(Math.min(deltaX, imageWidth / 2), -imageWidth / 2)
        deltaY = Math.max(Math.min(deltaY, imageHeight / 2), -imageHeight / 2)
        val viewportX = (imageWidth - fitWidth) / 2 + deltaX
        val viewportY = (imageHeight - fitHeight) / 2 + deltaY
        viewport = Rectangle2D(viewportX, viewportY, fitWidth, fitHeight)
    }

    companion object {
        /**
         * Array of possible zoom values
         */
        private val SCALES = doubleArrayOf(0.1, 0.13, 0.18, 0.24, 0.32, 0.42, 0.56, 0.75, 1.0, 1.25, 1.56, 1.95, 2.44, 3.05, 3.81, 4.76, 5.95, 7.44, 9.3)
    }

    /**
     * Empty image view with panning and zooming functionality.
     */
    init {
        isPickOnBounds = true
        addEventHandler(MouseEvent.MOUSE_DRAGGED) { event: MouseEvent ->
            if (event.button == MouseButton.PRIMARY) {
                val s = scale.get()
                deltaX = clickImageX + (clickX - event.x) * s
                deltaY = clickImageY + (clickY - event.y) * s
                updateViewPort()
                draggedThisClick = true
                event.consume()
            }
        }
        addEventHandler(MouseEvent.MOUSE_PRESSED) { event: MouseEvent ->
            if (event.button == MouseButton.PRIMARY) {
                draggedThisClick = false
                clickX = event.x
                clickY = event.y
                clickImageX = deltaX
                clickImageY = deltaY
            }
        }
        addEventHandler(MouseEvent.MOUSE_RELEASED) { event: MouseEvent ->
            if (event.button == MouseButton.PRIMARY && image != null && !draggedThisClick) {
                val s = scale.get()
                val w = image.width / s
                val h = image.height / s
                if (deltaX == 0.0 && deltaY == 0.0 && (Math.abs(fitWidth - w) < 5 || Math.abs(fitHeight - h) < 5)) {
                    scale.set(1.0)
                    updateViewPort()
                } else {
                    fitImageToView()
                }
            }
        }
        addEventHandler(ScrollEvent.SCROLL) { event: ScrollEvent ->
            val fitScale = getFitScale(image)
            val work: MutableList<Double> = ArrayList()
            for (v in SCALES) {
                work.add(v)
            }
            work.add(fitScale)
            Collections.sort(work)
            if (event.deltaY < 0) {
                for (d in work) {
                    if (d > scale.get()) {
                        scale.set(d)
                        break
                    }
                }
            } else {
                Collections.reverse(work)
                for (d in work) {
                    if (d < scale.get()) {
                        scale.set(d)
                        break
                    }
                }
            }
            updateViewPort()
            event.consume()
        }
        addEventHandler(MouseEvent.MOUSE_ENTERED) { event: MouseEvent? -> scene.cursor = Cursor.MOVE }
        addEventHandler(MouseEvent.MOUSE_EXITED) { event: MouseEvent? -> scene.cursor = Cursor.DEFAULT }
    }
}

fun EventTarget.panzoomimageview(op: PanZoomImageView.() -> Unit = {}) = PanZoomImageView().attachTo(this, op)
fun EventTarget.panzoomimageview(url: String, op: PanZoomImageView.() -> Unit = {}) = PanZoomImageView(url).attachTo(this, op)