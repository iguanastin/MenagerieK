package com.github.iguanastin.app.menagerie.search.filters

import com.github.iguanastin.app.menagerie.model.Item

class IDFilter(private val id: Int, private val type: Type, exclude: Boolean): SearchFilter(exclude) {

    enum class Type {
        LESS_THAN,
        EQUALS,
        GREATER_THAN
    }

    override fun accepts(item: Item): Boolean {
        return invertIfExcluded((item.id == id && type == Type.EQUALS) ||
                (item.id < id && type == Type.LESS_THAN) ||
                (item.id > id && type == Type.GREATER_THAN))
    }

    override fun toString(): String {
        var str = prefix
        if (type == Type.LESS_THAN) str += "<"
        else if (type == Type.GREATER_THAN) str += ">"
        str += id
        if (exclude) str = "-$str"
        return str
    }

    companion object {
        const val prefix = "id:"
        val autocomplete = arrayOf(prefix, "${prefix}<", "${prefix}>")

        fun fromSearchString(query: String, exclude: Boolean): IDFilter {
            if (!query.startsWith(prefix, true)) throw IllegalArgumentException("Expected \"$prefix\" prefix")

            var type = Type.EQUALS
            val id: Int
            if (query[prefix.length] == '<') {
                type = Type.LESS_THAN
                id = query.substring(prefix.length + 1).toInt()
            } else if (query[prefix.length] == '>') {
                type = Type.GREATER_THAN
                id = query.substring(prefix.length + 1).toInt()
            } else {
                id = query.substring(prefix.length).toInt()
            }

            return IDFilter(id, type, exclude)
        }
    }

}