package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import javafx.event.EventHandler
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import tornadofx.*

class FourChooser() : VBox(25.0) {

    constructor(onCancel: (() -> Unit)? = null, onLeft: Pair<String, () -> Unit>? = null, onRight: Pair<String, () -> Unit>? = null, onTop: Pair<String, () -> Unit>? = null, onBottom: Pair<String, () -> Unit>? = null): this() {
        this.onCancel = onCancel
        this.onLeft = onLeft
        this.onRight = onRight
        this.onTop = onTop
        this.onBottom = onBottom
    }


    var onClose: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var onLeft: Pair<String, () -> Unit>? = null
        set(value) {
            field = value
            leftLabel.apply {
                isVisible = value != null
                text = value?.first ?: onRight?.first ?: ""
            }
        }
    var onRight: Pair<String, () -> Unit>? = null
        set(value) {
            field = value
            rightLabel.apply {
                isVisible = value != null
                text = value?.first ?: onLeft?.first ?: ""
            }
        }
    var onTop: Pair<String, () -> Unit>? = null
        set(value) {
            field = value
            topLabel.apply {
                isVisible = value != null
                text = value?.first ?: ""
            }
        }
    var onBottom: Pair<String, () -> Unit>? = null
        set(value) {
            field = value
            bottomLabel.apply {
                isVisible = value != null
                text = onBottom?.first ?: ""
            }
        }


    private lateinit var cancel: Label
    private lateinit var topLabel: Label
    private lateinit var bottomLabel: Label
    private lateinit var leftLabel: Label
    private lateinit var rightLabel: Label


    init {
        alignment = Pos.CENTER

        topLabel = label {
            borderpaneConstraints { alignment = Pos.CENTER }
            addClass(Styles.fourChoice)
            onMouseClicked = EventHandler {
                onTop?.second?.invoke()
                it.consume()
                close()
            }
        }

        hbox(spacing = 25, alignment = Pos.CENTER) {
            leftLabel = label {
                borderpaneConstraints { alignment = Pos.CENTER }
                addClass(Styles.fourChoice)
                onMouseClicked = EventHandler {
                    onLeft?.second?.invoke()
                    it.consume()
                    close()
                }
            }

            cancel = label("Cancel") {
                borderpaneConstraints { alignment = Pos.CENTER }
                addClass(Styles.fourChoice)
                onMouseClicked = EventHandler {
                    onCancel?.invoke()
                    it.consume()
                    close()
                }
            }

            rightLabel = label {
                isVisible = onRight != null
                borderpaneConstraints { alignment = Pos.CENTER }
                addClass(Styles.fourChoice)
                onMouseClicked = EventHandler {
                    onRight?.second?.invoke()
                    it.consume()
                    close()
                }
            }
        }

        bottomLabel = label {
            isVisible = onBottom != null
            borderpaneConstraints { alignment = Pos.CENTER }
            addClass(Styles.fourChoice)
            onMouseClicked = EventHandler {
                onBottom?.second?.invoke()
                it.consume()
                close()
            }
        }
    }


    fun close() {
        removeFromParent()
        onClose?.invoke()
    }

}

fun EventTarget.fourChooser(onCancel: (() -> Unit)? = null, onLeft: Pair<String, () -> Unit>? = null, onRight: Pair<String, () -> Unit>? = null, onTop: Pair<String, () -> Unit>? = null, onBottom: Pair<String, () -> Unit>? = null, op: FourChooser.() -> Unit = {}) = FourChooser(onCancel, onLeft, onRight, onTop, onBottom).attachTo(this, op)
