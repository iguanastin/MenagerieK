package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.model.*
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
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.StackPane
import javafx.scene.text.TextAlignment
import mu.KotlinLogging
import tornadofx.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

private val log = KotlinLogging.logger {}

class MultiTypeItemDisplay : StackPane() {

    val groupDisplay = GroupDisplay()
    val imageDisplay = ImageDisplay()
    val videoDisplay = VideoDisplay()
    val plaintextDisplay = PlainTextDisplay()

    private val displays: List<ItemDisplay> = listOf(groupDisplay, imageDisplay, videoDisplay, plaintextDisplay)

    private lateinit var infoLabel: Label

    private val infoPadding: Double = 5.0

    private val infoRightProperty: BooleanProperty = SimpleBooleanProperty(false).apply {
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
    private var infoRight: Boolean
        set(value) = infoRightProperty.set(value)
        get() = infoRightProperty.get()

    private val expandedInfoProperty: BooleanProperty = SimpleBooleanProperty(false).apply {
        addListener { _ ->
            runOnUIThread { updateInfo(item) }
        }
    }
    private var expandedInfo: Boolean
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

        displays.forEach { d ->
            add(d)
            d.isVisible = false
        }

        anchorpane {
            isPickOnBounds = false
            infoLabel = label {
                addClass(Styles.infoLabel)
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
        displays.forEach { d ->
            d.item = if (d.canDisplay(item)) item else null
            d.isVisible = d.item != null
        }

        updateInfo(item)
    }

    private fun updateInfo(new: Item?) {
        infoLabel.text = null
        if (new == null) return

        val time =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(
                ZoneId.systemDefault()
            ).format(Date(new.added).toInstant())
        infoLabel.text = if (expandedInfo) "ID: ${new.id}\n$time\n" else ""

        if (new is FileItem) {
            val fileSize = bytesToPrettyString(new.file.length())

            if (new is ImageItem) {
                imageDisplay.trueImage?.afterLoaded {
                    val resolution = "${width.toInt()}x${height.toInt()}"

                    runOnUIThread {
                        if (item == new) infoLabel.text += if (expandedInfo) "$fileSize\n" +
                                "$resolution\n" +
                                new.file.path else resolution
                    }
                }
            } else if (new is VideoItem) {
                infoLabel.text += if (expandedInfo) "$fileSize\n" +
                        new.file.path else "ID: ${new.id}"
            }
        } else if (new is GroupItem) {
            infoLabel.text += if (expandedInfo) "Size: ${new.items.size}" else "ID: ${new.id}"
        }
    }

    fun release() {
        videoDisplay.release()
    }

}

fun EventTarget.multitypeitemdisplay(op: MultiTypeItemDisplay.() -> Unit = {}) =
    MultiTypeItemDisplay().attachTo(this, op)