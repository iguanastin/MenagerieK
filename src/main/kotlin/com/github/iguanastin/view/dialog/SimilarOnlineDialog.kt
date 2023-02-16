package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.duplicates.remote.*
import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.utils.bytesToPrettyString
import com.github.iguanastin.view.factories.ItemCellFactory
import com.github.iguanastin.view.gridView
import com.github.iguanastin.view.image
import com.github.iguanastin.view.onActionConsuming
import com.github.iguanastin.view.runOnUIThread
import javafx.application.Platform
import javafx.beans.Observable
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
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

class SimilarOnlineDialog(
    private val matches: List<OnlineMatchSet>,
    private val matcher: OnlineMatchFinder = SauceNAOMatchFinder()
) : StackDialog() {

    companion object {
        private val tagIcon = Image(
            SimilarOnlineDialog::class.java.getResource("/imgs/tag.png")?.toExternalForm(),
            true
        )
        private val loadingIcon = Image(
            SimilarOnlineDialog::class.java.getResource("/imgs/loading.png")?.toExternalForm(),
            true
        )
        private val finishedIcon = Image(
            SimilarOnlineDialog::class.java.getResource("/imgs/check.png")?.toExternalForm(),
            true
        )
    }

    private val factory = Callback<GridView<OnlineMatch>, GridCell<OnlineMatch>> {
        object : GridCell<OnlineMatch>() {

            private lateinit var thumbView: ImageView
            private lateinit var bottomLabel: Label
            private lateinit var topLabel: Label
            private lateinit var tagButton: Button

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
                    stackpane {
                        padding = insets(5.0)
                        topLabel = label {
                            stackpaneConstraints { alignment = Pos.TOP_LEFT }
                            effect = DropShadow(5.0, c("black")).apply { spread = 0.5 }
                        }
                        bottomLabel = label {
                            stackpaneConstraints { alignment = Pos.BOTTOM_RIGHT }
                            effect = DropShadow(5.0, c("black")).apply { spread = 0.5 }
                        }
                        tagButton = button {
                            graphic = ImageView(tagIcon)
                            stackpaneConstraints { alignment = Pos.TOP_RIGHT }
                            onActionConsuming { autotagCurrentItem() }
                            tooltip("Automatically tag item")
                        }
                    }
                }

                addEventHandler(MouseEvent.MOUSE_PRESSED) { event ->
                    if (event.button == MouseButton.PRIMARY) {
                        if (item != null) Desktop.getDesktop().browse(URI(item!!.sourceUrl))
                    }
                    event.consume()
                }
            }

            private fun autotagCurrentItem() {
                val match = item ?: return
                val set = viewing ?: return
                if (!SourceTagParser.canGetTagsFrom(match.sourceUrl)) return

                (graphic as ImageView).image = loadingIcon
                isDisable = true

                thread(start = true, isDaemon = true, name = "Online auto tagger") {
                    val tags = SourceTagParser.getTags(match.sourceUrl, matcher.client!!)

                    tags.forEach {
                        val tag = set.item.menagerie.getOrMakeTag(it, true)
                        set.item.addTag(tag)
                    }

                    if (scene != null && item == match) {
                        runOnUIThread {
                            (graphic as ImageView).image =
                                if (tags.isNotEmpty()) finishedIcon else tagIcon
                            isDisable = tags.isNotEmpty()

            //                                            this@SimilarOnlineDialog.parent.add(
            //                                                InfoStackDialog(
            //                                                    "Found ${tags.size} tags",
            //                                                    "Applied tags to item\n\nNew tags are applied as temporary tags"
            //                                                )
            //                                            )
                        }
                    }
                }
            }

            override fun updateItem(item: OnlineMatch?, empty: Boolean) {
                super.updateItem(item, empty)

                // --- The following applies to versions of ControlsFX newer than 9.0.0 ---
                // For some reason, any node changes in the GridCell cause the entire grid to reset all cells
                // Background loading images in GridCells causes a loop of updateItem calls which keep resetting the images
                // https://github.com/controlsfx/controlsfx/issues/1241 still present as of 11.1.2

                thumbView.image =
                    if (item == null) null else Image(
                        item.thumbUrl,
                        Item.thumbnailSize,
                        Item.thumbnailSize,
                        true,
                        true,
                        true
                    )
                bottomLabel.text = item?.bottomText
                topLabel.text = item?.topText
                (tagButton.graphic as ImageView).image = tagIcon
                tagButton.isVisible = item != null && SourceTagParser.canGetTagsFrom(item.sourceUrl)
                tagButton.isDisable = false
            }

        }
    }


    private val matchQueue: BlockingQueue<OnlineMatchSet> = LinkedBlockingQueue()

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
    private lateinit var errorText: Label


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
                    graphic = imageview(
                        SimilarOnlineDialog::class.java.getResource("/imgs/refresh.png")?.toExternalForm(),
                        true
                    )
                    onActionConsuming {
                        val viewing = viewing

                        if (viewing != null) {
                            viewing.reset()
                            matchQueue.put(viewing)
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
                errorText = label {
                    isWrapText = true
                }
            }

            hbox(5.0) {
                padding = insets(5.0)
                alignment = Pos.CENTER
                prefWidth = 0.0

                button("<") {
                    isDisable = true
                    viewingProperty.addListener { _, _, new -> isDisable = matches.indexOf(new) <= 0 }
                    onActionConsuming { previous() }
                }
                indexLabel = label("N/A")
                button(">") {
                    isDisable = true
                    viewingProperty.addListener { _, _, new -> isDisable = matches.indexOf(new) >= matches.size - 1 }
                    onActionConsuming { next() }
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

        Platform.runLater { initMatcherThread() }
    }

    private fun initViewingListener() {
        val stateListener = { _: Observable? ->
            runOnUIThread {
                matchGrid.items.clear()

                if (viewing?.state == OnlineMatchSet.State.FAILED) {
                    errorText.show()
                    errorText.text = " !!! --- FAILED --- !!!\n\n${viewing?.error?.localizedMessage}"
                } else {
                    errorText.hide()
                }
                if (viewing?.state == OnlineMatchSet.State.FINISHED) matchGrid.items.addAll(viewing!!.matches)
                if (viewing?.state in arrayOf(
                        OnlineMatchSet.State.LOADING,
                        OnlineMatchSet.State.WAITING
                    )
                ) loadingIndicator.show() else loadingIndicator.hide()
                refreshButton.isDisable = viewing == null || viewing?.state in arrayOf(
                    OnlineMatchSet.State.LOADING,
                    OnlineMatchSet.State.WAITING
                )
            }
        }

        viewingProperty.addListener { _, oldValue, newValue ->
            oldValue?.stateProperty?.removeListener(stateListener)
            newValue?.stateProperty?.addListener(stateListener)

            val item = newValue?.item

            yourImageView.image = null
            item?.getThumbnail()?.want(this) { thumb ->
                runOnUIThread { yourImageView.image = thumb.image }
            }

            yourItemDetails.text = if (item is FileItem) {
                thread(start = true, isDaemon = true) {
                    val img = image(item.file)
                    runOnUIThread {
                        if (viewing == newValue) yourItemDetails.text =
                            "${item.file.name}\n${img.width.toInt()}x${img.height.toInt()}\n${bytesToPrettyString(item.file.length())}"
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

            stateListener(null)
        }
    }

    private fun next() {
        viewing = matches[(matches.indexOf(viewing) + 1).coerceAtMost(matches.lastIndex)]
    }

    private fun previous() {
        viewing = matches[(matches.indexOf(viewing) - 1).coerceAtLeast(0)]
    }

    private fun initMatcherThread() {
        matches.forEach { matchQueue.put(it) }

        thread(start = true, isDaemon = true, name = "Online Match Finder") {
            while (!matcher.isClosed) {
                val match = matchQueue.poll(3, TimeUnit.SECONDS)
                if (matcher.isClosed) break
                if (match == null) continue

                try {
                    matcher.findMatches(match)
                } catch (t: Throwable) {
                    log.error("Error while finding matches", t)
                    match.error = t
                    match.state = OnlineMatchSet.State.FAILED
                }
            }
        }
    }

    override fun close() {
        super.close()

        matcher.close()
    }

}