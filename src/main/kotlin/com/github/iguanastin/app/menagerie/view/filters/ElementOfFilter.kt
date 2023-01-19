package com.github.iguanastin.app.menagerie.view.filters

import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.Menagerie

class ElementOfFilter(val group: GroupItem?, exclude: Boolean): ViewFilter(exclude) {

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

        fun fromSearchQuery(query: String, exclude: Boolean, menagerie: Menagerie): ElementOfFilter {
            if (!query.startsWith(prefix, true)) throw IllegalArgumentException("Expected \"$prefix\" prefix")

            val parameter = query.substring(prefix.length)
            if (parameter.equals("any", true)) {
                return ElementOfFilter(null, exclude)
            } else {
                val item = menagerie.getItem(parameter.toInt())
                if (item !is GroupItem) throw NullPointerException("No group with ID: $parameter")

                return ElementOfFilter(item, exclude)
            }
        }
    }

}