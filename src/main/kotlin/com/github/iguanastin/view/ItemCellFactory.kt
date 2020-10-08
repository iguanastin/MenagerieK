package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.GroupItem
import com.github.iguanastin.app.menagerie.Item
import javafx.geometry.Pos
import javafx.scene.image.Image
import javafx.scene.image.ImageView
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
                }

                if (grid is MultiSelectGridView) {
                    grid.initSelectableCell(this)
                }
            }

            override fun updateItem(item: Item?, empty: Boolean) {
                super.updateItem(item, empty)

                thumbView.image = item?.getThumbnail()
                if (item is GroupItem) {
                    tagView.show()
                    tagView.image = groupTag
//                } else if (item) {
//                    tagView.show()
//                    tagView.image = videoTag
                } else {
                    tagView.hide()
                }
            }

        }
    }

}