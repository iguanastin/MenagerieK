package com.github.iguanastin.app.menagerie.search.filters

import com.github.iguanastin.app.menagerie.model.Menagerie

object FilterFactory {

    val filterPrefixes: Array<String> = arrayOf(
        *ElementOfFilter.autocomplete,
        *DateFilter.autocomplete,
        *IDFilter.autocomplete,
        *TypeFilter.autocomplete
    )

    fun parseFilters(
        terms: String,
        menagerie: Menagerie,
        excludeElements: Boolean = true
    ): MutableList<SearchFilter> {
        val filters = mutableListOf<SearchFilter>()

        for (str in terms.trim().split(Regex("\\s+"))) {
            if (str.isBlank()) continue
            val exclude = str.startsWith('-')
            val word = if (exclude) str.substring(1) else str

            if (word.startsWith(ElementOfFilter.prefix, true)) {
                // Items in group filter
                filters.add(ElementOfFilter.fromSearchQuery(word, exclude, menagerie))
            } else if (word.startsWith(TypeFilter.prefix, true)) {
                // Item type filter
                filters.add(TypeFilter.fromSearchString(word, exclude))
            } else if (word.startsWith(IDFilter.prefix, true)) {
                // ID filter
                filters.add(IDFilter.fromSearchString(word, exclude))
            } else if (word.startsWith(DateFilter.prefix, true)) {
                // Date filter
                filters.add(DateFilter.fromSearchString(word, exclude))
            } else {
                // Normal tag filter
                filters.add(TagFilter.fromSearchString(word, exclude, menagerie))
            }
        }

        if (excludeElements) filters.add(ElementOfFilter(null, true)) // Exclude items that are in groups

        return filters
    }

}