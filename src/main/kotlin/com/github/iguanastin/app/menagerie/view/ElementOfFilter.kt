package com.github.iguanastin.app.menagerie.view

import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Item

class ElementOfFilter(val group: GroupItem, exclude: Boolean): ViewFilter(exclude) {

    override fun accepts(item: Item): Boolean {
        return invertIfExcluded(item in group.items)
    }

}