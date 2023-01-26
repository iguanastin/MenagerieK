package com.github.iguanastin.app.menagerie.search.filters

import com.github.iguanastin.app.menagerie.model.*

class TypeFilter(val type: Type, exclude: Boolean): SearchFilter(exclude) {

    enum class Type {
        Image,
        Video,
        Group,
        File
    }

    override fun accepts(item: Item): Boolean {
        val result: Boolean = when (type) {
            Type.Image -> item is ImageItem
            Type.Video -> item is VideoItem
            Type.Group -> item is GroupItem
            Type.File -> item is FileItem
        }

        return if (exclude) !result else result
    }

    override fun toString(): String {
        var str = prefix
        str += typeToString(type)
        if (exclude) str = "-$str"
        return str
    }

    companion object {
        const val prefix = "is:"
        val autocomplete = Type.values().map { "${prefix}${it.name.lowercase()}" }.toTypedArray()

        fun fromSearchString(query: String, exclude: Boolean): TypeFilter {
            if (!query.startsWith(prefix, true)) throw IllegalArgumentException("Expected \"$prefix\" prefix")
            val temp = query.substring(prefix.length)

            return TypeFilter(stringToType(temp), exclude)
        }

        private fun stringToType(str: String): Type {
            for (type in Type.values()) {
                if (type.name.equals(str, ignoreCase = true)) return type
            }

            throw IllegalArgumentException("No such type: $str")
        }

        private fun typeToString(type: Type): String {
            return type.name.lowercase()
        }
    }

}