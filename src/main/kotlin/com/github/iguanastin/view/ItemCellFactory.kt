package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.model.*
import com.github.iguanastin.view.nodes.MultiSelectGridView
import javafx.beans.value.ChangeListener
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.effect.DropShadow
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.text.TextAlignment
import javafx.util.Callback
import org.controlsfx.control.GridCell
import org.controlsfx.control.GridView
import tornadofx.*

object ItemCellFactory {

    const val SIZE: Double = Item.thumbnailSize + 10

    val groupTag: Image by lazy { Image(javaClass.getResource("/imgs/group_tag.png").toExternalForm(), true) }
    val videoTag: Image by lazy { Image(javaClass.getResource("/imgs/video_tag.png").toExternalForm(), true) }

    fun factory(afterInitCell: GridCell<Item>.() -> Unit = {}): Callback<GridView<Item>, GridCell<Item>> {
        return Callback<GridView<Item>, GridCell<Item>> { grid ->
            object : GridCell<Item>() {

                private lateinit var thumbView: ImageView
                private lateinit var tagView: ImageView
                private lateinit var textView: Label
                private lateinit var bottomRightLabel: Label

                private val groupTitleListener: ChangeListener<String> = ChangeListener { _, _, newValue ->
                    textView.text = newValue
                }

                init {
                    addClass(Styles.itemGridCell)
                    graphic = stackpane {
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
                        tagView = imageview {
                            stackpaneConstraints { alignment = Pos.BOTTOM_LEFT }
                            translateX = -4.0
                            translateY = -4.0
                        }
                        textView = label {
                            padding = insets(2.0)
                            stackpaneConstraints { alignment = Pos.TOP_CENTER }
                            textAlignment = TextAlignment.CENTER
                            isWrapText = true
                            effect = DropShadow(5.0, c("black")).apply { spread = 0.75 }
                        }
                        bottomRightLabel = label {
                            stackpaneConstraints { alignment = Pos.BOTTOM_RIGHT }
                            padding = insets(2.0)
                            effect = DropShadow(5.0, c("black")).apply { spread = 0.75 }
                        }
                    }

                    if (grid is MultiSelectGridView) {
                        grid.initSelectableCell(this)
                    }

                    afterInitCell()
                }

                override fun updateItem(item: Item?, empty: Boolean) {
                    cleanUpCurrentItem()

                    super.updateItem(item, empty)

                    tagView.hide()
                    textView.hide()
                    bottomRightLabel.hide()

                    thumbView.image = null
                    item?.getThumbnail()?.want(this) {
                        runOnUIThread { if (getItem() == it.item) thumbView.image = it.image }
                    }

                    if (item is FileItem) {
                        if (item.elementOf != null) {
                            bottomRightLabel.apply {
                                show()
                                text = item.elementOf?.items?.indexOf(item)?.toString()
                            }
                        }
                    }

                    when (item) {
                        is GroupItem -> {
                            tagView.apply {
                                show()
                                image = groupTag
                            }
                            textView.apply {
                                show()
                                text = item.title
                            }
                            bottomRightLabel.apply {
                                show()
                                text = item.items.size.toString()
                            }
                            item.titleProperty.addListener(groupTitleListener)
                        }
                        is ImageItem -> {

                        }
                        is VideoItem -> {
                            tagView.apply {
                                show()
                                image = videoTag
                            }
                        }
                        is FileItem -> {
                            textView.apply {
                                show()
                                text = item.file.name
                            }
                        }
                    }
                }

                private fun cleanUpCurrentItem() {
                    when (val item = item) {
                        is GroupItem -> {
                            item.titleProperty.removeListener(groupTitleListener)
                        }
                    }
                    item?.getThumbnail()?.unWant(this)
                }

            }
        }
    }

}