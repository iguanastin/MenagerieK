package com.github.iguanastin.app.menagerie.view

import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Menagerie

class FilterFactory private constructor() {
    companion object {
        fun parseFilters(
            terms: String,
            menagerie: Menagerie,
            excludeElements: Boolean = true
        ): MutableList<ViewFilter> {
            val filters = mutableListOf<ViewFilter>()

            for (str in terms.trim().split(Regex("\\s+"))) {
                if (str.isBlank()) continue
                val exclude = str.startsWith('-')
                val word = if (exclude) str.substring(1) else str

                if (word.matches(Regex("(in:any)|(in:[0-9]+)", RegexOption.IGNORE_CASE))) {
                    // TODO refactor this to be more modular. Each filter type parses its own string?
                    val parameter = word.substring(3)
                    if (parameter.equals("any", true)) {
                        filters.add(ElementOfFilter(null, exclude))
                    } else {
                        val id = parameter.toInt()
                        val item = menagerie.getItem(id)
                        if (item is GroupItem) {
                            filters.add(ElementOfFilter(item, exclude))
                        } else {
                            throw NullPointerException("No group with ID: $id")
                        }
                    }
                } else if (word.toLowerCase() in arrayOf("is:group", "is:image", "is:video", "is:file")) {
                    filters.add(
                        IsTypeFilter(
                            when (word.toLowerCase()) {
                                "is:group" -> IsTypeFilter.Type.Group
                                "is:image" -> IsTypeFilter.Type.Image
                                "is:video" -> IsTypeFilter.Type.Video
                                "is:file" -> IsTypeFilter.Type.File
                                else -> throw IllegalArgumentException("Accepts type, but doesn't know how to handle it: \"$word\"")
                            }, exclude
                        )
                    )
                } else {
                    val tag = menagerie.getTag(word)!!
                    filters.add(TagFilter(tag, exclude))
                }
            }

            if (excludeElements) filters.add(ElementOfFilter(null, true))

            return filters
        }
    }
}