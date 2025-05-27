package com.github.iguanastin.view.nodes

import com.github.iguanastin.app.Styles
import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.Item
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import tornadofx.*

class PlainTextDisplay : ItemDisplay() {

    private val text = TextArea()
    private val title = Label()


    init {
        addClass(Styles.textDisplay)

        top = title
        title.addClass(Styles.heading)

        center = text

        itemProperty.addListener { _, _, item ->
            title.text = if (item is FileItem) item.file.path else null
            text.text = if (item is FileItem) item.file.readText() else null
        }
    }

    override fun canDisplay(item: Item?): Boolean {
        return item is FileItem && item.file.extension.lowercase() == "txt"
    }

}