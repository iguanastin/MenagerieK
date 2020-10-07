package com.github.iguanastin.view

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.Item
import javafx.collections.ListChangeListener
import javafx.geometry.Pos
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.util.Callback
import org.controlsfx.control.GridCell
import org.controlsfx.control.GridView
import tornadofx.*

object ItemCellFactory {

    const val PADDING: Int = 5

    val factory = Callback<GridView<Item>, GridCell<Item>> { grid ->
        object : GridCell<Item>() {

            private lateinit var image: ImageView

            init {
                addClass(Styles.itemGridCell)
                graphic = borderpane {
                    center {
                        image = imageview {
                            alignment = Pos.CENTER
                            maxWidth = Item.thumbnailWidth
                            maxHeight = Item.thumbnailHeight
                            minWidth = Item.thumbnailWidth
                            minHeight = Item.thumbnailHeight
                        }
                    }
                }

                if (grid is MultiSelectGridView) {
                    grid.initSelectableCell(this)
                }
            }

            override fun updateItem(item: Item?, empty: Boolean) {
                super.updateItem(item, empty)

                image.image = item?.getThumbnail()
            }

        }
    }

}