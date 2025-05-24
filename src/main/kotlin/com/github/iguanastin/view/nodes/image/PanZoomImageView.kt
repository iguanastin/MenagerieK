package com.github.iguanastin.view.nodes.image

import com.github.iguanastin.app.utils.addIfUnique
import com.github.iguanastin.view.afterLoaded
import com.github.iguanastin.view.runOnUIThread
import javafx.beans.property.*
import javafx.event.EventTarget
import javafx.geometry.Rectangle2D
import javafx.scene.Cursor
import javafx.scene.image.Image
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import tornadofx.*
import kotlin.math.abs
import kotlin.math.max


class PanZoomImageView : DynamicImageView() {

    companion object {
        private val SCALES = arrayOf(
            0.1,
            0.13,
            0.18,
            0.24,
            0.32,
            0.42,
            0.56,
            0.75,
            1.0,
            1.25,
            1.56,
            1.95,
            2.44,
            3.05,
            3.81,
            4.76,
            5.95,
            7.44,
            9.3
        )
        private const val scaleAsyncGreaterThan = 1.0
    }

    val trueImageProperty: ObjectProperty<Image?> = SimpleObjectProperty()
    var trueImage: Image?
        get() = trueImageProperty.get()
        set(value) = trueImageProperty.set(value)

    private val _scaleProperty: DoubleProperty = SimpleDoubleProperty(1.0)
    private val scaleProperty: ReadOnlyDoubleProperty = _scaleProperty
    var scale: Double
        get() = _scaleProperty.get()
        private set(value) = _scaleProperty.set(value)

    private var deltaX = 0.0
    private var deltaY = 0.0
    private var clickX = 0.0
    private var clickY = 0.0
    private var clickImageX = 0.0
    private var clickImageY = 0.0
    private var draggedThisClick = false

    var applyScaleAsyncProperty: BooleanProperty = SimpleBooleanProperty(false)
    var applyScaleAsync: Boolean
        get() = applyScaleAsyncProperty.get()
        set(value) = applyScaleAsyncProperty.set(value)

    private var _scaleAppliedProperty: BooleanProperty = SimpleBooleanProperty(false)
    var scaleAppliedProperty: ReadOnlyBooleanProperty = _scaleAppliedProperty
    var isScaleApplied
        get() = _scaleAppliedProperty.get()
        private set(value) = _scaleAppliedProperty.set(value)

    private var scalerThread: ImageScalerThread? = null


    init {
        isPickOnBounds = true

        addEventHandler(MouseEvent.MOUSE_DRAGGED) { event: MouseEvent ->
            if (event.button == MouseButton.PRIMARY) {
                var s = scale
                if (isScaleApplied) s = 1.0
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
                var s = scale
                if (isScaleApplied) s = 1.0
                val w = image.width / s
                val h = image.height / s
                if (deltaX == 0.0 && deltaY == 0.0 && (abs(fitWidth - w) < 5 || abs(fitHeight - h) < 5)) {
                    scale = 1.0
                    updateViewPort()
                } else {
                    fitImageToView()
                }
            }
        }
        addEventHandler(ScrollEvent.SCROLL) { event: ScrollEvent ->
            if (event.deltaY == 0.0) return@addEventHandler
            val image = trueImage ?: return@addEventHandler

            mutableListOf(*SCALES).apply {
                addIfUnique(scale, getFitScale(image))
                if (event.deltaY < 0) sort() else sortDescending()
                scale = this[(indexOf(scale) + 1).coerceIn(0, size - 1)]
            }

            updateViewPort()
            event.consume()
        }
        addEventHandler(MouseEvent.MOUSE_ENTERED) { scene.cursor = Cursor.MOVE }
        addEventHandler(MouseEvent.MOUSE_EXITED) { scene.cursor = Cursor.DEFAULT }


        trueImageProperty.addListener { _, _, image ->
            this.image = image
            isScaleApplied = false

            image?.afterLoaded {
                runOnUIThread { fitImageToView() }
            }
        }

        scaleProperty.addListener { _, _, newValue ->
            val newScale = newValue.toDouble()
            if (isScaleApplied) {
                isScaleApplied = false
                deltaX *= newScale
                deltaY *= newScale
                image = trueImage
            }
            tryEnqueueScaleAsync(newScale)
        }

        applyScaleAsyncProperty.addListener { _, _, scaleAsync ->
            scalerThread?.close()
            scalerThread = null

            if (scaleAsync) {
                scalerThread = ImageScalerThread().apply {
                    isDaemon = true
                    start()
                }

                tryEnqueueScaleAsync(scale)
            }
        }
    }

    private fun tryEnqueueScaleAsync(newScale: Double) {
        if (!applyScaleAsync || newScale < scaleAsyncGreaterThan) return

        trueImage?.also { sourceImage ->
            scalerThread?.enqueue(ImageScaleJob(sourceImage, newScale, { scaledImage ->
                if (scale != newScale || trueImage != sourceImage) return@ImageScaleJob
                runOnUIThread { setAppliedScaleImage(scaledImage) }
            }))
        }
    }

    fun fitImageToView() {
        deltaY = 0.0
        deltaX = deltaY
        scale = 1.0

        updateViewPort()

        image?.also { img ->
            val fit = {
                scale = getFitScale(img)
                updateViewPort()
            }
            img.afterLoaded {
                if (image == this) runOnUIThread(fit)
            }
        }
    }

    private fun getFitScale(img: Image): Double {
        return max(img.width / fitWidth, img.height/ fitHeight).coerceAtLeast(1.0)
    }

    private fun setAppliedScaleImage(image: Image?) {
        setImage(image)

        if (!isScaleApplied) {
            deltaX /= scale
            deltaY /= scale
        }
        isScaleApplied = true

        updateViewPort()
    }

    override fun resize(width: Double, height: Double) {
        fitWidth = width
        fitHeight = height
        if (image != null) {
            updateViewPort()
        }
    }

    private fun updateViewPort() {
        if (image == null || fitWidth == 0.0 || fitHeight == 0.0) return

        var scale = scale
        if (isScaleApplied) scale = 1.0

        val fitWidth = fitWidth * scale
        val fitHeight = fitHeight * scale

        val imageWidth = if (isScaleApplied) image.width / scale else image.width
        val imageHeight = if (isScaleApplied) image.height / scale else image.height
        deltaX = deltaX.coerceIn(-imageWidth / 2, imageWidth / 2)
        deltaY = deltaY.coerceIn(-imageHeight / 2, imageHeight / 2)

        val viewportX = (imageWidth - fitWidth) / 2 + deltaX
        val viewportY = (imageHeight - fitHeight) / 2 + deltaY
        viewport = Rectangle2D(viewportX, viewportY, fitWidth, fitHeight)
    }

}

fun EventTarget.panzoomimageview(op: PanZoomImageView.() -> Unit = {}) = PanZoomImageView().attachTo(this, op)