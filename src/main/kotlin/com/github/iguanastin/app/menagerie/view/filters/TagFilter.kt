package com.github.iguanastin.app.menagerie.view.filters

import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Menagerie
import com.github.iguanastin.app.menagerie.model.Tag

class TagFilter(val tag: Tag, exclude: Boolean): ViewFilter(exclude) {

    override fun accepts(item: Item): Boolean {
        return invertIfExcluded(tag in item.tags)
    }

    override fun toString(): String {
        return if (exclude) {
            "-${tag.name}"
        } else {
            tag.name
        }
    }

    companion object {
        fun fromSearchString(query: String, exclude: Boolean, menagerie: Menagerie): TagFilter {
            return TagFilter(menagerie.getTag(query)!!, exclude)
        }
    }

}