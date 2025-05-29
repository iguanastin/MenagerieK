package com.github.iguanastin.app.menagerie.import

import com.github.iguanastin.app.menagerie.model.GroupItem
import com.github.iguanastin.app.menagerie.model.Menagerie

class ImportGroup(val title: String, var id: Int) {

    val idChangeListeners = mutableListOf<(group: ImportGroup, old: Int, new: Int) -> Unit>()

    fun getRealGroup(menagerie: Menagerie): GroupItem {
        if (id < 0) {
            val g = menagerie.createGroup(title)
            idChangeListeners.forEach { it(this, id, g.id) }
            id = g.id
        }
        return menagerie.getItem(id) as GroupItem
    }

}