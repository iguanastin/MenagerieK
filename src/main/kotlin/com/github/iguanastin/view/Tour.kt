package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import javafx.application.Platform
import javafx.event.Event
import javafx.event.EventHandler
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Region
import javafx.stage.Popup
import javafx.stage.PopupWindow
import javafx.stage.Stage
import tornadofx.*

class Tour(val stage: Stage) {

    var onStart: () -> Unit = {}
    var onEnd: () -> Unit = {}

    private val stops: MutableList<TourStop> = mutableListOf()
    private var currentStop: TourStop? = null

    private val stageFocusListener = ChangeListener<Boolean> { _, _, new ->
        if (!new) currentStop?.hide()
        else currentStop?.show(stage)
    }

    private val keyEventHandler = EventHandler<KeyEvent> { event ->
        if (currentStop == null) return@EventHandler
        val i = stops.indexOf(currentStop)

        if (event.code == KeyCode.LEFT && i > 0) {
            showStop(stops[i - 1])
        } else if (event.code == KeyCode.RIGHT) {
            if (i < stops.size - 1) {
                showStop(stops[i + 1])
            } else {
                end()
            }
        } else if (event.code == KeyCode.ESCAPE) {
            end()
        }
    }

    private val eventFilter = EventHandler<Event> { event ->
        if (event is KeyEvent && event.eventType == KeyEvent.KEY_PRESSED) keyEventHandler.handle(event)

        event.consume()
    }

    fun addStop(stop: TourStop) {
        stops.add(stop)
    }

    fun start() {
        if (stops.isEmpty()) return

        stage.focusedProperty().addListener(stageFocusListener)
        stage.addEventFilter(Event.ANY, eventFilter)
        showStop(stops.first())

        onStart()
    }

    fun end() {
        stage.removeEventFilter(Event.ANY, eventFilter)
        stage.focusedProperty().removeListener(stageFocusListener)

        currentStop?.hide()
        currentStop = null

        onEnd()
    }

    private fun showStop(stop: TourStop) {
        currentStop?.hide()
        currentStop = stop
        stop.show(stage)
    }

}

class TourStop(val node: Node?, val message: String, val pos: TextPos = TextPos.BOTTOM_RIGHT) {

    companion object {
        private const val pad = 10.0
    }

    enum class TextPos {
        LEFT,
        RIGHT,
        TOP_LEFT,
        BOTTOM_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT
    }

    private val highlightPopup: Popup = Popup().apply {
        anchorLocation = PopupWindow.AnchorLocation.CONTENT_TOP_LEFT
        isAutoHide = false
        isHideOnEscape = false
        content.add(Region().apply {
            addClass(Styles.tourOutline)
            isMouseTransparent = true
            val targetBounds = node?.localToScreen(node.boundsInLocal)
            if (targetBounds != null) {
                prefWidth = targetBounds.width + (pad * 2)
                prefHeight = targetBounds.height + (pad * 2)
            }
            Platform.runLater { parent.addClass(Styles.bastardTourParentPane) } // Popup contains its elements in a Pane which isn't exposed and will automatically give itself a background color, and I don't know why.
        })
    }

    private val messagePopup: Popup = Popup().apply {
        anchorLocation = PopupWindow.AnchorLocation.CONTENT_TOP_LEFT
        isAutoHide = false
        isHideOnEscape = false

        content.add(Label(message).apply {
            addClass(Styles.tourText)
            isWrapText = true
            maxWidth = 300.0
            padding = insets(pad)
            layout()
            Platform.runLater { parent.addClass(Styles.bastardTourParentPane) } // Popup contains its elements in a Pane which isn't exposed and will automatically give itself a background color, and I don't know why.
        })
    }

    fun show(stage: Stage) {
        if (node != null) {
            val targetBounds = node.localToScreen(node.boundsInLocal)
            highlightPopup.show(node, targetBounds.minX - pad, targetBounds.minY - pad)

            showMessagePopup(targetBounds)
            Platform.runLater { showMessagePopup(targetBounds) } // Run it again after a layout happens
        } else {
            messagePopup.centerOnScreen()
            messagePopup.show(stage)
        }
    }

    private fun showMessagePopup(targetBounds: Bounds) {
        var x = targetBounds.minX
        var y = targetBounds.minY

        when (pos) {
            TextPos.BOTTOM_RIGHT -> {
                y += targetBounds.height + pad * 2
            }
            TextPos.LEFT -> {
                x += -messagePopup.width - pad * 3
            }
            TextPos.RIGHT -> {
                x += targetBounds.width + pad * 2
            }
            TextPos.TOP_LEFT -> {
                x += targetBounds.width - messagePopup.width
                y += -messagePopup.height - pad * 3
            }
            TextPos.BOTTOM_LEFT -> {
                x += targetBounds.width - messagePopup.width
                y += targetBounds.height + pad * 2
            }
            TextPos.TOP_RIGHT -> {
                y += -messagePopup.height - pad * 3
            }
        }

        messagePopup.show(node, x, y)
    }

    fun hide() {
        highlightPopup.hide()
        messagePopup.hide()
    }

}