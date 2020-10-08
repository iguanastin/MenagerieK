package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.*
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.effect.DropShadow
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.paint.Color
import javafx.scene.text.TextAlignment
import javafx.util.Callback
import org.controlsfx.control.GridCell
import org.controlsfx.control.GridView
import tornadofx.*

object ItemCellFactory {

    const val SIZE: Double = Item.thumbnailSize + 10

    val videoTag: Image by lazy { Image(javaClass.getResource("/imgs/group_tag.png").toExternalForm(), true) }
    val groupTag: Image by lazy { Image(javaClass.getResource("/imgs/video_tag.png").toExternalForm(), true) }

    val factory = Callback<GridView<Item>, GridCell<Item>> { grid ->
        object : GridCell<Item>() {

            private lateinit var thumbView: ImageView
            private lateinit var tagView: ImageView
            private lateinit var textView: Label

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
                        stackpaneConstraints { alignment = Pos.CENTER }
                        textAlignment = TextAlignment.CENTER
                        effect = DropShadow(5.0, c("black")).apply { spread = 0.5 }
                    }
                }

                if (grid is MultiSelectGridView) {
                    grid.initSelectableCell(this)
                }
            }

            override fun updateItem(item: Item?, empty: Boolean) {
                super.updateItem(item, empty)

                tagView.hide()
                textView.hide()

                thumbView.image = item?.getThumbnail()

                when (item) {
                    is GroupItem -> {
                        tagView.apply {
                            show()
                            image = groupTag
                        }
                    }
                    is ImageItem -> {
                        // Nothing special
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

        }
    }

}