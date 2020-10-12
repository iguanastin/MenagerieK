package com.github.iguanastin.app.menagerie.view

import com.github.iguanastin.app.menagerie.model.FileItem
import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Item

class ElementOfFilter(val group: GroupItem?, exclude: Boolean): ViewFilter(exclude) {

    override fun accepts(item: Item): Boolean {
        return invertIfExcluded((group == null && item is FileItem && item.elementOf != null) || (group != null && item in group.items))
    }

    override fun toString(): String {
        val str = if (group == null) {
            "in:any"
        } else {
            "in:${group.id}"
        }

        return if (exclude) {
            "-$str"
        } else {
            str
        }
    }

}