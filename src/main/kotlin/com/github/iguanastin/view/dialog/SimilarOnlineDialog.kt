package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.bytesToPrettyString
import com.github.iguanastin.app.menagerie.duplicates.IQDBDuplicateFinder
import com.github.iguanastin.app.menagerie.duplicates.OnlineMatch
import com.github.iguanastin.app.menagerie.duplicates.OnlineMatchSet
import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.view.ItemCellFactory
import com.github.iguanastin.view.gridView
import com.github.iguanastin.view.image
import com.github.iguanastin.view.runOnUIThread
import javafx.application.Platform
import javafx.beans.Observable
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.effect.DropShadow
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Priority
import javafx.util.Callback
import mu.KotlinLogging
import org.controlsfx.control.GridCell
import org.controlsfx.control.GridView
import tornadofx.*
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private val log = KotlinLogging.logger {}

class SimilarOnlineDialog(val matches: List<OnlineMatchSet>) : StackDialog() {

    private val factory = Callback<GridView<OnlineMatch>, GridCell<OnlineMatch>> {
        object : GridCell<OnlineMatch>() {

            private lateinit var thumbView: ImageView
            private lateinit var detailsLabel: Label
            private lateinit var sourceLabel: Label

            init {
                graphic = stackpane {
                    addClass(Styles.itemGridCell)

                    borderpane {
                        center {
                            thumbView = imageview {
                                maxWidth = Item.thumbnailSize
                                maxHeight = Item.thumbnailSize
                                minWidth = Item.thumbnailSize
                                minHeight = Item.thumbnailSize
                            }
                        }
                    }
                    sourceLabel = label {
                        padding = insets(5.0)
                        stackpaneConstraints { alignment = Pos.TOP_LEFT }
                        effect = DropShadow(5.0, c("black")).apply { spread = 0.5 }
                    }
                    detailsLabel = label {
                        padding = insets(5.0)
                        stackpaneConstraints { alignment = Pos.BOTTOM_RIGHT }
                        effect = DropShadow(5.0, c("black")).apply { spread = 0.5 }
                    }
                }

                addEventHandler(MouseEvent.MOUSE_PRESSED) { event ->
                    if (event.button == MouseButton.SECONDARY) {
                        event.consume()
                        if (item != null) Desktop.getDesktop().browse(URI(item.sourceUrl))
                    } else if (event.button == MouseButton.PRIMARY) {
                        event.consume()
                        if (item != null) {
                            this@SimilarOnlineDialog.parent.add(CompareSimilarOnlineDialog(viewing!!.item, item))
                        }
                    }
                }
            }

            override fun updateItem(item: OnlineMatch?, empty: Boolean) {
                super.updateItem(item, empty)

                thumbView.image = if (item == null) null else Image(item.thumbUrl, Item.thumbnailSize, Item.thumbnailSize, true, true, true)
                detailsLabel.text = item?.shortDetails
                sourceLabel.text = item?.sourceName
            }

        }
    }


    private val duper = IQDBDuplicateFinder()
    private val duperQueue: BlockingQueue<OnlineMatchSet> = LinkedBlockingQueue()

    private val viewingProperty: ObjectProperty<OnlineMatchSet?> = SimpleObjectProperty(null)
    private var viewing: OnlineMatchSet?
        get() = viewingProperty.get()
        set(value) = viewingProperty.set(value)


    private lateinit var yourImageView: ImageView
    private lateinit var matchGrid: GridView<OnlineMatch>
    private lateinit var yourItemDetails: Label
    private lateinit var loadingIndicator: ProgressIndicator
    private lateinit var indexLabel: Label
    private lateinit var refreshButton: Button


    init {
        root.graphic = vbox(10.0) {
            padding = insets(10.0)
            prefWidth = 600.0
            prefHeight = 800.0
            alignment = Pos.TOP_CENTER

            borderpane {
                center {
                    yourImageView = imageview {
                        addClass(Styles.itemGridCell)
                    }
                }
                right {
                    yourItemDetails = label {
                        borderpaneConstraints { alignment = Pos.CENTER_LEFT }
                        isWrapText = true
                    }
                }
            }

            hbox(5.0) {
                prefWidth = 0.0
                alignment = Pos.CENTER
                label("Matches")
                separator {
                    hgrow = Priority.ALWAYS
                }
                refreshButton = button {
                    graphic = imageview(SimilarOnlineDialog::class.java.getResource("/imgs/refresh.png").toExternalForm(), true)
                    onAction = EventHandler { event ->
                        event.consume()
                        val viewing = viewing

                        if (viewing != null && viewing.isFinished) {
                            viewing.reset()
                            duperQueue.put(viewing)
                        }
                    }
                }
            }

            stackpane {
                vgrow = Priority.ALWAYS
                prefWidth = 0.0

                matchGrid = gridView {
                    cellWidth = ItemCellFactory.SIZE
                    cellHeight = ItemCellFactory.SIZE
                    horizontalCellSpacing = 5.0
                    verticalCellSpacing = 5.0
                    cellFactory = factory
                }
                loadingIndicator = progressindicator {
                    stackpaneConstraints { alignment = Pos.CENTER }
                    maxWidth = 50.0
                    maxHeight = 50.0
                    progress = -1.0
                }
            }

            hbox(5.0) {
                padding = insets(5.0)
                alignment = Pos.CENTER
                prefWidth = 0.0

                button("<") {
                    onAction = EventHandler { event ->
                        event.consume()
                        previous()
                    }
                }
                indexLabel = label("N/A")
                button(">") {
                    onAction = EventHandler { event ->
                        event.consume()
                        next()
                    }
                }
            }
        }


        initViewingListener()

        if (matches.isNotEmpty()) {
            viewing = matches.first()
        }

        addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (!event.isShortcutDown && !event.isAltDown && !event.isShiftDown) {
                if (event.code == KeyCode.LEFT) {
                    event.consume()
                    previous()
                } else if (event.code == KeyCode.RIGHT) {
                    event.consume()
                    next()
                }
            }
        }

        Platform.runLater { initDuperThread() }
    }

    private fun initViewingListener() {
        val finishedListener = { _: Observable ->
            runOnUIThread {
                if (viewing?.isFinished == true) {
                    loadingIndicator.hide()
                } else {
                    loadingIndicator.show()
                }

                refreshButton.isDisable = viewing == null || viewing?.isFinished == false

                matchGrid.items.apply {
                    clear()
                    if (viewing != null) addAll(viewing!!.matches)
                }
            }
        }

        viewingProperty.addListener { _, oldValue, newValue ->
            oldValue?.finishedProperty?.removeListener(finishedListener)

            val item = newValue?.item

            yourImageView.image = null
            item?.getThumbnail()?.want(this) { thumb ->
                runOnUIThread { yourImageView.image = thumb.image }
            }

            yourItemDetails.text = if (item is FileItem) {
                thread(start = true, isDaemon = true) {
                    val img = image(item.file)
                    runOnUIThread {
                        if (viewing == newValue) yourItemDetails.text = "${item.file.name}\n${img.width}x${img.height}\n${bytesToPrettyString(item.file.length())}"
                    }
                }

                "${item.file.name}\nLoading...\n${bytesToPrettyString(item.file.length())}"
            } else {
                ""
            }

            indexLabel.text = if (newValue == null) {
                "N/A"
            } else {
                "${matches.indexOf(viewing) + 1}/${matches.size}"
            }

            if (newValue?.isFinished == true) {
                loadingIndicator.hide()
            } else {
                newValue?.finishedProperty?.addListener(finishedListener)
                loadingIndicator.show()
            }

            refreshButton.isDisable = newValue == null || !newValue.isFinished

            matchGrid.items.apply {
                clear()
                if (newValue != null) {
                    addAll(newValue.matches)
                }
            }
        }
    }

    private fun next() {
        viewing = matches[(matches.indexOf(viewing) + 1).coerceAtMost(matches.lastIndex)]
    }

    private fun previous() {
        viewing = matches[(matches.indexOf(viewing) - 1).coerceAtLeast(0)]
    }

    private fun initDuperThread() {
        matches.forEach { duperQueue.put(it) }

        thread(start = true, isDaemon = true, name = "IQDB Duper") {
            while (!duper.isClosed) {
                val match = duperQueue.poll(3, TimeUnit.SECONDS)
                if (duper.isClosed) break
                if (match == null) continue

                try {
                    duper.findMatches(match)
                } catch (e: Exception) {
                    log.error("Error while finding matches", e)
                }
            }
        }
    }

    override fun close() {
        super.close()

        duper.close()
    }

}