package com.github.iguanastin.app.menagerie.search.filters

import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.search.FilterParseException

class DateFilter(private val time: Long, private val type: Type, exclude: Boolean) : SearchFilter(exclude) {

    enum class Type {
        LESS_THAN,
        EQUALS,
        GREATER_THAN
    }

    override fun accepts(item: Item): Boolean {
        return invertIfExcluded(
            (item.added < time && type == Type.LESS_THAN) ||
                    (item.added == time && type == Type.EQUALS) ||
                    (item.added > time && type == Type.GREATER_THAN)
        )
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
        val autocomplete = arrayOf(prefix, "${prefix}<", "${prefix}>")

        fun fromSearchString(query: String, exclude: Boolean): DateFilter {
            if (!query.startsWith(prefix, true)) throw IllegalArgumentException("Expected \"$prefix\" prefix")
            if (query.lowercase() in autocomplete) throw FilterParseException("Missing milliseconds since epoch parameter in query: \"$query\"")

            var type = Type.EQUALS
            val timeString: String = if (query[prefix.length] == '<') {
                type = Type.LESS_THAN
                query.substring(prefix.length + 1)
            } else if (query[prefix.length] == '>') {
                type = Type.GREATER_THAN
                query.substring(prefix.length + 1)
            } else {
                query.substring(prefix.length)
            }

            val time: Long = try {
                timeString.toLong()
            } catch (e: NumberFormatException) {
                throw FilterParseException("$timeString is not a valid milliseconds since epoch number")
            }

            if (time < 0) throw FilterParseException("Milliseconds since epoch must not be negative: $time")

            return DateFilter(time, type, exclude)
        }
    }

}