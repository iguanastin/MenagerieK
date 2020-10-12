package com.github.iguanastin.app.menagerie.view

import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.Item

class IsGroupedFilter(exclude: Boolean): ViewFilter(exclude) {

    override fun accepts(item: Item): Boolean {
        return invertIfExcluded(item is FileItem && item.elementOf != null)
    }

}