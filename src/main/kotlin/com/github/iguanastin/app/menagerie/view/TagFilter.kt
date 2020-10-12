package com.github.iguanastin.app.menagerie.view

import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Tag

class TagFilter(val tag: Tag, exclude: Boolean): ViewFilter(exclude) {

    override fun accepts(item: Item): Boolean {
        return invertIfExcluded(tag in item.tags)
    }

}