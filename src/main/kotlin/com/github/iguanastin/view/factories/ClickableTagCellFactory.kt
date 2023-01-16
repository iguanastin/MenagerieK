package com.github.iguanastin.view.factories

import com.github.iguanastin.app.menagerie.model.Tag
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.util.Callback
import tornadofx.*

object ClickableTagCellFactory {

    fun factory(onClick: (Tag) -> Unit): Callback<ListView<Tag>, ListCell<Tag>> {
        return Callback {
            TagCellFactory.factory.call(it).apply {
                graphic.onLeftClick {
                    if (item != null) onClick(item)
                }
            }
        }
    }

}