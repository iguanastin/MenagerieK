package com.github.iguanastin.app.context

import com.github.iguanastin.app.menagerie.model.Tag

class TagColorEdit(val tag: Tag, val newColor: String?): Edit() {

    val oldColor = tag.color

    override fun _perform(): Boolean {
        tag.color = newColor
        return true
    }

    override fun _undo(): Boolean {
        tag.color = oldColor
        return true
    }

    override fun toString(): String {
        return "Change tag color: $oldColor -> $newColor"
    }


}