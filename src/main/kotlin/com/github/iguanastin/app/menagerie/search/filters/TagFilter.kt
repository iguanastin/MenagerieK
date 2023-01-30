package com.github.iguanastin.app.menagerie.search.filters

import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Menagerie
import com.github.iguanastin.app.menagerie.model.Tag
import com.github.iguanastin.app.menagerie.search.FilterParseException

class TagFilter(val tag: Tag, exclude: Boolean): SearchFilter(exclude) {

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
            val tag = menagerie.getTag(query) ?: throw FilterParseException("No such tag: $query")
            return TagFilter(tag, exclude)
        }
    }

}