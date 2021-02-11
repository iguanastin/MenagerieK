package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.menagerie.model.ImageItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.utils.bytesToPrettyString
import com.github.iguanastin.view.afterLoaded
import com.github.iguanastin.view.image
import com.github.iguanastin.view.nodes.image.PanZoomImageView
import com.github.iguanastin.view.nodes.image.panzoomimageview
import com.github.iguanastin.view.runOnUIThread
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.effect.DropShadow
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.AnchorPane
import javafx.scene.paint.Color
import javafx.scene.text.TextAlignment
import tornadofx.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

class ImageDisplay : ItemDisplay() {

    private lateinit var imageView: PanZoomImageView
    private lateinit var infoLabel: Label

    private val infoPadding: Double = 5.0

    val infoRightProperty: BooleanProperty = SimpleBooleanProperty(false).apply {
        addListener { _, _, new ->
            runOnUIThread {
                infoLabel.apply {
                    textAlignment = if (new) TextAlignment.RIGHT else TextAlignment.LEFT
                    alignment = if (new) Pos.BOTTOM_RIGHT else Pos.BOTTOM_LEFT
                    if (new) {
                        AnchorPane.setLeftAnchor(infoLabel, null)
                        AnchorPane.setRightAnchor(infoLabel, infoPadding)
                    } else {
                        AnchorPane.setLeftAnchor(infoLabel, infoPadding)
                        AnchorPane.setRightAnchor(infoLabel, null)
                    }
                }
            }
        }
    }
    var infoRight: Boolean
        set(value) = infoRightProperty.set(value)
        get() = infoRightProperty.get()

    val expandedInfoProperty: BooleanProperty = SimpleBooleanProperty(false).apply {
        addListener { _ ->
            runOnUIThread { updateInfo(item) }
        }
    }
    var expandedInfo: Boolean
        set(value) = expandedInfoProperty.set(value)
        get() = expandedInfoProperty.get()


    init {
        center = stackpane {
            imageView = panzoomimageview {
                applyScaleAsync = true
            }

            anchorpane {
                isPickOnBounds = false
                infoLabel = label {
                    effect = DropShadow(5.0, Color.BLACK).apply { spread = 0.5 }
                    alignment = Pos.BOTTOM_LEFT
                    anchorpaneConstraints {
                        leftAnchor = infoPadding
                        bottomAnchor = infoPadding
                    }

                    addEventHandler(MouseEvent.MOUSE_CLICKED) { event ->
                        if (event.button == MouseButton.PRIMARY) {
                            event.consume()
                            expandedInfo = !expandedInfo
                        } else if (event.button == MouseButton.SECONDARY) {
                            event.consume()
                            infoRight = !infoRight
                        }
                    }
                }
            }
        }

        itemProperty.addListener { _, _, new ->
            runOnUIThread {
                imageView.trueImage = if (new is ImageItem) image(new.file, true) else null
                infoLabel.text = null

                updateInfo(new)
            }
        }
    }

    private fun updateInfo(new: Item?) {
        if (new != null) {
            val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Date(new.added).toInstant())
            infoLabel.text = if (expandedInfo) "ID: ${new.id}\n$time" else ""

            if (new is ImageItem) {
                imageView.trueImage?.afterLoaded {
                    val fileSize = bytesToPrettyString(new.file.length())
                    val resolution = "${width.toInt()}x${height.toInt()}"

                    runOnUIThread {
                        if (item == new) infoLabel.text = if (expandedInfo) "ID: ${new.id}\n$fileSize\n$resolution\n$time\n${new.file.path}" else resolution
                    }
                }
            }
        }
    }

    override fun canDisplay(item: Item): Boolean {
        return item is ImageItem
    }

}