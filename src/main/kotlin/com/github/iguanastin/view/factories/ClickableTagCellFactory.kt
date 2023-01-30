package com.github.iguanastin.view.factories

import com.github.iguanastin.app.menagerie.model.Tag
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import tornadofx.*

class ClickableTagCellFactory(val onClick: (Tag) -> Unit): TagCellFactory() {

    override fun call(listView: ListView<Tag>?): ListCell<Tag> {
        return super.call(listView).apply {
            graphic.onLeftClick {
                if (item != null) onClick(item)
            }
        }
    }

}