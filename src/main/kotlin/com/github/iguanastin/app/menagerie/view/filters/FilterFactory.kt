package com.github.iguanastin.app.menagerie.view.filters

import com.github.iguanastin.app.menagerie.model.Menagerie

class FilterFactory private constructor() {
    companion object {
        val filterPrefixes: Array<String> = arrayOf(*ElementOfFilter.autocomplete, *DateFilter.autocomplete, *IDFilter.autocomplete, *TypeFilter.autocomplete)

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

                // TODO refactor this to be more modular. Each filter type parses its own string?
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
}