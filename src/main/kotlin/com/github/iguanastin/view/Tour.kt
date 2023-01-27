package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import javafx.application.Platform
import javafx.event.Event
import javafx.event.EventHandler
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
    private var currentPopup: Popup? = null

    private val stageFocusListener = ChangeListener<Boolean> { _, _, new ->
        if (!new) currentPopup?.hide()
        else currentPopup?.show(stage)
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

        currentPopup?.hide()
        currentStop = null

        onEnd()
    }

    private fun showStop(stop: TourStop) {
        currentPopup?.hide()
        currentPopup = stop.show(stage)
        currentStop = stop
    }

}

class TourStop(val node: Node?, val message: String, val pos: TextPos = TextPos.BOTTOM_RIGHT) {

    enum class TextPos {
        LEFT,
        RIGHT,
        TOP_LEFT,
        BOTTOM_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT
    }

    fun show(stage: Stage): Popup {
        return Popup().apply {
            anchorLocation = PopupWindow.AnchorLocation.CONTENT_TOP_LEFT
            isAutoHide = false
            isHideOnEscape = false

            val pad = 10.0
            val bounds = node?.localToScreen(node.boundsInLocal)
            if (node != null) {
                content.add(Region().apply {
                    addClass(Styles.tourOutline)
                    isMouseTransparent = true
                    prefWidth = bounds!!.width + (pad * 2)
                    prefHeight = bounds.height + (pad * 2)
                })
            }
            content.add(Label(message).apply {
                addClass(Styles.tourText)
                isWrapText = true
                maxWidth = 300.0
                padding = insets(pad)
                Platform.runLater { parent.addClass(Styles.bastardTourParentPane) } // Popup contains its elements in a Pane which isn't exposed and will automatically give itself a background color, and I don't know why.
                if (node != null) {
                    when (pos) {
                        TextPos.LEFT -> Platform.runLater { translateX = -width - pad }
                        TextPos.RIGHT -> translateX = bounds!!.width + (pad * 3)
                        TextPos.TOP_LEFT -> Platform.runLater {
                            translateX = -width + pad + bounds!!.width
                            translateY = -height - pad
                        }
                        TextPos.BOTTOM_LEFT -> Platform.runLater {
                            translateX = -width + pad + bounds!!.width
                            translateY = bounds.height + (pad * 3)
                        }
                        TextPos.TOP_RIGHT -> Platform.runLater { translateY = -height - pad }
                        TextPos.BOTTOM_RIGHT -> translateY = bounds!!.height + (pad * 3)
                    }
                }
            })

            if (node != null) {
                show(node, bounds!!.minX - pad, bounds.minY - pad)
            } else {
                show(stage)
                centerOnScreen()
            }
        }
    }

}