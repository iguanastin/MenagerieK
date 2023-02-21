package com.github.iguanastin.app.menagerie.search.filters

import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Menagerie
import com.github.iguanastin.app.menagerie.search.FilterParseException

class ElementOfFilter(val group: GroupItem?, exclude: Boolean): SearchFilter(exclude) {

    override fun accepts(item: Item): Boolean {
        return invertIfExcluded((group == null && item is FileItem && item.elementOf != null) || (group != null && item in group.items))
    }

    override fun toString(): String {
        var str = prefix + (group?.id ?: "any")

        if (exclude) str = "-$str"
        return str
    }

    companion object {
        const val prefix = "in:"
        val autocomplete = arrayOf(prefix, "${prefix}any")

        fun fromSearchQuery(query: String, exclude: Boolean, menagerie: Menagerie): ElementOfFilter {
            if (!query.startsWith(prefix, true)) throw IllegalArgumentException("Expected \"$prefix\" prefix")
            if (query.lowercase() in autocomplete) throw FilterParseException("Missing group ID parameter in query: \"$query\"")

            val parameter = query.substring(prefix.length)
            if (parameter.equals("any", true)) {
                return ElementOfFilter(null, exclude)
            } else {
                val id = try {
                    parameter.toInt()
                } catch (e: NumberFormatException) {
                    throw FilterParseException("$parameter is not a valid ID number")
                }
                val item = menagerie.getItem(id) ?: throw FilterParseException("No item with ID: $id")
                if (item !is GroupItem) throw FilterParseException("ID: $id is not a group")

                return ElementOfFilter(item, exclude)
            }
        }
    }

}