package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.ImageItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.utils.bytesToPrettyString
import com.github.iguanastin.view.afterLoaded
import com.github.iguanastin.view.runOnUIThread
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.effect.DropShadow
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.text.TextAlignment
import mu.KotlinLogging
import tornadofx.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

private val log = KotlinLogging.logger {}

class MultiTypeItemDisplay : StackPane() {

    private val groupDisplay: GroupDisplay
    private val imageDisplay: ImageDisplay
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

    private val expandedInfoProperty: BooleanProperty = SimpleBooleanProperty(false).apply {
        addListener { _ ->
            runOnUIThread { updateInfo(item) }
        }
    }
    var expandedInfo: Boolean
        set(value) = expandedInfoProperty.set(value)
        get() = expandedInfoProperty.get()

    val itemProperty: ObjectProperty<Item?> = SimpleObjectProperty(null)
    var item: Item?
        get() = itemProperty.get()
        set(value) = itemProperty.set(value)


    init {
        itemProperty.addListener(ChangeListener { _, _, newValue ->
            runOnUIThread {
                display(newValue)
            }
        })

        groupDisplay = groupdisplay {
            isVisible = false
        }
        imageDisplay = imagedisplay {
            isVisible = false
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


    private fun display(item: Item?) {
        // Reset displays
        groupDisplay.apply {
            this.item = if (canDisplay(item)) item else null
            isVisible = this.item != null
        }
        imageDisplay.apply {
            this.item = if (canDisplay(item)) item else null
            isVisible = this.item != null
        }

        updateInfo(item)
    }

    private fun updateInfo(new: Item?) {
        if (new != null) {
            val time =
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(
                    ZoneId.systemDefault()
                ).format(Date(new.added).toInstant())
            infoLabel.text = if (expandedInfo) "ID: ${new.id}\n$time\n" else ""

            if (new is ImageItem) {
                imageDisplay.trueImage?.afterLoaded {
                    val fileSize = bytesToPrettyString(new.file.length())
                    val resolution = "${width.toInt()}x${height.toInt()}"

                    runOnUIThread {
                        if (item == new) infoLabel.text += if (expandedInfo) "$fileSize\n" +
                                "$resolution\n" +
                                "${new.file.path}" else resolution
                    }
                }
            } else if (new is GroupItem) {
                infoLabel.text += if (expandedInfo) "Size: ${new.items.size}" else "ID: ${new.id}"
            }
        }
    }

}

fun EventTarget.multitypeitemdisplay(op: MultiTypeItemDisplay.() -> Unit = {}) =
    MultiTypeItemDisplay().attachTo(this, op)