package com.github.iguanastin.app.menagerie.view.filters

import com.github.iguanastin.app.menagerie.model.Item

class DateFilter(private val time: Long, private val type: Type, exclude: Boolean): ViewFilter(exclude) {

    enum class Type {
        LESS_THAN,
        EQUALS,
        GREATER_THAN
    }

    override fun accepts(item: Item): Boolean {
        return invertIfExcluded((item.added < time && type == Type.LESS_THAN) ||
                (item.added == time && type == Type.EQUALS) ||
                (item.added > time && type == Type.GREATER_THAN))
    }

    override fun toString(): String {
        var str = prefix
        if (type == Type.LESS_THAN) str += "<"
        else if (type == Type.GREATER_THAN) str += ">"
        str += time
        if (exclude) str = "-$str"
        return str
    }

    companion object {
        const val prefix = "time:"

        fun fromSearchString(query: String, exclude: Boolean): DateFilter {
            if (!query.startsWith(prefix, true)) throw IllegalArgumentException("Expected \"$prefix\" prefix")

            var type = Type.EQUALS
            val time: Long
            if (query[prefix.length] == '<') {
                type = Type.LESS_THAN
                time = query.substring(prefix.length + 1).toLong()
            } else if (query[prefix.length] == '>') {
                type = Type.GREATER_THAN
                time = query.substring(prefix.length + 1).toLong()
            } else {
                time = query.substring(prefix.length).toLong()
            }

            return DateFilter(time, type, exclude)
        }
    }

}